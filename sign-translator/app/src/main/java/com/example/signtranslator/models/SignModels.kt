package com.example.signtranslator.models

data class SignResult(
    val sign: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class DetectionState(
    val currentResult: SignResult? = null,
    val sentence: String = "",
    val isAutoAddEnabled: Boolean = false,
    val isProcessing: Boolean = false
)

data class SignLetter(
    val letter: Char,
    val imageResourceId: Int, // Resource ID for the sign letter image
    val description: String = "Sign for letter $letter"
)