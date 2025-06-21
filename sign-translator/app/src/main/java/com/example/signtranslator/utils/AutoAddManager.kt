package com.example.signtranslator.utils

import com.example.signtranslator.models.SignResult

/**
 * Manages automatic letter addition when signs are held for a specified duration.
 * Implements hold-to-add functionality with cooldown periods and duplicate prevention.
 */
class AutoAddManager {

    private var lastAutoAddTime = 0L
    private var autoAddSignStart = 0L
    private var autoAddCurrentSign = ""
    private var lastAddedSign = ""

    companion object {
        private const val AUTO_ADD_HOLD_TIME = 1000L // Hold for 1 second to auto-add
        private const val AUTO_ADD_COOLDOWN = 800L // 800ms cooldown between additions
        private const val PREVENT_DUPLICATE_TIME = 1500L // 1.5s to prevent same sign duplicate
        private const val MIN_CONFIDENCE = 0.70f // Minimum confidence for auto-add
    }

    /**
     * Process a sign result and determine if it should be auto-added
     * @param result The detected sign result
     * @return true if the sign should be added to the sentence
     */
    fun processSign(result: SignResult): Boolean {
        val currentTime = System.currentTimeMillis()

        // Reject low confidence signs
        if (result.confidence < MIN_CONFIDENCE) {
            resetState()
            return false
        }

        // Prevent duplicate signs within time window
        if (result.sign == lastAddedSign &&
            (currentTime - lastAutoAddTime) < PREVENT_DUPLICATE_TIME) {
            return false
        }

        // Check if we're tracking the same sign
        if (result.sign == autoAddCurrentSign) {
            if (autoAddSignStart > 0L) {
                val holdTime = currentTime - autoAddSignStart

                // Add if held long enough and cooldown has passed
                if (holdTime >= AUTO_ADD_HOLD_TIME &&
                    (currentTime - lastAutoAddTime) >= AUTO_ADD_COOLDOWN) {

                    lastAutoAddTime = currentTime
                    lastAddedSign = result.sign
                    resetState()
                    return true
                }
            }
        } else {
            // New sign detected - start tracking
            autoAddCurrentSign = result.sign
            autoAddSignStart = currentTime
        }

        return false
    }

    /**
     * Get the current hold progress as a percentage (0-100)
     */
    fun getHoldProgress(): Int {
        if (autoAddSignStart <= 0L) return 0
        val holdTime = System.currentTimeMillis() - autoAddSignStart
        return (holdTime.toFloat() / AUTO_ADD_HOLD_TIME * 100).toInt().coerceAtMost(100)
    }

    /**
     * Get the currently tracked sign for auto-add
     */
    fun getCurrentSign(): String = autoAddCurrentSign

    /**
     * Check if a sign is in cooldown period (to prevent immediate re-addition)
     */
    fun isWaitingForCooldown(sign: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return sign == lastAddedSign &&
                (currentTime - lastAutoAddTime) < PREVENT_DUPLICATE_TIME
    }

    /**
     * Reset all tracking state
     */
    fun reset() {
        resetState()
        lastAddedSign = ""
        lastAutoAddTime = 0L
    }

    /**
     * Reset current sign tracking state
     */
    private fun resetState() {
        autoAddCurrentSign = ""
        autoAddSignStart = 0L
    }
}