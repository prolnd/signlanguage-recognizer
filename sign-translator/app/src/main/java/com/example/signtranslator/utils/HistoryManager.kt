package com.example.signtranslator.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.signtranslator.models.SignHistoryEntry
import com.example.signtranslator.models.TranslationHistoryEntry
import com.example.signtranslator.models.SignFrame
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HistoryManager(private val context: Context) {

    private val historyList = mutableListOf<TranslationHistoryEntry>()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val TAG = "HistoryManager"
        private const val HISTORY_KEY = "translation_history"
        private const val MAX_HISTORY_ENTRIES = 100
        private const val IMAGES_DIR = "history_images"
    }

    init {
        loadHistoryFromStorage()
    }

    fun addTranslation(sentence: String, signEntries: List<SignHistoryEntry>) {
        if (sentence.isBlank() || signEntries.isEmpty()) return

        Log.d(TAG, "=== ADDING TRANSLATION ===")
        Log.d(TAG, "Sentence: '$sentence'")
        Log.d(TAG, "Sign entries: ${signEntries.size}")

        signEntries.forEachIndexed { index, entry ->
            val bitmap = entry.bestFrame.bitmap
            Log.d(TAG, "  [$index] Sign: '${entry.sign}', Bitmap: ${bitmap?.let { "${it.width}x${it.height}, recycled: ${it.isRecycled}" } ?: "null"}")
        }

        val entry = TranslationHistoryEntry(
            sentence = sentence,
            signEntries = signEntries
        )

        historyList.add(0, entry) // Add to beginning

        // Keep only last entries
        if (historyList.size > MAX_HISTORY_ENTRIES) {
            val removed = historyList.removeAt(historyList.size - 1)
            cleanupEntryImages(removed)
        }

        saveHistoryToStorage()
        Log.d(TAG, "Translation added successfully. Total entries: ${historyList.size}")
    }

    fun getHistory(): List<TranslationHistoryEntry> {
        Log.d(TAG, "Getting history: ${historyList.size} entries")
        historyList.forEachIndexed { index, entry ->
            Log.d(TAG, "  [$index] '${entry.sentence}' - ${entry.signEntries.size} signs")
            entry.signEntries.forEach { signEntry ->
                val bitmap = signEntry.bestFrame.bitmap
                Log.d(TAG, "    Sign: '${signEntry.sign}', Bitmap: ${bitmap?.let { "${it.width}x${it.height}" } ?: "null"}")
            }
        }
        return historyList.toList()
    }

    fun clearHistory() {
        historyList.forEach { entry ->
            cleanupEntryImages(entry)
        }
        historyList.clear()
        clearStoredHistory()
    }

    fun getEntry(id: String): TranslationHistoryEntry? {
        val entry = historyList.find { it.id == id }
        Log.d(TAG, "Getting entry by ID: $id, found: ${entry != null}")
        return entry
    }

    fun deleteEntry(id: String): Boolean {
        val entry = historyList.find { it.id == id }
        return if (entry != null) {
            historyList.remove(entry)
            cleanupEntryImages(entry)
            saveHistoryToStorage()
            true
        } else {
            false
        }
    }

    private fun saveHistoryToStorage() {
        try {
            Log.d(TAG, "=== SAVING HISTORY TO STORAGE ===")
            Log.d(TAG, "Saving ${historyList.size} history entries")

            // Save metadata (without bitmaps) to SharedPreferences
            val historyMetadata = historyList.map { entry ->
                Log.d(TAG, "Processing entry: '${entry.sentence}' with ${entry.signEntries.size} signs")

                HistoryMetadata(
                    id = entry.id,
                    sentence = entry.sentence,
                    timestamp = entry.timestamp,
                    signMetadata = entry.signEntries.mapIndexed { index, signEntry ->
                        Log.d(TAG, "  Saving sign [$index]: '${signEntry.sign}'")
                        val imageId = saveFrameImage(signEntry.bestFrame, entry.id)
                        Log.d(TAG, "  Image ID: '$imageId'")

                        SignMetadata(
                            sign = signEntry.sign,
                            timestamp = signEntry.timestamp,
                            sentence = signEntry.sentence,
                            bestFrameId = imageId,
                            bestFrameConfidence = signEntry.bestFrame.confidence,
                            frameCount = signEntry.allFrames.size
                        )
                    }
                )
            }

            val json = gson.toJson(historyMetadata)
            sharedPrefs.edit().putString(HISTORY_KEY, json).apply()
            Log.d(TAG, "History metadata saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving history", e)
        }
    }

    private fun loadHistoryFromStorage() {
        try {
            Log.d(TAG, "=== LOADING HISTORY FROM STORAGE ===")
            val json = sharedPrefs.getString(HISTORY_KEY, null)
            if (json == null) {
                Log.d(TAG, "No history data found in storage")
                return
            }

            val type = object : TypeToken<List<HistoryMetadata>>() {}.type
            val historyMetadata: List<HistoryMetadata> = gson.fromJson(json, type)

            Log.d(TAG, "Loading ${historyMetadata.size} history entries from storage")

            historyList.clear()
            historyList.addAll(historyMetadata.mapNotNull { metadata ->
                Log.d(TAG, "Loading entry: '${metadata.sentence}' with ${metadata.signMetadata.size} signs")

                try {
                    val signEntries = metadata.signMetadata.mapNotNull { signMeta ->
                        Log.d(TAG, "  Loading sign: '${signMeta.sign}', Image ID: '${signMeta.bestFrameId}'")

                        val bitmap = loadFrameImage(signMeta.bestFrameId)
                        if (bitmap != null) {
                            Log.d(TAG, "  ✅ Loaded bitmap: ${bitmap.width}x${bitmap.height}")

                            val bestFrame = SignFrame(
                                sign = signMeta.sign,
                                confidence = signMeta.bestFrameConfidence,
                                bitmap = bitmap,
                                timestamp = signMeta.timestamp
                            )
                            SignHistoryEntry(
                                sign = signMeta.sign,
                                bestFrame = bestFrame,
                                allFrames = listOf(bestFrame), // Only keep best frame for storage
                                sentence = signMeta.sentence,
                                timestamp = signMeta.timestamp
                            )
                        } else {
                            Log.w(TAG, "  ❌ Could not load bitmap for sign: ${signMeta.sign}")
                            null
                        }
                    }

                    if (signEntries.isNotEmpty()) {
                        Log.d(TAG, "✅ Successfully loaded entry with ${signEntries.size} signs")
                        TranslationHistoryEntry(
                            id = metadata.id,
                            sentence = metadata.sentence,
                            signEntries = signEntries,
                            timestamp = metadata.timestamp
                        )
                    } else {
                        Log.w(TAG, "❌ No valid sign entries for translation: ${metadata.sentence}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading entry: ${metadata.id}", e)
                    null
                }
            })

            Log.d(TAG, "Successfully loaded ${historyList.size} history entries")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
        }
    }

    private fun saveFrameImage(frame: SignFrame, entryId: String): String {
        val imageId = "${entryId}_${frame.sign}_${frame.timestamp}"
        val fileName = "$imageId.jpg"

        Log.d(TAG, "Saving frame image: $imageId")

        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            if (!imagesDir.exists()) {
                val created = imagesDir.mkdirs()
                Log.d(TAG, "Created images directory: $created")
            }

            val imageFile = File(imagesDir, fileName)

            // Check if bitmap is valid
            if (frame.bitmap.isRecycled) {
                Log.w(TAG, "Cannot save recycled bitmap for: $imageId")
                return ""
            }

            Log.d(TAG, "Saving bitmap: ${frame.bitmap.width}x${frame.bitmap.height} to ${imageFile.absolutePath}")

            val outputStream = FileOutputStream(imageFile)
            val saved = frame.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.close()

            if (saved) {
                Log.d(TAG, "✅ Successfully saved image: $imageId (${imageFile.length()} bytes)")
                return imageId
            } else {
                Log.e(TAG, "❌ Failed to compress bitmap for: $imageId")
                return ""
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Error saving image: $imageId", e)
            return ""
        }
    }

    private fun loadFrameImage(imageId: String): Bitmap? {
        if (imageId.isEmpty()) {
            Log.w(TAG, "Empty image ID provided")
            return null
        }

        Log.d(TAG, "Loading frame image: $imageId")

        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            val imageFile = File(imagesDir, "$imageId.jpg")

            Log.d(TAG, "Looking for image file: ${imageFile.absolutePath}")
            Log.d(TAG, "File exists: ${imageFile.exists()}, Size: ${if (imageFile.exists()) imageFile.length() else "N/A"}")

            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap != null) {
                    Log.d(TAG, "✅ Successfully loaded image: $imageId (${bitmap.width}x${bitmap.height})")
                    return bitmap
                } else {
                    Log.w(TAG, "❌ Failed to decode image file: $imageId")
                }
            } else {
                Log.w(TAG, "❌ Image file does not exist: $imageId")
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading image: $imageId", e)
            return null
        }
    }

    private fun cleanupEntryImages(entry: TranslationHistoryEntry) {
        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            entry.signEntries.forEach { signEntry ->
                // Don't recycle bitmaps here - they might still be in use
                // The bitmaps will be garbage collected naturally

                // Delete saved image file
                val imageId = "${entry.id}_${signEntry.sign}_${signEntry.timestamp}"
                val imageFile = File(imagesDir, "$imageId.jpg")
                if (imageFile.exists()) {
                    val deleted = imageFile.delete()
                    Log.d(TAG, "Cleaned up image file: $imageId (deleted: $deleted)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up images", e)
        }
    }

    private fun clearStoredHistory() {
        sharedPrefs.edit().remove(HISTORY_KEY).apply()

        // Clear all image files
        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            if (imagesDir.exists()) {
                val files = imagesDir.listFiles()
                files?.forEach {
                    val deleted = it.delete()
                    Log.d(TAG, "Deleted image file: ${it.name} (success: $deleted)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing image files", e)
        }
    }

    // Data classes for serialization
    private data class HistoryMetadata(
        val id: String,
        val sentence: String,
        val timestamp: Long,
        val signMetadata: List<SignMetadata>
    )

    private data class SignMetadata(
        val sign: String,
        val timestamp: Long,
        val sentence: String,
        val bestFrameId: String,
        val bestFrameConfidence: Float,
        val frameCount: Int
    )
}