package com.example.signtranslator.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.signtranslator.R
import com.example.signtranslator.models.SignLetter
import com.example.signtranslator.models.TrainingLetter
import com.example.signtranslator.models.TrainingSession
import com.example.signtranslator.utils.TrainingHistoryManager
import com.example.signtranslator.utils.FirebaseAuthManager
import kotlinx.coroutines.launch

/**
 * ViewModel for practice mode functionality.
 * Manages sign letter practice sessions, training history, and cloud synchronization.
 * Converts text to swipeable ASL reference images for learning.
 */
class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val trainingHistoryManager = TrainingHistoryManager(application)
    private val firebaseAuth = FirebaseAuthManager(application)

    // LiveData for UI updates
    private val _signLetters = MutableLiveData<List<SignLetter>>()
    val signLetters: LiveData<List<SignLetter>> = _signLetters

    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    private val _trainingHistory = MutableLiveData<List<TrainingSession>>()
    val trainingHistory: LiveData<List<TrainingSession>> = _trainingHistory

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _syncStatus = MutableLiveData<String>()
    val syncStatus: LiveData<String> = _syncStatus

    // Session tracking variables
    private var currentSentence: String = ""
    private var sessionStartTime: Long = 0
    private var isInPracticeMode = false

    // Map letters to drawable resources
    private val letterToResourceMap = mapOf(
        'A' to R.drawable.a, 'B' to R.drawable.b, 'C' to R.drawable.c, 'D' to R.drawable.d,
        'E' to R.drawable.e, 'F' to R.drawable.f, 'G' to R.drawable.g, 'H' to R.drawable.h,
        'I' to R.drawable.i, 'J' to R.drawable.j, 'K' to R.drawable.k, 'L' to R.drawable.l,
        'M' to R.drawable.m, 'N' to R.drawable.n, 'O' to R.drawable.o, 'P' to R.drawable.p,
        'Q' to R.drawable.q, 'R' to R.drawable.r, 'S' to R.drawable.s, 'T' to R.drawable.t,
        'U' to R.drawable.u, 'V' to R.drawable.v, 'W' to R.drawable.w, 'X' to R.drawable.x,
        'Y' to R.drawable.y, 'Z' to R.drawable.b
    )

    init {
        _currentPosition.value = 0
        _signLetters.value = emptyList()
        _errorMessage.value = ""
        loadHistory()
        updateSyncStatus()

        // Listen for authentication changes
        firebaseAuth.addAuthStateListener { isSignedIn ->
            updateSyncStatus()
            if (isSignedIn) {
                // Auto-sync when user signs in
                viewModelScope.launch {
                    syncWithCloud()
                }
            }
        }
    }

    /**
     * Generate practice session from input text
     */
    fun generatePractice(sentence: String) {
        val letters = sentence.uppercase()
            .filter { it.isLetter() || it == ' ' }
            .mapNotNull { char ->
                letterToResourceMap[char]?.let { resourceId ->
                    SignLetter(char, resourceId)
                }
            }

        if (letters.isEmpty()) {
            _errorMessage.value = "Please enter letters to practice"
            return
        }

        startNewSession(sentence, letters)
    }

    /**
     * Load a previous training session for practice
     */
    fun loadHistorySession(session: TrainingSession) {
        val letters = session.letters.map { trainingLetter ->
            SignLetter(trainingLetter.letter, trainingLetter.imageResourceId)
        }

        if (letters.isNotEmpty()) {
            startNewSession(session.sentence, letters)
        }
    }

    /**
     * Exit practice mode without saving
     */
    fun exitPractice() {
        clearPracticeMode()
    }

    /**
     * Handle page changes in the practice slideshow
     */
    fun onPageChanged(position: Int) {
        if (!isInPracticeMode) return

        val letters = _signLetters.value ?: return
        if (position < 0 || position >= letters.size) return

        _currentPosition.value = position
    }

    /**
     * Save the current practice session to history
     */
    fun saveCurrentSession() {
        if (isInPracticeMode) {
            viewModelScope.launch {
                saveSessionToHistory()
                loadHistory()
            }
        }
    }

    /**
     * Delete a training session from history
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (trainingHistoryManager.deleteSession(sessionId)) {
                loadHistory()
            }
        }
    }

    /**
     * Refresh training history from storage
     */
    fun loadHistory() {
        _trainingHistory.value = trainingHistoryManager.getTrainingHistory()
    }

    /**
     * Manually trigger cloud synchronization
     */
    private suspend fun syncWithCloud() {
        if (firebaseAuth.isSignedIn()) {
            try {
                _syncStatus.value = "Syncing with cloud..."
                trainingHistoryManager.manualSyncWithCloud()
                loadHistory() // Refresh with synced data
                updateSyncStatus()
            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message}"
                updateSyncStatus()
            }
        }
    }

    /**
     * Update sync status display
     */
    private fun updateSyncStatus() {
        val stats = trainingHistoryManager.getTrainingStats()
        _syncStatus.value = if (stats.isCloudSynced) {
            "☁️ ${stats.totalSessions} sessions synced"
        } else {
            "📱 ${stats.totalSessions}/10 local sessions"
        }
    }

    /**
     * Start a new practice session
     */
    private fun startNewSession(sentence: String, letters: List<SignLetter>) {
        currentSentence = sentence
        sessionStartTime = System.currentTimeMillis()
        isInPracticeMode = true

        _signLetters.value = letters
        _currentPosition.value = 0
        _errorMessage.value = ""
    }

    /**
     * Save current session to training history
     */
    private suspend fun saveSessionToHistory() {
        val letters = _signLetters.value
        if (currentSentence.isNotEmpty() && letters != null && letters.isNotEmpty()) {

            // Create training letters
            val trainingLetters = letters.map { signLetter ->
                TrainingLetter(
                    letter = signLetter.letter,
                    imageResourceId = signLetter.imageResourceId
                )
            }

            // Save to training history manager
            trainingHistoryManager.addTrainingSession(
                sentence = currentSentence,
                letters = trainingLetters
            )

            updateSyncStatus()
        }
    }

    /**
     * Clear practice mode and reset state
     */
    private fun clearPracticeMode() {
        isInPracticeMode = false
        currentSentence = ""
        sessionStartTime = 0

        _signLetters.value = emptyList()
        _currentPosition.value = 0
    }
}