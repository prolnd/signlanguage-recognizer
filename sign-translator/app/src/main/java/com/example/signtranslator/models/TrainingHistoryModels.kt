package com.example.signtranslator.models

data class TrainingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sentence: String,
    val letters: List<TrainingLetter>,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0, // Time spent practicing in milliseconds
    val completionRate: Float = 100f // Percentage of letters practiced
)


data class TrainingLetter(
    val letter: Char,
    val imageResourceId: Int,
    val viewedAt: Long = System.currentTimeMillis(),
    val viewDuration: Long = 0, // How long user spent on this letter
    val timesViewed: Int = 1
)