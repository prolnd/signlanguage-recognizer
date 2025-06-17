package com.example.signtranslator.adapters

import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.signtranslator.databinding.ItemSignDetailPageBinding
import com.example.signtranslator.models.SignHistoryEntry
import java.text.SimpleDateFormat
import java.util.*

class SignDetailPagerAdapter(
    private val signEntries: List<SignHistoryEntry>
) : RecyclerView.Adapter<SignDetailPagerAdapter.SignDetailPageViewHolder>() {

    companion object {
        private const val TAG = "SignDetailPagerAdapter"
    }

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
            Log.d(TAG, "Binding sign detail: '${entry.sign}' with bitmap: ${entry.bestFrame.bitmap?.let { "${it.width}x${it.height}" } ?: "null"}")

            // Display the captured image (large)
            val bitmap = entry.bestFrame.bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                Log.d(TAG, "Setting valid bitmap: ${bitmap.width}x${bitmap.height}")
                binding.ivSignImage.setImageBitmap(bitmap)
                binding.ivSignImage.alpha = 1.0f

                // Ensure ImageView is properly configured
                binding.ivSignImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivSignImage.visibility = android.view.View.VISIBLE

            } else {
                Log.w(TAG, "Bitmap is null or recycled, creating placeholder")
                val placeholderBitmap = createPlaceholderBitmap(entry.sign)
                binding.ivSignImage.setImageBitmap(placeholderBitmap)
                binding.ivSignImage.alpha = 0.7f
            }

            // Display the sign letter (large)
            binding.tvSignLetter.text = entry.sign.uppercase()

            // Display confidence with color coding and background
            val confidencePercent = (entry.bestFrame.confidence * 100).toInt()
            binding.tvConfidence.text = "Confidence: $confidencePercent%"

            // Set confidence color
            when {
                entry.bestFrame.confidence > 0.8f -> {
                    binding.tvConfidence.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    binding.tvConfidence.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E8"))
                }
                entry.bestFrame.confidence > 0.6f -> {
                    binding.tvConfidence.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                    binding.tvConfidence.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
                }
                else -> {
                    binding.tvConfidence.setTextColor(android.graphics.Color.parseColor("#F44336"))
                    binding.tvConfidence.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                }
            }

            // Add padding to confidence text
            binding.tvConfidence.setPadding(16, 8, 16, 8)

            // Display timestamp
            binding.tvTimestamp.text = "Detected: ${formatTimestamp(entry.timestamp)}"

            // Display frame info
            binding.tvFrameInfo.text = "${entry.allFrames.size} frame(s) captured"
        }

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

            Log.d(TAG, "Created placeholder bitmap for sign: $sign")
            return bitmap
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}