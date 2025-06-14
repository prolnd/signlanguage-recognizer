package com.example.signtranslator.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
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
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.launch

class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val signClassifier = SignClassifier(application)
    private val autoAddManager = AutoAddManager()
    private val historyManager = HistoryManager(application)

    private val _detectionState = MutableLiveData(DetectionState())
    val detectionState: LiveData<DetectionState> = _detectionState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _historyUpdated = MutableLiveData<Boolean>()
    val historyUpdated: LiveData<Boolean> = _historyUpdated

    // Sign stability tracking
    private var lastDetection = ""
    private var detectionCount = 0
    private val stableDetectionThreshold = 3

    // History tracking
    private val currentSessionSigns = mutableListOf<SignHistoryEntry>()

    // Camera fragment reference for image capture
    private var cameraFragment: CameraFragment? = null

    companion object {
        private const val TAG = "DetectionViewModel"
    }

    fun setCameraFragment(fragment: CameraFragment) {
        cameraFragment = fragment
        Log.d(TAG, "Camera fragment set for image capture")
    }

    fun processHandLandmarks(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) return

        viewModelScope.launch {
            try {
                val landmarks = result.landmarks()[0].map { landmark ->
                    listOf(landmark.x(), landmark.y(), landmark.z())
                }

                val signResult = signClassifier.classifySign(landmarks)
                signResult?.let {
                    processSignResult(it)
                }

            } catch (e: Exception) {
                _errorMessage.value = "Error processing landmarks: ${e.message}"
                Log.e(TAG, "Error processing landmarks", e)
            }
        }
    }

    private fun processSignResult(result: SignResult) {
        // Validate stability
        if (result.sign == lastDetection) {
            detectionCount++
        } else {
            lastDetection = result.sign
            detectionCount = 1
        }

        if (detectionCount >= stableDetectionThreshold && result.confidence > 0.6f) {
            val currentState = _detectionState.value ?: DetectionState()
            _detectionState.value = currentState.copy(currentResult = result)

            // Handle auto-add if enabled
            if (currentState.isAutoAddEnabled) {
                if (autoAddManager.processSign(result)) {
                    Log.d(TAG, "Auto-adding letter: ${result.sign}")
                    addLetterToSentence(result.sign, true)
                }
            }
        }
    }

    fun addLetterManually() {
        val currentState = _detectionState.value ?: return
        val result = currentState.currentResult ?: return

        Log.d(TAG, "Manual add letter called for: ${result.sign} with confidence: ${result.confidence}")

        if (result.confidence > 0.7f) {
            addLetterToSentence(result.sign, false)
        } else {
            val errorMsg = "Low confidence (${(result.confidence * 100).toInt()}%) or no detection"
            _errorMessage.value = errorMsg
            Log.w(TAG, errorMsg)
        }
    }

    fun addSpace() {
        val currentState = _detectionState.value ?: return
        val newSentence = currentState.sentence + " "
        _detectionState.value = currentState.copy(sentence = newSentence)
        Log.d(TAG, "Space added. New sentence: '$newSentence'")
    }

    fun saveToHistory() {
        val currentState = _detectionState.value ?: return

        Log.d(TAG, "=== SAVE TO HISTORY DEBUG ===")
        Log.d(TAG, "Current sentence: '${currentState.sentence}' (length: ${currentState.sentence.length})")
        Log.d(TAG, "Current session signs count: ${currentSessionSigns.size}")

        if (currentSessionSigns.isNotEmpty()) {
            Log.d(TAG, "Signs in session:")
            currentSessionSigns.forEachIndexed { index, sign ->
                Log.d(TAG, "  [$index] Sign: '${sign.sign}', Confidence: ${sign.bestFrame.confidence}")
            }
        }

        if (currentState.sentence.isBlank()) {
            val errorMsg = "No sentence to save"
            _errorMessage.value = errorMsg
            Log.w(TAG, errorMsg)
            return
        }

        if (currentSessionSigns.isEmpty()) {
            val errorMsg = "No signs captured to save"
            _errorMessage.value = errorMsg
            Log.w(TAG, errorMsg)
            return
        }

        Log.d(TAG, "Saving translation to history: '${currentState.sentence}' with ${currentSessionSigns.size} signs")
        historyManager.addTranslation(currentState.sentence, currentSessionSigns.toList())
        _historyUpdated.value = true
        Log.d(TAG, "Translation saved successfully")
    }

    fun clearSentence() {
        Log.d(TAG, "Clear sentence called")
        finishCurrentTranslation()

        val currentState = _detectionState.value ?: return
        _detectionState.value = currentState.copy(
            sentence = "",
            currentResult = null
        )
        resetDetection()
        autoAddManager.reset()
        currentSessionSigns.clear()
        Log.d(TAG, "Sentence cleared and session reset")
    }

    fun finishCurrentTranslation() {
        val currentState = _detectionState.value ?: return
        if (currentState.sentence.isNotBlank() && currentSessionSigns.isNotEmpty()) {
            Log.d(TAG, "Finishing translation: '${currentState.sentence}' with ${currentSessionSigns.size} signs")
            historyManager.addTranslation(currentState.sentence, currentSessionSigns.toList())
            _historyUpdated.value = true
        }
    }

    fun toggleAutoAdd() {
        val currentState = _detectionState.value ?: return
        val newAutoAddState = !currentState.isAutoAddEnabled
        _detectionState.value = currentState.copy(isAutoAddEnabled = newAutoAddState)

        Log.d(TAG, "Auto-add toggled to: $newAutoAddState")

        if (!newAutoAddState) {
            autoAddManager.reset()
        }
    }

    fun getAutoAddProgress(): Int = autoAddManager.getHoldProgress()

    fun getAutoAddCurrentSign(): String = autoAddManager.getCurrentSign()

    fun isWaitingForCooldown(sign: String): Boolean = autoAddManager.isWaitingForCooldown(sign)

    fun isModelReady(): Boolean = signClassifier.isReady()

    // History methods
    fun getHistory() = historyManager.getHistory()

    fun getHistoryEntry(id: String) = historyManager.getEntry(id)

    fun deleteHistoryEntry(id: String): Boolean {
        val deleted = historyManager.deleteEntry(id)
        if (deleted) {
            _historyUpdated.value = true
        }
        return deleted
    }

    fun clearHistory() {
        historyManager.clearHistory()
        _historyUpdated.value = true
    }

    private fun addLetterToSentence(letter: String, wasAutoAdded: Boolean) {
        val currentState = _detectionState.value ?: return
        val cleanLetter = letter.replace(Regex("[\\r\\n\\t]"), "").lowercase().trim()
        val newSentence = currentState.sentence + cleanLetter

        Log.d(TAG, "=== ADD LETTER WITH IMAGE CAPTURE ===")
        Log.d(TAG, "Adding letter '$cleanLetter' to sentence. Auto-added: $wasAutoAdded")
        Log.d(TAG, "New sentence: '$newSentence'")
        Log.d(TAG, "Camera fragment available: ${cameraFragment != null}")
        Log.d(TAG, "Current result available: ${currentState.currentResult != null}")

        // Capture actual camera frame when letter is added
        if (cameraFragment != null && currentState.currentResult != null) {
            Log.d(TAG, "Initiating camera capture...")

            cameraFragment!!.captureCurrentFrame { bitmap ->
                if (bitmap != null) {
                    Log.d(TAG, "✅ Successfully captured camera frame: ${bitmap.width}x${bitmap.height}")

                    // Create sign entry with captured bitmap
                    val signFrame = SignFrame(
                        sign = currentState.currentResult!!.sign,
                        confidence = currentState.currentResult!!.confidence,
                        bitmap = bitmap
                    )

                    val signEntry = SignHistoryEntry(
                        sign = currentState.currentResult!!.sign,
                        bestFrame = signFrame,
                        allFrames = listOf(signFrame),
                        sentence = newSentence
                    )

                    currentSessionSigns.add(signEntry)
                    Log.d(TAG, "✅ Added sign entry to session. Total signs: ${currentSessionSigns.size}")

                } else {
                    Log.e(TAG, "❌ Camera capture failed")
                }
            }
        } else {
            Log.w(TAG, "❌ Missing requirements for capture:")
            Log.w(TAG, "   Camera fragment: ${cameraFragment != null}")
            Log.w(TAG, "   Current result: ${currentState.currentResult != null}")
        }

        _detectionState.value = currentState.copy(sentence = newSentence)
    }

    private fun resetDetection() {
        lastDetection = ""
        detectionCount = 0
    }

    override fun onCleared() {
        super.onCleared()
        signClassifier.cleanup()
        cameraFragment = null
    }
}