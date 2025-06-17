package com.example.signtranslator.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.signtranslator.models.TrainingSession
import com.example.signtranslator.models.TrainingLetter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TrainingHistoryManager(private val context: Context) {

    private val trainingList = mutableListOf<TrainingSession>()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("training_history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val TAG = "TrainingHistoryManager"
        private const val TRAINING_HISTORY_KEY = "training_history"
        private const val MAX_TRAINING_ENTRIES = 50
    }

    init {
        loadTrainingFromStorage()
    }

    fun addTrainingSession(sentence: String, letters: List<TrainingLetter>, duration: Long = 0) {
        if (sentence.isBlank() || letters.isEmpty()) return

        Log.d(TAG, "=== ADDING TRAINING SESSION ===")
        Log.d(TAG, "Sentence: '$sentence'")
        Log.d(TAG, "Letters: ${letters.size}")
        Log.d(TAG, "Duration: ${duration}ms")

        val session = TrainingSession(
            sentence = sentence,
            letters = letters,
            duration = duration,
            completionRate = calculateCompletionRate(letters)
        )

        trainingList.add(0, session) // Add to beginning

        // Keep only recent entries
        if (trainingList.size > MAX_TRAINING_ENTRIES) {
            trainingList.removeAt(trainingList.size - 1)
        }

        saveTrainingToStorage()
        Log.d(TAG, "Training session added successfully. Total sessions: ${trainingList.size}")
    }

    fun getTrainingHistory(): List<TrainingSession> {
        Log.d(TAG, "Getting training history: ${trainingList.size} sessions")
        return trainingList.toList()
    }

    fun clearTrainingHistory() {
        trainingList.clear()
        clearStoredTraining()
        Log.d(TAG, "Training history cleared")
    }

    fun getSession(id: String): TrainingSession? {
        val session = trainingList.find { it.id == id }
        Log.d(TAG, "Getting session by ID: $id, found: ${session != null}")
        return session
    }

    fun deleteSession(id: String): Boolean {
        val session = trainingList.find { it.id == id }
        return if (session != null) {
            trainingList.remove(session)
            saveTrainingToStorage()
            Log.d(TAG, "Deleted training session: $id")
            true
        } else {
            Log.w(TAG, "Training session not found: $id")
            false
        }
    }

    fun getTrainingStats(): TrainingStats {
        val totalSessions = trainingList.size
        val totalLetters = trainingList.sumOf { it.letters.size }
        val totalDuration = trainingList.sumOf { it.duration }
        val averageCompletion = if (totalSessions > 0) {
            trainingList.map { it.completionRate }.average().toFloat()
        } else 0f

        val mostPracticedLetters = trainingList
            .flatMap { it.letters }
            .groupBy { it.letter }
            .mapValues { it.value.sumOf { letter -> letter.timesViewed } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        return TrainingStats(
            totalSessions = totalSessions,
            totalLetters = totalLetters,
            totalDuration = totalDuration,
            averageCompletion = averageCompletion,
            mostPracticedLetters = mostPracticedLetters
        )
    }

    private fun calculateCompletionRate(letters: List<TrainingLetter>): Float {
        // For now, assume 100% completion if session is saved
        // Could be enhanced to track actual completion
        return 100f
    }

    private fun saveTrainingToStorage() {
        try {
            Log.d(TAG, "=== SAVING TRAINING TO STORAGE ===")
            Log.d(TAG, "Saving ${trainingList.size} training sessions")

            val json = gson.toJson(trainingList)
            sharedPrefs.edit().putString(TRAINING_HISTORY_KEY, json).apply()
            Log.d(TAG, "Training history saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving training history", e)
        }
    }

    private fun loadTrainingFromStorage() {
        try {
            Log.d(TAG, "=== LOADING TRAINING FROM STORAGE ===")
            val json = sharedPrefs.getString(TRAINING_HISTORY_KEY, null)
            if (json == null) {
                Log.d(TAG, "No training history found in storage")
                return
            }

            val type = object : TypeToken<List<TrainingSession>>() {}.type
            val sessions: List<TrainingSession> = gson.fromJson(json, type)

            Log.d(TAG, "Loading ${sessions.size} training sessions from storage")

            trainingList.clear()
            trainingList.addAll(sessions)

            Log.d(TAG, "Successfully loaded ${trainingList.size} training sessions")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading training history", e)
        }
    }

    private fun clearStoredTraining() {
        sharedPrefs.edit().remove(TRAINING_HISTORY_KEY).apply()
        Log.d(TAG, "Cleared stored training history")
    }

    // Data class for training statistics
    data class TrainingStats(
        val totalSessions: Int,
        val totalLetters: Int,
        val totalDuration: Long,
        val averageCompletion: Float,
        val mostPracticedLetters: List<Pair<Char, Int>>
    )
}