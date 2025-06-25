package com.example.signtranslator.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.signtranslator.models.TrainingSession
import com.example.signtranslator.models.TrainingLetter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages training session history with local storage and cloud synchronization.
 * Handles practice session data, statistics, and automatic cloud sync when authenticated.
 */
class TrainingHistoryManager(private val context: Context) {

    private val trainingList = mutableListOf<TrainingSession>()
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("training_history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val firebaseAuth = FirebaseAuthManager(context)
    private val firebaseTraining = FirebaseTrainingManager()
    private var hasPerformedInitialSync = false

    companion object {
        private const val TRAINING_HISTORY_KEY = "training_history"
        private const val INITIAL_SYNC_KEY = "initial_sync_done"
        private const val MAX_LOCAL_ENTRIES = 10 // Limit when not logged in
        private const val MAX_CLOUD_ENTRIES = 1000 // Expanded limit when logged in
    }

    init {
        loadTrainingFromStorage()

        // Perform initial sync if signed in and not done yet
        if (firebaseAuth.isSignedIn() && !hasPerformedInitialSync) {
            CoroutineScope(Dispatchers.IO).launch {
                performInitialSyncIfNeeded()
            }
        }
    }

    /**
     * Add a new training session to history
     */
    fun addTrainingSession(sentence: String, letters: List<TrainingLetter>) {
        if (sentence.isBlank() || letters.isEmpty()) return

        val session = TrainingSession(
            sentence = sentence,
            letters = letters
        )

        // Add to local storage first
        trainingList.add(0, session)

        // Apply storage limits based on authentication status
        val maxEntries = if (firebaseAuth.isSignedIn()) MAX_CLOUD_ENTRIES else MAX_LOCAL_ENTRIES

        while (trainingList.size > maxEntries) {
            trainingList.removeAt(trainingList.size - 1)
        }

        // Save locally (primary storage)
        saveTrainingToStorage()

        // Sync to cloud if authenticated (non-blocking)
        if (firebaseAuth.isSignedIn()) {
            firebaseAuth.getUserId()?.let { userId ->
                CoroutineScope(Dispatchers.IO).launch {
                    firebaseTraining.syncSessionToCloud(userId, session)
                }
            }
        }
    }

    /**
     * Get all training sessions
     */
    fun getTrainingHistory(): List<TrainingSession> {
        return trainingList.toList()
    }

    /**
     * Perform initial synchronization between local and cloud data
     */
    suspend fun performInitialSyncIfNeeded() {
        val userId = firebaseAuth.getUserId()
        if (userId == null || hasPerformedInitialSync) {
            return
        }

        try {
            val localSessions = trainingList.toList()
            val cloudSessions = firebaseTraining.loadAllSessionsFromCloud(userId)

            // Upload local sessions not in cloud
            val localSessionIds = localSessions.map { it.id }.toSet()
            val cloudSessionIds = cloudSessions.map { it.id }.toSet()
            val sessionsToUpload = localSessions.filter { !cloudSessionIds.contains(it.id) }

            for (session in sessionsToUpload) {
                firebaseTraining.syncSessionToCloud(userId, session)
            }

            // Merge local and cloud data
            val mergedSessions = mergeAndDeduplicateSessions(localSessions, cloudSessions)

            // Update local storage
            trainingList.clear()
            trainingList.addAll(mergedSessions.take(MAX_CLOUD_ENTRIES))
            saveTrainingToStorage()

            // Mark sync as completed
            hasPerformedInitialSync = true
            sharedPrefs.edit().putBoolean(INITIAL_SYNC_KEY, true).apply()

        } catch (e: Exception) {
            // Handle sync errors silently
        }
    }

    /**
     * Manually trigger cloud synchronization
     */
    suspend fun manualSyncWithCloud() {
        val userId = firebaseAuth.getUserId() ?: return

        try {
            val localSessions = trainingList.toList()
            val cloudSessions = firebaseTraining.loadAllSessionsFromCloud(userId)

            // Upload new local sessions
            val cloudSessionIds = cloudSessions.map { it.id }.toSet()
            val sessionsToUpload = localSessions.filter { !cloudSessionIds.contains(it.id) }

            for (session in sessionsToUpload) {
                firebaseTraining.syncSessionToCloud(userId, session)
            }

            // Merge and update
            val mergedSessions = mergeAndDeduplicateSessions(localSessions, cloudSessions)
            trainingList.clear()
            trainingList.addAll(mergedSessions.take(MAX_CLOUD_ENTRIES))
            saveTrainingToStorage()

        } catch (e: Exception) {
            throw e // Re-throw for UI error handling
        }
    }

    /**
     * Delete a training session
     */
    suspend fun  deleteSession(sessionId: String): Boolean {
        val session = trainingList.find { it.id == sessionId } ?: return false

        // Remove locally
        trainingList.remove(session)
        saveTrainingToStorage()

        // Delete from cloud if authenticated
        if (firebaseAuth.isSignedIn()) {
            firebaseAuth.getUserId()?.let { userId ->
                CoroutineScope(Dispatchers.IO).launch {
                    firebaseTraining.deleteSessionFromCloud(sessionId)
                }
            }
        }

        return true
    }


    /**
     * Get a specific training session by ID
     */
    fun getSession(id: String): TrainingSession? {
        return trainingList.find { it.id == id }
    }

    /**
     * Get training statistics
     */
    fun getTrainingStats(): TrainingStats {
        val totalSessions = trainingList.size
        val totalLetters = trainingList.sumOf { it.letters.size }

        val mostPracticedLetters = trainingList
            .flatMap { it.letters }
            .groupBy { it.letter }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        return TrainingStats(
            totalSessions = totalSessions,
            totalLetters = totalLetters,
            mostPracticedLetters = mostPracticedLetters,
            isCloudSynced = firebaseAuth.isSignedIn()
        )
    }

    /**
     * Merge local and cloud sessions, removing duplicates
     */
    private fun mergeAndDeduplicateSessions(local: List<TrainingSession>, cloud: List<TrainingSession>): List<TrainingSession> {
        val sessionMap = mutableMapOf<String, TrainingSession>()

        // Add local sessions first (they have latest changes)
        local.forEach { session ->
            sessionMap[session.id] = session
        }

        // Add cloud sessions that aren't already local
        cloud.forEach { session ->
            if (!sessionMap.containsKey(session.id)) {
                sessionMap[session.id] = session
            }
        }

        // Return sorted by timestamp (newest first)
        return sessionMap.values.sortedByDescending { it.timestamp }
    }

    /**
     * Save training data to local storage
     */
    private fun saveTrainingToStorage() {
        try {
            val json = gson.toJson(trainingList)
            sharedPrefs.edit().putString(TRAINING_HISTORY_KEY, json).apply()
        } catch (e: Exception) {
            // Handle storage errors silently
        }
    }

    /**
     * Load training data from local storage
     */
    private fun loadTrainingFromStorage() {
        try {
            val json = sharedPrefs.getString(TRAINING_HISTORY_KEY, null) ?: return

            val type = object : TypeToken<List<TrainingSession>>() {}.type
            val sessions: List<TrainingSession> = gson.fromJson(json, type)

            trainingList.clear()
            trainingList.addAll(sessions)

            // Load sync state
            hasPerformedInitialSync = sharedPrefs.getBoolean(INITIAL_SYNC_KEY, false)

        } catch (e: Exception) {
            // Handle loading errors silently
        }
    }


    /**
     * Data class for training statistics
     */
    data class TrainingStats(
        val totalSessions: Int,
        val totalLetters: Int,
        val mostPracticedLetters: List<Pair<Char, Int>>,
        val isCloudSynced: Boolean
    )
}