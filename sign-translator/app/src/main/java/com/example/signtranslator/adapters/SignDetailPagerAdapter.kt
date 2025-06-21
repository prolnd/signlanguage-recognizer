package com.example.signtranslator.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.signtranslator.databinding.ItemSignDetailPageBinding
import com.example.signtranslator.models.SignHistoryEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewPager2 adapter for displaying detailed sign entries in swipeable pages.
 * Each page shows a large sign image, letter, confidence, and detection details.
 */
class SignDetailPagerAdapter(
    private val signEntries: List<SignHistoryEntry>
) : RecyclerView.Adapter<SignDetailPagerAdapter.SignDetailPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SignDetailPageViewHolder {
        val binding = ItemSignDetailPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SignDetailPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SignDetailPageViewHolder, position: Int) {
        holder.bind(signEntries[position])
    }

    override fun getItemCount(): Int = signEntries.size

    inner class SignDetailPageViewHolder(
        private val binding: ItemSignDetailPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: SignHistoryEntry) {
            // Display the captured image (large)
            val bitmap = entry.signFrame.bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                binding.ivSignImage.setImageBitmap(bitmap)
                binding.ivSignImage.alpha = 1.0f
                binding.ivSignImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivSignImage.visibility = android.view.View.VISIBLE
            } else {
                // Create placeholder if bitmap is unavailable
                val placeholderBitmap = createPlaceholderBitmap(entry.sign)
                binding.ivSignImage.setImageBitmap(placeholderBitmap)
                binding.ivSignImage.alpha = 0.7f
            }

            // Display the sign letter
            binding.tvSignLetter.text = entry.sign.uppercase()

            // Display confidence with color coding
            val confidencePercent = (entry.signFrame.confidence * 100).toInt()
            binding.tvConfidence.text = "Confidence: $confidencePercent%"

            // Apply confidence-based styling
            applyConfidenceColors(entry.signFrame.confidence)

            // Display detection metadata
            binding.tvTimestamp.text = "Detected: ${formatTimestamp(entry.timestamp)}"
            binding.tvFrameInfo.text = "Frame captured"
        }

        /**
         * Apply color coding based on confidence level
         */
        private fun applyConfidenceColors(confidence: Float) {
            val (textColor, backgroundColor) = when {
                confidence > 0.8f -> Pair("#4CAF50", "#E8F5E8") // Green
                confidence > 0.6f -> Pair("#FF9800", "#FFF3E0") // Orange
                else -> Pair("#F44336", "#FFEBEE") // Red
            }

            binding.tvConfidence.setTextColor(android.graphics.Color.parseColor(textColor))
            binding.tvConfidence.setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
            binding.tvConfidence.setPadding(16, 8, 16, 8)
        }

        /**
         * Create placeholder bitmap when original image is not available
         */
        private fun createPlaceholderBitmap(sign: String): Bitmap {
            val size = 300
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // Background
            val backgroundPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), backgroundPaint)

            // Sign letter in center
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 80f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val centerX = size / 2f
            val centerY = size / 2f + 30f // Offset for text baseline
            canvas.drawText(sign.uppercase(), centerX, centerY, textPaint)

            // Border
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 8f
            }
            canvas.drawRect(4f, 4f, size - 4f, size - 4f, borderPaint)

            return bitmap
        }

        /**
         * Format timestamp for detailed view (time only)
         */
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}