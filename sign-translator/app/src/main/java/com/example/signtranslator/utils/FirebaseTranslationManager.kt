package com.example.signtranslator.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.signtranslator.models.TranslationHistoryEntry
import com.example.signtranslator.models.SignHistoryEntry
import com.example.signtranslator.models.SignFrame
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Manages cloud synchronization of translation history using Firebase Firestore.
 * Handles uploading, downloading, and syncing translation data with image compression.
 */
class FirebaseTranslationManager {

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val COLLECTION_TRANSLATIONS = "translation_history"
        private const val MAX_IMAGE_SIZE = 1024 * 1024 // 1MB limit for Firestore document size
    }

    /**
     * Upload a translation entry to the cloud with compressed images
     */
    suspend fun syncTranslationToCloud(userId: String, entry: TranslationHistoryEntry): Boolean {
        return try {
            // Convert images to Base64 strings with compression
            val signEntriesWithBase64 = entry.signEntries.map { signEntry ->
                val base64Image = bitmapToBase64(signEntry.signFrame.bitmap)

                mapOf(
                    "sign" to signEntry.sign,
                    "timestamp" to signEntry.timestamp,
                    "sentence" to signEntry.sentence,
                    "bestFrameConfidence" to signEntry.signFrame.confidence,
                    "imageBase64" to base64Image
                )
            }

            val translationData = mapOf(
                "id" to entry.id,
                "sentence" to entry.sentence,
                "timestamp" to entry.timestamp,
                "signEntries" to signEntriesWithBase64,
                "userId" to userId,
                "synced" to true,
                "syncTimestamp" to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_TRANSLATIONS)
                .document(entry.id)
                .set(translationData)
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Download all translations for a user from the cloud
     */
    suspend fun loadAllTranslationsFromCloud(userId: String): List<TranslationHistoryEntry> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_TRANSLATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val translations = querySnapshot.documents.mapNotNull { document ->
                try {
                    val data = document.data ?: return@mapNotNull null

                    @Suppress("UNCHECKED_CAST")
                    val signEntriesData = data["signEntries"] as? List<Map<String, Any>> ?: return@mapNotNull null

                    val signEntries = signEntriesData.mapNotNull { signMap ->
                        try {
                            val sign = signMap["sign"] as? String ?: return@mapNotNull null
                            val timestamp = signMap["timestamp"] as? Long ?: System.currentTimeMillis()
                            val sentence = signMap["sentence"] as? String ?: ""
                            val confidence = (signMap["bestFrameConfidence"] as? Double)?.toFloat() ?: 0f
                            val imageBase64 = signMap["imageBase64"] as? String ?: ""

                            // Convert Base64 back to bitmap
                            val bitmap = base64ToBitmap(imageBase64)

                            if (bitmap != null) {
                                val signFrame = SignFrame(
                                    sign = sign,
                                    confidence = confidence,
                                    bitmap = bitmap,
                                    timestamp = timestamp
                                )

                                SignHistoryEntry(
                                    sign = sign,
                                    signFrame = signFrame,
                                    sentence = sentence,
                                    timestamp = timestamp
                                )
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (signEntries.isNotEmpty()) {
                        TranslationHistoryEntry(
                            id = data["id"] as? String ?: document.id,
                            sentence = data["sentence"] as? String ?: "",
                            signEntries = signEntries,
                            timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            // Sort by timestamp
            translations.sortedByDescending { it.timestamp }

        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Delete a translation from the cloud
     */
    suspend fun deleteTranslationFromCloud(userId: String, entryId: String): Boolean {
        return try {
            // Verify the document exists and belongs to the user
            val document = firestore.collection(COLLECTION_TRANSLATIONS)
                .document(entryId)
                .get()
                .await()

            if (!document.exists()) {
                return true // Consider successful if already gone
            }

            val documentUserId = document.getString("userId")
            if (documentUserId != userId) {
                return false // Don't delete if user doesn't own it
            }

            // Delete the document
            firestore.collection(COLLECTION_TRANSLATIONS)
                .document(entryId)
                .delete()
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sync all local translations to the cloud (only uploads new ones)
     */
    suspend fun syncAllLocalTranslations(userId: String, localTranslations: List<TranslationHistoryEntry>): SyncResult {
        var successCount = 0
        var errorCount = 0

        try {
            // Get existing cloud translation IDs to avoid unnecessary uploads
            val existingCloudIds = getExistingTranslationIds(userId)

            for (translation in localTranslations) {
                // Check if translation already exists in cloud
                if (existingCloudIds.contains(translation.id)) {
                    successCount++ // Count as success since it's already there
                } else {
                    if (syncTranslationToCloud(userId, translation)) {
                        successCount++
                    } else {
                        errorCount++
                    }
                }
            }

            return SyncResult(successCount, errorCount)

        } catch (e: Exception) {
            return SyncResult(successCount, errorCount + (localTranslations.size - successCount))
        }
    }

    /**
     * Get existing translation IDs from cloud to avoid duplicate uploads
     */
    private suspend fun getExistingTranslationIds(userId: String): Set<String> {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_TRANSLATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { document ->
                document.getString("id") ?: document.id
            }.toSet()

        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Clear all translations for a user from the cloud
     */
    suspend fun clearAllUserTranslations(userId: String): Boolean {
        return try {
            val querySnapshot = firestore.collection(COLLECTION_TRANSLATIONS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            for (document in querySnapshot.documents) {
                try {
                    document.reference.delete().await()
                } catch (e: Exception) {
                    // Continue deleting other documents even if one fails
                }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Convert bitmap to Base64 string with compression
     */
    private fun bitmapToBase64(bitmap: Bitmap?): String {
        if (bitmap == null) return ""

        return try {
            // Resize bitmap to reduce size for Firestore
            val resizedBitmap = resizeBitmapIfNeeded(bitmap, 300, 300)

            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            // Check size limit and compress further if needed
            if (byteArray.size > MAX_IMAGE_SIZE) {
                byteArrayOutputStream.reset()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                val smallerByteArray = byteArrayOutputStream.toByteArray()

                if (smallerByteArray.size > MAX_IMAGE_SIZE) {
                    return "" // Skip if still too large
                }

                Base64.encodeToString(smallerByteArray, Base64.DEFAULT)
            } else {
                Base64.encodeToString(byteArray, Base64.DEFAULT)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Convert Base64 string back to bitmap
     */
    private fun base64ToBitmap(base64String: String): Bitmap? {
        if (base64String.isEmpty()) return null

        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resize bitmap if it exceeds maximum dimensions
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val scaleWidth = maxWidth.toFloat() / width
        val scaleHeight = maxHeight.toFloat() / height
        val scale = minOf(scaleWidth, scaleHeight)

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Result of a synchronization operation
     */
    data class SyncResult(
        val successCount: Int,
        val errorCount: Int
    )
}