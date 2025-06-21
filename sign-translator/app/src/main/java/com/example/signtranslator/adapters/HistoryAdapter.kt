package com.example.signtranslator.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.signtranslator.databinding.ItemHistoryBinding
import com.example.signtranslator.models.TranslationHistoryEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for displaying translation history entries.
 * Shows translation sentences with preview images, timestamps, and sign counts.
 * Supports click to view details and long-click to delete.
 */
class HistoryAdapter(
    private val onItemClick: (TranslationHistoryEntry) -> Unit,
    private val onItemLongClick: ((TranslationHistoryEntry) -> Boolean)? = null
) : ListAdapter<TranslationHistoryEntry, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: TranslationHistoryEntry) {
            // Set basic text information
            binding.tvSentence.text = entry.sentence
            binding.tvSignCount.text = "${entry.signEntries.size} signs"
            binding.tvTimestamp.text = formatTimestamp(entry.timestamp)

            // Create preview image after layout is complete
            setupImageViewAfterLayout(entry)

            // Set click listeners
            binding.root.setOnClickListener {
                onItemClick(entry)
            }

            onItemLongClick?.let { longClickListener ->
                binding.root.setOnLongClickListener {
                    longClickListener(entry)
                }
            }
        }

        /**
         * Wait for ImageView layout completion before setting the preview image
         */
        private fun setupImageViewAfterLayout(entry: TranslationHistoryEntry) {


        }

        /**
         * Create and set the preview image for the history entry
         */
        private fun setPreviewImage(entry: TranslationHistoryEntry) {
            if (entry.signEntries.isNotEmpty()) {
                val firstSign = entry.signEntries.first()
                val originalBitmap = firstSign.signFrame.bitmap

                if (originalBitmap != null && !originalBitmap.isRecycled) {
                    // Create enhanced preview with sign letter overlay
                    val previewBitmap = createEnhancedPreview(originalBitmap, firstSign.sign)
                } else {
                    // Fallback to placeholder if bitmap is invalid
                    val placeholderBitmap = createPlaceholderPreview(firstSign.sign)
                }
            } else {
                // Create generic placeholder for entries without signs
                val placeholderBitmap = createPlaceholderPreview("?")
            }

        }

        /**
         * Create an enhanced preview image with the original photo and sign letter overlay
         */
        private fun createEnhancedPreview(originalBitmap: Bitmap, sign: String): Bitmap {
            // Scale to preview size
            val scaledOriginal = Bitmap.createScaledBitmap(originalBitmap, 200, 200, true)

            val enhancedBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(enhancedBitmap)

            // Draw the original image
            canvas.drawBitmap(scaledOriginal, 0f, 0f, null)

            // Add semi-transparent overlay for better text visibility
            val overlayPaint = Paint().apply {
                color = Color.argb(100, 0, 0, 0) // Semi-transparent black
            }
            canvas.drawRect(0f, 160f, 200f, 200f, overlayPaint)

            // Draw sign letter in bottom area
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
            canvas.drawText(sign.uppercase(), 100f, 185f, textPaint)

            // Clean up scaled bitmap if it's different from original
            if (scaledOriginal != originalBitmap) {
                scaledOriginal.recycle()
            }

            return enhancedBitmap
        }

        /**
         * Create a placeholder preview image when the original bitmap is not available
         */
        private fun createPlaceholderPreview(sign: String): Bitmap {
            val placeholderBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(placeholderBitmap)

            // Background gradient
            canvas.drawColor(Color.parseColor("#E0E0E0"))

            // Center sign letter
            val textPaint = Paint().apply {
                color = Color.parseColor("#757575")
                textSize = 80f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText(sign.uppercase(), 100f, 120f, textPaint)

            // Border
            val borderPaint = Paint().apply {
                color = Color.parseColor("#BDBDBD")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(1f, 1f, 199f, 199f, borderPaint)

            return placeholderBitmap
        }

        /**
         * Format timestamp for display (relative time or absolute date)
         */
        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "Just now"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    /**
     * DiffUtil callback for efficient RecyclerView updates
     */
    private class HistoryDiffCallback : DiffUtil.ItemCallback<TranslationHistoryEntry>() {
        override fun areItemsTheSame(oldItem: TranslationHistoryEntry, newItem: TranslationHistoryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TranslationHistoryEntry, newItem: TranslationHistoryEntry): Boolean {
            return oldItem == newItem
        }
    }
}