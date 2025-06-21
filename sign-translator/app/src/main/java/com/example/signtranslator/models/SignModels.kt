package com.example.signtranslator.models

/**
 * Represents the result of sign detection from ML model
 */
data class SignResult(
    val sign: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents the current state of the detection system
 */
data class DetectionState(
    val currentResult: SignResult? = null,
    val sentence: String = "",
    val isAutoAddEnabled: Boolean = false,
    val isProcessing: Boolean = false
)

/**
 * Represents a sign letter for practice mode with reference image
 */
data class SignLetter(
    val letter: Char,
    val imageResourceId: Int, // Resource ID for the ASL reference image
    val description: String = "Sign for letter $letter"
)