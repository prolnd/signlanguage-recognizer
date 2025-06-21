package com.example.signtranslator.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.signtranslator.R
import com.example.signtranslator.adapters.HistoryAdapter
import com.example.signtranslator.databinding.FragmentHistoryBinding
import com.example.signtranslator.viewmodels.DetectionViewModel

/**
 * Fragment displaying the translation history list.
 * Shows all saved translations with preview images and metadata.
 * Supports navigation to detail view and deletion functionality.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by activityViewModels()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadHistory()
    }

    /**
     * Configure RecyclerView with adapter and click handlers
     */
    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = { historyEntry ->
                // Navigate to detailed view
                val bundle = Bundle().apply {
                    putString("historyId", historyEntry.id)
                }
                findNavController().navigate(R.id.action_history_to_detail, bundle)
            },
            onItemLongClick = { historyEntry ->
                // Show delete confirmation
                showDeleteConfirmationDialog(historyEntry.id, historyEntry.sentence)
                true
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    /**
     * Configure UI click listeners
     */
    private fun setupClickListeners() {
        binding.btnClearHistory?.setOnClickListener {
            showClearAllConfirmationDialog()
        }
    }

    /**
     * Observe ViewModel for history updates
     */
    private fun observeViewModel() {
        detectionViewModel.historyUpdated.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                loadHistory()
            }
        }
    }

    /**
     * Load history from ViewModel and update UI
     */
    private fun loadHistory() {
        val history = detectionViewModel.getHistory()
        historyAdapter.submitList(history)

        // Show empty state if no history exists
        if (history.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * Show confirmation dialog for deleting a single history entry
     */
    private fun showDeleteConfirmationDialog(historyId: String, sentence: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Translation")
            .setMessage("Are you sure you want to delete \"$sentence\"?")
            .setPositiveButton("Delete") { _, _ ->
                detectionViewModel.deleteHistoryEntry(historyId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show confirmation dialog for clearing all history
     */
    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete all translation history? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                detectionViewModel.clearHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh history when returning to this fragment
        loadHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}