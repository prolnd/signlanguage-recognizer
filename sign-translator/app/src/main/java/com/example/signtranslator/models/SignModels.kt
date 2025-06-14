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