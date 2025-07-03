package com.example.signtranslator.utils

import android.util.Log
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
        private const val TAG = "FirebaseTrainingManager"
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
            Log.e(TAG, "Failed to sync session ${session.id} to cloud", e)
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

                            com.example.signtranslator.models.SignLetter(
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
            Log.e(TAG, "Failed to load training sessions from cloud for user $userId", e)
            emptyList()
        }
    }

    /**
     * Delete a training session from the cloud
     */
    suspend fun deleteSessionFromCloud( sessionId: String): Boolean {
        return try {
            firestore.collection(COLLECTION_TRAINING)
                .document(sessionId)
                .delete()
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session $sessionId from cloud", e)
            false
        }
    }

}