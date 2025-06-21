package com.example.signtranslator.models

/**
 * Represents a complete practice session with sentence and letters
 */
data class TrainingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sentence: String,
    val letters: List<TrainingLetter>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a single letter in a training session
 */
data class TrainingLetter(
    val letter: Char,
    val imageResourceId: Int
)