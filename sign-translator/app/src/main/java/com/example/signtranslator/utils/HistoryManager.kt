package com.example.signtranslator.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.signtranslator.models.SignFrame
import com.example.signtranslator.models.SignHistoryEntry
import com.example.signtranslator.models.TranslationHistoryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

/**
 * Manages local storage of translation history using SharedPreferences for metadata
 * and internal storage for compressed images. Handles saving, loading, and cleanup
 * of translation entries with automatic size management.
 */
class HistoryManager(private val context: Context) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_FILE = "translation_history_prefs"
        private const val PREF_HISTORY = "translation_history"
        private const val MAX_HISTORY_ENTRIES = 100
    }

    /**
     * Add a new translation to history with image compression and storage
     */
    fun addTranslation(sentence: String, signEntries: List<SignHistoryEntry>): Boolean {
        return try {
            if (sentence.isBlank() || signEntries.isEmpty()) {
                return false
            }

            val entryId = generateEntryId()
            val timestamp = System.currentTimeMillis()

            // Save images and build sign entries with metadata
            val savedSignEntries = mutableListOf<SignHistoryEntry>()

            signEntries.forEachIndexed { index, signEntry ->
                try {
                    if (signEntry.signFrame.bitmap == null) {
                        return@forEachIndexed
                    }

                    // Use consistent filename format
                    val filename = "${entryId}_${signEntry.sign}_${index}.jpg"
                    val file = File(context.filesDir, filename)

                    // Compress and save image
                    val outputStream = FileOutputStream(file)
                    val compressed = signEntry.signFrame.bitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    outputStream.close()

                    if (compressed) {
                        // Create sign entry with metadata for loading
                        val savedSignEntry = SignHistoryEntry(
                            sign = signEntry.sign,
                            signFrame = SignFrame(
                                sign = signEntry.sign,
                                confidence = signEntry.signFrame.confidence,
                                bitmap = signEntry.signFrame.bitmap,
                                timestamp = signEntry.signFrame.timestamp
                            ),
                            sentence = signEntry.sentence,
                            timestamp = signEntry.timestamp,
                            metadata = mapOf(
                                "filename" to filename,
                                "index" to index
                            )
                        )

                        savedSignEntries.add(savedSignEntry)
                    }

                } catch (e: Exception) {
                    // Continue with other signs even if one fails
                }
            }

            if (savedSignEntries.isEmpty()) {
                return false
            }

            // Create translation entry
            val translationEntry = TranslationHistoryEntry(
                id = entryId,
                sentence = sentence,
                signEntries = savedSignEntries,
                timestamp = timestamp
            )

            // Load and update history
            val currentHistory = getHistory().toMutableList()
            currentHistory.add(0, translationEntry)

            // Apply size limit and cleanup old entries
            while (currentHistory.size > MAX_HISTORY_ENTRIES) {
                val removedEntry = currentHistory.removeAt(currentHistory.size - 1)
                cleanupEntryFiles(removedEntry)
            }

            // Save updated history
            return saveHistoryToPrefs(currentHistory)

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieve all translation history entries
     */
    fun getHistory(): List<TranslationHistoryEntry> {
        return try {
            val jsonString = sharedPrefs.getString(PREF_HISTORY, null) ?: return emptyList()

            // Parse JSON data
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val historyData: List<Map<String, Any>> = gson.fromJson(jsonString, type)

            // Convert to TranslationHistoryEntry objects
            val translations = historyData.mapIndexedNotNull { index, entryMap ->
                try {
                    val id = entryMap["id"] as? String ?: return@mapIndexedNotNull null
                    val sentence = entryMap["sentence"] as? String ?: return@mapIndexedNotNull null
                    val timestamp = when (val ts = entryMap["timestamp"]) {
                        is Number -> ts.toLong()
                        is String -> ts.toLongOrNull() ?: System.currentTimeMillis()
                        else -> System.currentTimeMillis()
                    }

                    @Suppress("UNCHECKED_CAST")
                    val signEntriesData = entryMap["signEntries"] as? List<Map<String, Any>> ?: emptyList()

                    val signEntries = signEntriesData.mapIndexedNotNull { signIndex, signMap ->
                        loadSignEntryFromData(signMap, id, signIndex, sentence, timestamp)
                    }

                    if (signEntries.isNotEmpty()) {
                        TranslationHistoryEntry(
                            id = id,
                            sentence = sentence,
                            signEntries = signEntries,
                            timestamp = timestamp
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            translations

        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get a specific history entry by ID
     */
    fun getEntry(id: String): TranslationHistoryEntry? {
        return try {
            getHistory().find { it.id == id }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete a specific history entry and its associated files
     */
    fun deleteEntry(id: String): Boolean {
        return try {
            val currentHistory = getHistory().toMutableList()
            val entryToRemove = currentHistory.find { it.id == id } ?: return false

            // Remove from list
            currentHistory.removeAll { it.id == id }

            // Delete associated image files
            cleanupEntryFiles(entryToRemove)

            // Save updated history
            saveHistoryToPrefs(currentHistory)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all history entries and associated files
     */
    fun clearHistory() {
        try {
            // Delete all image files
            val files = context.filesDir.listFiles()
            files?.filter { it.name.endsWith(".jpg") }?.forEach { file ->
                file.delete()
            }

            // Clear SharedPreferences
            sharedPrefs.edit().remove(PREF_HISTORY).apply()
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    /**
     * Load sign entry from stored data with multiple filename patterns
     */
    private fun loadSignEntryFromData(
        signMap: Map<String, Any>,
        entryId: String,
        signIndex: Int,
        sentence: String,
        entryTimestamp: Long
    ): SignHistoryEntry? {
        try {
            val sign = signMap["sign"] as? String ?: return null
            val confidence = when (val conf = signMap["confidence"]) {
                is Number -> conf.toFloat()
                is String -> conf.toFloatOrNull() ?: 0f
                else -> 0f
            }
            val signTimestamp = when (val ts = signMap["timestamp"]) {
                is Number -> ts.toLong()
                is String -> ts.toLongOrNull() ?: entryTimestamp
                else -> entryTimestamp
            }
            val signSentence = signMap["sentence"] as? String ?: sentence

            // Try multiple filename patterns to find the image
            val possibleFilenames = listOf(
                "${entryId}_${sign}_${signIndex}.jpg",  // Current format
                "${entryId}_${sign}_${signTimestamp}.jpg",  // Legacy format
                "${entryId}_${sign}.jpg"  // Fallback format
            )

            val bitmap = possibleFilenames.firstNotNullOfOrNull { filename ->
                loadBitmapFromFile(filename)
            }

            return if (bitmap != null) {
                val signFrame = SignFrame(
                    sign = sign,
                    confidence = confidence,
                    bitmap = bitmap,
                    timestamp = signTimestamp
                )

                SignHistoryEntry(
                    sign = sign,
                    signFrame = signFrame,
                    sentence = signSentence,
                    timestamp = signTimestamp
                )
            } else {
                null
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Clean up image files associated with a history entry
     */
    private fun cleanupEntryFiles(entry: TranslationHistoryEntry) {
        entry.signEntries.forEachIndexed { index, signEntry ->
            val possibleFilenames = listOf(
                "${entry.id}_${signEntry.sign}_${index}.jpg",
                "${entry.id}_${signEntry.sign}_${signEntry.timestamp}.jpg",
                "${entry.id}_${signEntry.sign}.jpg"
            )

            possibleFilenames.forEach { filename ->
                val file = File(context.filesDir, filename)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Save history data to SharedPreferences
     */
    private fun saveHistoryToPrefs(history: List<TranslationHistoryEntry>): Boolean {
        return try {
            val historyData = history.map { entry ->
                mapOf(
                    "id" to entry.id,
                    "sentence" to entry.sentence,
                    "timestamp" to entry.timestamp,
                    "signEntries" to entry.signEntries.map { signEntry ->
                        mapOf(
                            "sign" to signEntry.sign,
                            "confidence" to signEntry.signFrame.confidence,
                            "timestamp" to signEntry.timestamp,
                            "sentence" to signEntry.sentence
                        )
                    }
                )
            }

            val jsonString = gson.toJson(historyData)
            val editor = sharedPrefs.edit()
            editor.putString(PREF_HISTORY, jsonString)
            editor.commit() // Use commit for synchronous save
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load bitmap from file
     */
    private fun loadBitmapFromFile(filename: String): Bitmap? {
        return try {
            val file = File(context.filesDir, filename)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate unique entry ID
     */
    private fun generateEntryId(): String {
        return "entry_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}