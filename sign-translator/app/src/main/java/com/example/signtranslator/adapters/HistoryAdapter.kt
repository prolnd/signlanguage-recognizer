package com.example.signtranslator.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.signtranslator.databinding.ItemHistoryBinding
import com.example.signtranslator.models.TranslationHistoryEntry
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onItemClick: (TranslationHistoryEntry) -> Unit,
    private val onItemLongClick: ((TranslationHistoryEntry) -> Boolean)? = null
) : ListAdapter<TranslationHistoryEntry, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    companion object {
        private const val TAG = "HistoryAdapter"
    }

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
            Log.d(TAG, "=== BINDING HISTORY ENTRY ===")
            Log.d(TAG, "Entry: '${entry.sentence}' with ${entry.signEntries.size} signs")

            binding.tvSentence.text = entry.sentence
            binding.tvSignCount.text = "${entry.signEntries.size} signs"
            binding.tvTimestamp.text = formatTimestamp(entry.timestamp)

            // DIAGNOSTIC: Always show a test bitmap first
            showDiagnosticImage()

            // Then try to show the real image
            setupImageViewAfterLayout(entry)

            binding.root.setOnClickListener {
                onItemClick(entry)
            }

            onItemLongClick?.let { longClickListener ->
                binding.root.setOnLongClickListener {
                    longClickListener(entry)
                }
            }
        }

        private fun showDiagnosticImage() {
            Log.d(TAG, "=== SHOWING DIAGNOSTIC IMAGE ===")

            // Create a simple test bitmap
            val testBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(testBitmap)

            // Fill with bright red background
            canvas.drawColor(Color.RED)

            // Draw some white text
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 40f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("TEST", 100f, 100f, paint)

            // Set this test bitmap
            binding.ivPreview.setImageBitmap(testBitmap)
            binding.ivPreview.visibility = View.VISIBLE

            Log.d(TAG, "✅ Test bitmap set - you should see a red square with 'TEST'")

            // After 2 seconds, try to show the real image
            binding.ivPreview.postDelayed({
                Log.d(TAG, "Now attempting to show real image...")
                // We'll override this in setupImageViewAfterLayout
            }, 2000)
        }

        private fun setupImageViewAfterLayout(entry: TranslationHistoryEntry) {
            Log.d(TAG, "Setting up real ImageView for entry: ${entry.sentence}")

            binding.ivPreview.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.ivPreview.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    Log.d(TAG, "Layout complete - ImageView dimensions: ${binding.ivPreview.width}x${binding.ivPreview.height}")

                    // Wait a bit then set the real image
                    binding.ivPreview.postDelayed({
                        setRealImageToView(entry)
                    }, 3000) // 3 seconds after the test image
                }
            })
        }

        private fun setRealImageToView(entry: TranslationHistoryEntry) {
            Log.d(TAG, "=== SETTING REAL IMAGE ===")
            Log.d(TAG, "Setting real image to view for: ${entry.sentence}")

            if (entry.signEntries.isNotEmpty()) {
                val firstSign = entry.signEntries.first()
                val originalBitmap = firstSign.bestFrame.bitmap

                Log.d(TAG, "Original bitmap: ${originalBitmap?.let { "${it.width}x${it.height}, config: ${it.config}, hasAlpha: ${it.hasAlpha()}" } ?: "NULL"}")

                if (originalBitmap != null && !originalBitmap.isRecycled) {
                    Log.d(TAG, "Analyzing bitmap content...")

                    // Check if the bitmap is actually just transparent/black
                    val pixelSample = analyzePixels(originalBitmap)
                    Log.d(TAG, "Pixel analysis: $pixelSample")

                    // Create a processed version
                    val processedBitmap = createVisibleBitmap(originalBitmap, firstSign.sign)

                    Log.d(TAG, "Setting processed bitmap...")
                    binding.ivPreview.setImageBitmap(processedBitmap)

                    Log.d(TAG, "✅ Real bitmap should now be visible!")

                } else {
                    Log.w(TAG, "Original bitmap is invalid, keeping test image")
                }
            } else {
                Log.w(TAG, "No sign entries, keeping test image")
            }
        }

        private fun analyzePixels(bitmap: Bitmap): String {
            val width = bitmap.width
            val height = bitmap.height

            // Sample some pixels
            val centerPixel = bitmap.getPixel(width / 2, height / 2)
            val topLeftPixel = bitmap.getPixel(0, 0)
            val bottomRightPixel = bitmap.getPixel(width - 1, height - 1)

            return "Center: ${Integer.toHexString(centerPixel)}, " +
                    "TopLeft: ${Integer.toHexString(topLeftPixel)}, " +
                    "BottomRight: ${Integer.toHexString(bottomRightPixel)}"
        }

        private fun createVisibleBitmap(originalBitmap: Bitmap, sign: String): Bitmap {
            Log.d(TAG, "Creating visible version of bitmap")

            // Scale down
            val scaledOriginal = Bitmap.createScaledBitmap(originalBitmap, 200, 200, true)

            // Create a new bitmap with enhanced visibility
            val visibleBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(visibleBitmap)

            // Draw the original (might be dark/transparent)
            canvas.drawBitmap(scaledOriginal, 0f, 0f, null)

            // Overlay with semi-transparent colored background and sign letter
            val overlayPaint = Paint().apply {
                color = Color.argb(128, 0, 255, 0) // Semi-transparent green
            }
            canvas.drawRect(0f, 0f, 200f, 200f, overlayPaint)

            // Draw sign letter prominently
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            }
            canvas.drawText(sign.uppercase(), 100f, 120f, textPaint)

            // Draw border
            val borderPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawRect(2f, 2f, 198f, 198f, borderPaint)

            return visibleBitmap
        }

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

    private class HistoryDiffCallback : DiffUtil.ItemCallback<TranslationHistoryEntry>() {
        override fun areItemsTheSame(oldItem: TranslationHistoryEntry, newItem: TranslationHistoryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TranslationHistoryEntry, newItem: TranslationHistoryEntry): Boolean {
            return oldItem == newItem
        }
    }
}