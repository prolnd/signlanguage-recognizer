package com.example.signtranslator.models

import android.graphics.Bitmap

/**
 * Represents a single captured frame of a detected sign with metadata
 */
data class SignFrame(
    val sign: String,
    val confidence: Float,
    val bitmap: Bitmap,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a complete sign detection entry with captured frame
 */
data class SignHistoryEntry(
    val sign: String,
    val signFrame: SignFrame,
    val sentence: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Represents a complete translation with sentence and all detected signs
 */
data class TranslationHistoryEntry(
    val id: String = System.currentTimeMillis().toString(),
    val sentence: String,
    val signEntries: List<SignHistoryEntry>,
    val timestamp: Long = System.currentTimeMillis()
)