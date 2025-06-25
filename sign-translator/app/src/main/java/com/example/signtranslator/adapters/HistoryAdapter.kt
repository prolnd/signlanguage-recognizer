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