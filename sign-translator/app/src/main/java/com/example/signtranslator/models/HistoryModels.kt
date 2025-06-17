package com.example.signtranslator.models

import android.graphics.Bitmap

data class SignFrame(
    val sign: String,
    val confidence: Float,
    val bitmap: Bitmap,
    val timestamp: Long = System.currentTimeMillis()
)

data class SignHistoryEntry(
    val sign: String,
    val bestFrame: SignFrame,
    val allFrames: List<SignFrame>,
    val sentence: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TranslationHistoryEntry(
    val id: String = System.currentTimeMillis().toString(),
    val sentence: String,
    val signEntries: List<SignHistoryEntry>,
    val timestamp: Long = System.currentTimeMillis()
)