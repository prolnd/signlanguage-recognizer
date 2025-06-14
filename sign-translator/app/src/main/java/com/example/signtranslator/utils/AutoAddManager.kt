package com.example.signtranslator.utils

import com.example.signtranslator.models.SignResult

class AutoAddManager {

    private var lastAutoAddTime = 0L
    private var autoAddSignStart = 0L
    private var autoAddCurrentSign = ""
    private var lastAddedSign = ""

    companion object {
        private const val AUTO_ADD_HOLD_TIME = 1000L
        private const val AUTO_ADD_COOLDOWN = 800L
        private const val PREVENT_DUPLICATE_TIME = 1500L
        private const val MIN_CONFIDENCE = 0.70f
    }

    fun processSign(result: SignResult): Boolean {
        val currentTime = System.currentTimeMillis()

        if (result.confidence < MIN_CONFIDENCE) {
            resetState()
            return false
        }

        if (result.sign == lastAddedSign &&
            (currentTime - lastAutoAddTime) < PREVENT_DUPLICATE_TIME) {
            return false
        }

        if (result.sign == autoAddCurrentSign) {
            if (autoAddSignStart > 0L) {
                val holdTime = currentTime - autoAddSignStart

                if (holdTime >= AUTO_ADD_HOLD_TIME &&
                    (currentTime - lastAutoAddTime) >= AUTO_ADD_COOLDOWN) {

                    lastAutoAddTime = currentTime
                    lastAddedSign = result.sign
                    resetState()
                    return true
                }
            }
        } else {
            autoAddCurrentSign = result.sign
            autoAddSignStart = currentTime
        }

        return false
    }

    fun getHoldProgress(): Int {
        if (autoAddSignStart <= 0L) return 0
        val holdTime = System.currentTimeMillis() - autoAddSignStart
        return (holdTime.toFloat() / AUTO_ADD_HOLD_TIME * 100).toInt().coerceAtMost(100)
    }

    fun getCurrentSign(): String = autoAddCurrentSign

    fun isWaitingForCooldown(sign: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return sign == lastAddedSign &&
                (currentTime - lastAutoAddTime) < PREVENT_DUPLICATE_TIME
    }

    fun reset() {
        resetState()
        lastAddedSign = ""
        lastAutoAddTime = 0L
    }

    private fun resetState() {
        autoAddCurrentSign = ""
        autoAddSignStart = 0L
    }
}