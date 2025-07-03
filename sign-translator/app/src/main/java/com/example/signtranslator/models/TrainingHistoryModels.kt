package com.example.signtranslator.models

/**
 * Represents a complete practice session with sentence and letters
 */
data class TrainingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sentence: String,
    val letters: List<SignLetter>,
    val timestamp: Long = System.currentTimeMillis()
)

