package com.example.signtranslator.utils

import com.example.signtranslator.models.TrainingSession
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Manages cloud synchronization of training sessions using Firebase Firestore.
 * Handles uploading, downloading, and syncing practice session data.
 */
class FirebaseTrainingManager {

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_TRAINING = "training_sessions"
    }

    /**
     * Upload a training session to the cloud
     */
    suspend fun syncSessionToCloud(userId: String, session: TrainingSession): Boolean {
        return try {
            val sessionData = mapOf(
                "id" to session.id,
                "sentence" to session.sentence,
                "timestamp" to session.timestamp,
                "letters" to session.letters.map { letter ->
                    mapOf(
                        "letter" to letter.letter.toString(),
                        "imageResourceId" to letter.imageResourceId
                    )
                },
                "userId" to userId,
                "synced" to true
            )

            firestore.collection(COLLECTION_TRAINING)
                .document(session.id)
                .set(sessionData)
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Download all training sessions for a user from the cloud
     */
    suspend fun loadAllSessionsFromCloud(userId: String): List<TrainingSession> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_TRAINING)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val sessions = querySnapshot.documents.mapNotNull { document ->
                try {
                    val data = document.data ?: return@mapNotNull null

                    val lettersData = data["letters"] as? List<Map<String, Any>> ?: emptyList()
                    val letters = lettersData.mapNotNull { letterMap ->
                        try {
                            val letterChar = (letterMap["letter"] as? String)?.firstOrNull() ?: return@mapNotNull null
                            val imageResourceId = (letterMap["imageResourceId"] as? Long)?.toInt() ?: return@mapNotNull null

                            com.example.signtranslator.models.TrainingLetter(
                                letter = letterChar,
                                imageResourceId = imageResourceId
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    TrainingSession(
                        id = data["id"] as? String ?: document.id,
                        sentence = data["sentence"] as? String ?: "",
                        letters = letters,
                        timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            }

            sessions
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Delete a training session from the cloud
     */
    suspend fun deleteSessionFromCloud(userId: String, sessionId: String): Boolean {
        return try {
            firestore.collection(COLLECTION_TRAINING)
                .document(sessionId)
                .delete()
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sync all local training sessions to the cloud
     */
    suspend fun syncAllLocalSessions(userId: String, localSessions: List<TrainingSession>): SyncResult {
        var successCount = 0
        var errorCount = 0

        for (session in localSessions) {
            if (syncSessionToCloud(userId, session)) {
                successCount++
            } else {
                errorCount++
            }
        }

        return SyncResult(successCount, errorCount)
    }

    /**
     * Result of a synchronization operation
     */
    data class SyncResult(
        val successCount: Int,
        val errorCount: Int
    ) {
        val isFullSuccess: Boolean get() = errorCount == 0
        val hasErrors: Boolean get() = errorCount > 0
    }
}