package com.example.signtranslator.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.TextureView
import android.view.View
import androidx.camera.view.PreviewView
import com.example.signtranslator.models.SignFrame
import com.example.signtranslator.models.SignHistoryEntry
import com.example.signtranslator.models.SignResult

/**
 * Legacy frame capture manager - functionality moved to CameraFragment.
 * This class is kept for placeholder generation only.
 *
 * Note: Actual camera capture is now handled directly in CameraFragment.captureCurrentFrame()
 */
class FrameCaptureManager {

    /**
     * Create a placeholder entry when camera capture fails or for fallback scenarios
     */
    fun createPlaceholderEntry(result: SignResult, sentence: String): SignHistoryEntry {
        val placeholderBitmap = createPlaceholderBitmap(result.sign, result.confidence)
        val frame = SignFrame(result.sign, result.confidence, placeholderBitmap)

        return SignHistoryEntry(
            sign = result.sign,
            signFrame = frame,
            sentence = sentence
        )
    }

    /**
     * Create a styled placeholder bitmap with sign letter and confidence
     */
    private fun createPlaceholderBitmap(sign: String, confidence: Float): Bitmap {
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color based on confidence level
        val backgroundColor = when {
            confidence > 0.8f -> Color.parseColor("#E8F5E8") // Light green
            confidence > 0.6f -> Color.parseColor("#FFF3E0") // Light orange
            else -> Color.parseColor("#FFEBEE") // Light red
        }
        canvas.drawColor(backgroundColor)

        // Sign letter with confidence-based color
        val textColor = when {
            confidence > 0.8f -> Color.parseColor("#2E7D32") // Dark green
            confidence > 0.6f -> Color.parseColor("#F57C00") // Dark orange
            else -> Color.parseColor("#C62828") // Dark red
        }

        val textPaint = Paint().apply {
            color = textColor
            textSize = 120f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val centerX = size / 2f
        val centerY = size / 2f + 40f
        canvas.drawText(sign.uppercase(), centerX, centerY, textPaint)

        // Confidence percentage
        val confidencePaint = Paint().apply {
            color = textColor
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val confidenceText = "${(confidence * 100).toInt()}%"
        canvas.drawText(confidenceText, centerX, size - 50f, confidencePaint)

        // Border
        val borderPaint = Paint().apply {
            color = textColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(3f, 3f, size - 3f, size - 3f, borderPaint)

        return bitmap
    }
}