// adapters/TrainingHistoryAdapter.kt (Compact version for combined view)
package com.example.signtranslator.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.signtranslator.databinding.ItemTrainingBinding
import com.example.signtranslator.models.TrainingSession
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.toColorInt

class TrainingHistoryAdapter(
    private val onItemClick: (TrainingSession) -> Unit,
    private val onItemLongClick: ((TrainingSession) -> Boolean)? = null
) : ListAdapter<TrainingSession, TrainingHistoryAdapter.CompactTrainingViewHolder>(TrainingDiffCallback()) {

    companion object {
        private const val TAG = "TrainingHistoryAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompactTrainingViewHolder {
        val binding = ItemTrainingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CompactTrainingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompactTrainingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CompactTrainingViewHolder(
        private val binding: ItemTrainingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: TrainingSession) {
            Log.d(TAG, "=== BINDING COMPACT TRAINING SESSION ===")
            Log.d(TAG, "Session: '${session.sentence}' with ${session.letters.size} letters")

            binding.tvSentence.text = session.sentence
            binding.tvInfo.text = "${session.letters.size} letters • ${formatTimestamp(session.timestamp)}"

            // Create a compact preview showing first few letters
            createCompactLetterPreview(session)

            binding.root.setOnClickListener {
                onItemClick(session)
            }

            onItemLongClick?.let { longClickListener ->
                binding.root.setOnLongClickListener {
                    longClickListener(session)
                }
            }
        }

        private fun createCompactLetterPreview(session: TrainingSession) {
            Log.d(TAG, "Creating compact letter preview for: ${session.sentence}")

            // Create a small bitmap showing letters
            val previewBitmap = Bitmap.createBitmap(120, 40, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(previewBitmap)

            // Background
            canvas.drawColor(Color.parseColor("#E3F2FD"))

            // Draw up to 4 letters
            val lettersToShow = session.letters.take(4)
            val letterWidth = 120f / lettersToShow.size.coerceAtLeast(1)

            lettersToShow.forEachIndexed { index, trainingLetter ->
                val x = index * letterWidth + (letterWidth / 2)
                val y = 20f

                // Draw letter circle background
                val circlePaint = Paint().apply {
                    color = Color.parseColor("#2196F3")
                    isAntiAlias = true
                }
                canvas.drawCircle(x, y, 12f, circlePaint)

                // Draw letter text
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 14f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }

                val letterText = if (trainingLetter.letter == ' ') "␣" else trainingLetter.letter.toString()
                canvas.drawText(letterText, x, y + 4f, textPaint)
            }

            // If more than 4 letters, show "..."
            if (session.letters.size > 4) {
                val textPaint = Paint().apply {
                    color = "#666666".toColorInt()
                    textSize = 12f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("...", 110f, 25f, textPaint)
            }

        }

        private fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "now"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    private class TrainingDiffCallback : DiffUtil.ItemCallback<TrainingSession>() {
        override fun areItemsTheSame(oldItem: TrainingSession, newItem: TrainingSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrainingSession, newItem: TrainingSession): Boolean {
            return oldItem == newItem
        }
    }
}