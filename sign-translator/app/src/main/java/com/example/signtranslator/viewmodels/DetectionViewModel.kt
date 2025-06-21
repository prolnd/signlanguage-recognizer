package com.example.signtranslator.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.signtranslator.fragments.CameraFragment
import com.example.signtranslator.models.DetectionState
import com.example.signtranslator.models.SignResult
import com.example.signtranslator.models.SignHistoryEntry
import com.example.signtranslator.models.SignFrame
import com.example.signtranslator.utils.AutoAddManager
import com.example.signtranslator.utils.HistoryManager
import com.example.signtranslator.utils.SignClassifier
import com.example.signtranslator.utils.FirebaseTranslationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Main ViewModel for sign language detection and translation functionality.
 * Coordinates between ML detection, sentence building, auto-add features, and history management.
 * Serves as the central hub for the detection pipeline.
 */
class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val signClassifier = SignClassifier(application)
    private val autoAddManager = AutoAddManager()
    private val historyManager = HistoryManager(application)
    private val firebaseManager = FirebaseTranslationManager()
    private val auth = FirebaseAuth.getInstance()

    // LiveData for UI updates
    private val _detectionState = MutableLiveData(DetectionState())
    val detectionState: LiveData<DetectionState> = _detectionState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _historyUpdated = MutableLiveData<Boolean>()
    val historyUpdated: LiveData<Boolean> = _historyUpdated

    // Detection stability tracking
    private var lastDetection = ""
    private var detectionCount = 0
    private val stableDetectionThreshold = 3

    // Current session data
    private val currentSessionSigns = mutableListOf<SignHistoryEntry>()

    // Camera fragment reference for image capture (weak reference to prevent memory leaks)
    private var cameraFragmentRef: WeakReference<CameraFragment>? = null

    /**
     * Set reference to camera fragment for photo capture
     */
    fun setCameraFragment(fragment: CameraFragment?) {
        cameraFragmentRef = if (fragment != null) {
            WeakReference(fragment)
        } else {
            null
        }
    }

    /**
     * Process hand landmarks from MediaPipe and convert to sign classification
     */
    fun processHandLandmarks(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) return

        viewModelScope.launch {
            try {
                // Extract x,y,z coordinates from landmarks
                val landmarks = result.landmarks()[0].map { landmark ->
                    listOf(landmark.x(), landmark.y(), landmark.z())
                }

                // Classify the sign using ML model
                val signResult = signClassifier.classifySign(landmarks)
                signResult?.let {
                    processSignResult(it)
                }

            } catch (e: Exception) {
                _errorMessage.value = "Error processing landmarks: ${e.message}"
            }
        }
    }

    /**
     * Process sign classification result and handle UI updates and auto-add logic
     */
    private fun processSignResult(result: SignResult) {
        try {
            // Check for detection stability to reduce noise
            if (result.sign == lastDetection) {
                detectionCount++
            } else {
                lastDetection = result.sign
                detectionCount = 1
            }

            // Only update UI with stable, confident detections
            if (detectionCount >= stableDetectionThreshold && result.confidence > 0.6f) {
                val currentState = _detectionState.value ?: DetectionState()
                _detectionState.value = currentState.copy(currentResult = result)

                // Handle auto-add if enabled
                if (currentState.isAutoAddEnabled) {
                    if (autoAddManager.processSign(result)) {
                        addLetterToSentence(result.sign, true)
                    }
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error processing sign result: ${e.message}"
        }
    }

    /**
     * Manually add the currently detected letter to the sentence
     */
    fun addLetterManually() {
        try {
            val currentState = _detectionState.value ?: return
            val result = currentState.currentResult ?: return

            if (result.confidence > 0.7f) {
                addLetterToSentence(result.sign, false)
            } else {
                _errorMessage.value = "Low confidence (${(result.confidence * 100).toInt()}%) or no detection"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error adding letter: ${e.message}"
        }
    }

    /**
     * Add a space to the current sentence
     */
    fun addSpace() {
        try {
            val currentState = _detectionState.value ?: return
            val newSentence = currentState.sentence + " "
            _detectionState.value = currentState.copy(sentence = newSentence)
        } catch (e: Exception) {
            _errorMessage.value = "Error adding space: ${e.message}"
        }
    }

    /**
     * Save the current translation to history
     */
    fun saveToHistory() {
        try {
            val currentState = _detectionState.value ?: return

            // Validate content before saving
            if (currentState.sentence.isBlank()) {
                _errorMessage.value = "No sentence to save"
                return
            }

            if (currentSessionSigns.isEmpty()) {
                _errorMessage.value = "No signs captured to save"
                return
            }

            // Save to history manager
            val signsToSave = currentSessionSigns.toList()
            val success = historyManager.addTranslation(currentState.sentence, signsToSave)

            if (success) {
                _historyUpdated.value = true
                clearCurrentSession()
                _errorMessage.value = "Translation saved successfully!"
            } else {
                _errorMessage.value = "Failed to save translation"
            }

        } catch (e: Exception) {
            _errorMessage.value = "Error saving to history: ${e.message}"
        }
    }

    /**
     * Clear the current sentence and session data
     */
    fun clearSentence() {
        try {
            finishCurrentTranslation()

            val currentState = _detectionState.value ?: return
            _detectionState.value = currentState.copy(
                sentence = "",
                currentResult = null
            )
            resetDetection()
            autoAddManager.reset()
            currentSessionSigns.clear()
        } catch (e: Exception) {
            _errorMessage.value = "Error clearing sentence: ${e.message}"
        }
    }

    /**
     * Toggle auto-add mode on/off
     */
    fun toggleAutoAdd() {
        try {
            val currentState = _detectionState.value ?: return
            val newAutoAddState = !currentState.isAutoAddEnabled
            _detectionState.value = currentState.copy(isAutoAddEnabled = newAutoAddState)

            if (!newAutoAddState) {
                autoAddManager.reset()
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error toggling auto-add: ${e.message}"
        }
    }

    // Auto-add progress methods
    fun getAutoAddProgress(): Int = autoAddManager.getHoldProgress()
    fun getAutoAddCurrentSign(): String = autoAddManager.getCurrentSign()
    fun isWaitingForCooldown(sign: String): Boolean = autoAddManager.isWaitingForCooldown(sign)

    // History management methods
    fun getHistory() = historyManager.getHistory()
    fun getHistoryEntry(id: String) = historyManager.getEntry(id)

    /**
     * Delete history entry both locally and from cloud
     */
    fun deleteHistoryEntry(id: String): Boolean {
        return try {
            // Delete locally first
            val deleted = historyManager.deleteEntry(id)

            if (deleted) {
                // Also delete from cloud if user is signed in
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    viewModelScope.launch {
                        try {
                            firebaseManager.deleteTranslationFromCloud(currentUser.uid, id)
                        } catch (e: Exception) {
                            // Cloud deletion failed, but local deletion succeeded
                            // This is acceptable - sync can handle it later
                        }
                    }
                }

                _historyUpdated.value = true
            }
            deleted
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all history both locally and from cloud
     */
    fun clearHistory() {
        try {
            // Clear locally first
            historyManager.clearHistory()

            // Also clear from cloud if user is signed in
            val currentUser = auth.currentUser
            if (currentUser != null) {
                viewModelScope.launch {
                    try {
                        firebaseManager.clearAllUserTranslations(currentUser.uid)
                    } catch (e: Exception) {
                        // Cloud clearing failed, but local clearing succeeded
                        // This is acceptable
                    }
                }
            }

            _historyUpdated.value = true
        } catch (e: Exception) {
            _errorMessage.value = "Error clearing history: ${e.message}"
        }
    }

    /**
     * Add a letter to the sentence and capture camera frame
     */
    private fun addLetterToSentence(letter: String, wasAutoAdded: Boolean) {
        try {
            val currentState = _detectionState.value ?: return

            // Clean the letter input
            val cleanLetter = letter.replace(Regex("[\\r\\n\\t]"), "").trim().uppercase()
            if (cleanLetter.isEmpty()) return

            val newSentence = currentState.sentence + cleanLetter

            // Capture camera frame for this letter
            val cameraFragment = cameraFragmentRef?.get()
            if (cameraFragment != null && currentState.currentResult != null) {
                cameraFragment.captureCurrentFrame { bitmap ->
                    try {
                        if (bitmap != null) {
                            // Create sign entry with captured bitmap
                            val signFrame = SignFrame(
                                sign = cleanLetter,
                                confidence = currentState.currentResult.confidence,
                                bitmap = bitmap
                            )

                            val signEntry = SignHistoryEntry(
                                sign = cleanLetter,
                                signFrame = signFrame,
                                sentence = newSentence
                            )

                            currentSessionSigns.add(signEntry)
                        }
                    } catch (e: Exception) {
                        // Handle error silently - continue without image
                    }
                }
            }

            _detectionState.value = currentState.copy(sentence = newSentence)
        } catch (e: Exception) {
            _errorMessage.value = "Error adding letter: ${e.message}"
        }
    }

    /**
     * Finish current translation if it has content
     */
    fun finishCurrentTranslation() {
        try {
            val currentState = _detectionState.value ?: return
            if (currentState.sentence.isNotBlank() && currentSessionSigns.isNotEmpty()) {
                historyManager.addTranslation(currentState.sentence, currentSessionSigns.toList())
                _historyUpdated.value = true
            }
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    /**
     * Clear current session and clean up resources
     */
    private fun clearCurrentSession() {
        try {
            val currentState = _detectionState.value ?: return
            _detectionState.value = currentState.copy(
                sentence = "",
                currentResult = null
            )

            resetDetection()
            autoAddManager.reset()

            // Clean up bitmap resources
            currentSessionSigns.forEach { signEntry ->
                signEntry.signFrame.bitmap?.recycle()
            }
            currentSessionSigns.clear()

        } catch (e: Exception) {
            // Handle error silently
        }
    }

    /**
     * Reset detection tracking variables
     */
    private fun resetDetection() {
        lastDetection = ""
        detectionCount = 0
    }

    override fun onCleared() {
        super.onCleared()
        try {
            signClassifier.cleanup()
            cameraFragmentRef = null
        } catch (e: Exception) {
            // Handle cleanup errors silently
        }
    }
}