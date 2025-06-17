package com.example.signtranslator.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.TextureView
import android.view.View
import androidx.camera.view.PreviewView
import com.example.signtranslator.models.SignFrame
import com.example.signtranslator.models.SignHistoryEntry
import com.example.signtranslator.models.SignResult

class FrameCaptureManager {

    companion object {
        private const val TAG = "FrameCaptureManager"
    }

    /**
     * Capture a sign frame when letter is added to sentence
     */
    fun captureSignForHistory(result: SignResult, cameraView: View, sentence: String): SignHistoryEntry? {
        Log.d(TAG, "Attempting to capture sign: ${result.sign} with confidence: ${result.confidence}")

        try {
            // Validate inputs
            if (result.sign.isBlank()) {
                Log.w(TAG, "Cannot capture: sign is blank")
                return null
            }

            if (result.confidence < 0.1f) {
                Log.w(TAG, "Cannot capture: confidence too low (${result.confidence})")
                return null
            }

            Log.d(TAG, "Camera view type: ${cameraView::class.simpleName}")
            Log.d(TAG, "Camera view dimensions: ${cameraView.width}x${cameraView.height}")

            // Try to capture from PreviewView using TextureView method
            val bitmap = captureFromPreviewView(cameraView, result.sign)

            if (bitmap == null) {
                Log.w(TAG, "Camera capture failed, creating placeholder")
                return createPlaceholderEntry(result, sentence)
            }

            // Check if bitmap has actual content
            if (isBitmapMostlyBlack(bitmap)) {
                Log.w(TAG, "Captured bitmap is mostly black, creating placeholder")
                bitmap.recycle()
                return createPlaceholderEntry(result, sentence)
            }

            Log.d(TAG, "Successfully captured bitmap: ${bitmap.width}x${bitmap.height}")
            val frame = SignFrame(result.sign, result.confidence, bitmap)

            val entry = SignHistoryEntry(
                sign = result.sign,
                bestFrame = frame,
                allFrames = listOf(frame),
                sentence = sentence
            )

            Log.d(TAG, "✅ Successfully created SignHistoryEntry for: ${result.sign}")
            return entry

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame for history", e)
            return createPlaceholderEntry(result, sentence)
        }
    }

    private fun captureFromPreviewView(view: View, sign: String): Bitmap? {
        Log.d(TAG, "Attempting to capture from PreviewView")

        return try {
            // Wait a moment for the view to be ready
            Thread.sleep(100)

            when (view) {
                is PreviewView -> {
                    Log.d(TAG, "Capturing from PreviewView")

                    // Try to get the underlying TextureView or SurfaceView
                    val textureView = findTextureView(view)
                    if (textureView != null) {
                        Log.d(TAG, "Found TextureView, capturing...")
                        val bitmap = textureView.getBitmap()
                        if (bitmap != null) {
                            Log.d(TAG, "✅ TextureView capture successful: ${bitmap.width}x${bitmap.height}")
                            return bitmap
                        }
                    }

                    // Fallback: try standard view capture
                    Log.d(TAG, "Trying standard view capture as fallback")
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    bitmap
                }
                else -> {
                    Log.d(TAG, "Standard view capture")
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in capture method", e)
            null
        }
    }

    private fun findTextureView(view: View): TextureView? {
        return try {
            when (view) {
                is TextureView -> view
                is android.view.ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        val textureView = findTextureView(child)
                        if (textureView != null) return textureView
                    }
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding TextureView", e)
            null
        }
    }

    private fun isBitmapMostlyBlack(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        // Sample more pixels for better detection
        val sampleCount = 20
        var blackPixels = 0

        for (i in 0 until sampleCount) {
            val x = (width * i / sampleCount).coerceAtMost(width - 1)
            val y = (height * i / sampleCount).coerceAtMost(height - 1)

            val pixel = bitmap.getPixel(x, y)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)

            if (red < 30 && green < 30 && blue < 30) {
                blackPixels++
            }
        }

        val blackRatio = blackPixels.toFloat() / sampleCount
        val isMostlyBlack = blackRatio > 0.8f

        Log.d(TAG, "Bitmap black analysis: $blackPixels/$sampleCount pixels are black (${(blackRatio * 100).toInt()}%), mostly black: $isMostlyBlack")

        return isMostlyBlack
    }

    private fun createPlaceholderEntry(result: SignResult, sentence: String): SignHistoryEntry {
        Log.d(TAG, "Creating placeholder entry for sign: ${result.sign}")

        val placeholderBitmap = createPlaceholderBitmap(result.sign, result.confidence)
        val frame = SignFrame(result.sign, result.confidence, placeholderBitmap)

        return SignHistoryEntry(
            sign = result.sign,
            bestFrame = frame,
            allFrames = listOf(frame),
            sentence = sentence
        )
    }

    private fun createPlaceholderBitmap(sign: String, confidence: Float): Bitmap {
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color based on confidence
        val backgroundColor = when {
            confidence > 0.8f -> Color.parseColor("#E8F5E8") // Light green
            confidence > 0.6f -> Color.parseColor("#FFF3E0") // Light orange
            else -> Color.parseColor("#FFEBEE") // Light red
        }

        canvas.drawColor(backgroundColor)

        // Sign letter
        val textPaint = Paint().apply {
            color = when {
                confidence > 0.8f -> Color.parseColor("#2E7D32") // Dark green
                confidence > 0.6f -> Color.parseColor("#F57C00") // Dark orange
                else -> Color.parseColor("#C62828") // Dark red
            }
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
            color = textPaint.color
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val confidenceText = "${(confidence * 100).toInt()}%"
        canvas.drawText(confidenceText, centerX, size - 50f, confidencePaint)

        // Border
        val borderPaint = Paint().apply {
            color = textPaint.color
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(3f, 3f, size - 3f, size - 3f, borderPaint)

        Log.d(TAG, "Created placeholder bitmap for: $sign")
        return bitmap
    }

    // Legacy methods for compatibility
    fun startCapturingSign(sign: String) {
        Log.d(TAG, "Started capturing for sign: $sign (legacy method)")
    }

    fun captureFrame(result: SignResult, cameraView: View): SignFrame? {
        Log.d(TAG, "captureFrame called (legacy method)")
        return null
    }

    fun finishCapturingSign(sentence: String): SignHistoryEntry? {
        Log.d(TAG, "finishCapturingSign called (legacy method)")
        return null
    }

    fun stopCapturing() {
        Log.d(TAG, "Stopped capturing")
    }
}