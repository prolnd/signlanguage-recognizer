package com.example.signtranslator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.signtranslator.R
import com.example.signtranslator.adapters.TrainingHistoryAdapter
import com.example.signtranslator.viewmodels.PracticeViewModel

/**
 * Fragment for sign language practice functionality.
 * Shows input section for text entry and history of previous practice sessions.
 * Navigates to PracticeDetailFragment for actual practice slideshow.
 */
class PracticeFragment : Fragment() {

    private val viewModel: PracticeViewModel by activityViewModels()

    // Input section views
    private lateinit var inputEditText: EditText
    private lateinit var generateButton: Button
    private lateinit var inputContainer: LinearLayout


    // History section views
    private lateinit var historyRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var historyContainer: LinearLayout
    private lateinit var emptyState: LinearLayout

    // Status and adapters
    private lateinit var syncStatusText: TextView
    private lateinit var historyAdapter: TrainingHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_practice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupAdapters()
        setupObservers()
        setupClickListeners()
        loadHistory()
    }

    /**
     * Initialize all view references
     */
    private fun initViews(view: View) {
        // Input section
        inputEditText = view.findViewById(R.id.input_edit_text)
        generateButton = view.findViewById(R.id.generate_button)
        inputContainer = view.findViewById(R.id.input_container)

        // History section
        historyRecyclerView = view.findViewById(R.id.history_recycler_view)
        historyContainer = view.findViewById(R.id.history_container)
        emptyState = view.findViewById(R.id.empty_state)

        // Status
        syncStatusText = view.findViewById(R.id.sync_status_text)
    }

    /**
     * Setup RecyclerView adapter
     */
    private fun setupAdapters() {
        // Training history adapter
        historyAdapter = TrainingHistoryAdapter(
            onItemClick = { session ->
                // Navigate to practice detail with existing session
                val bundle = Bundle().apply {
                    putString("sessionId", session.id)
                    putString("sentence", session.sentence)
                    putBoolean("isResuming", true)
                }
                findNavController().navigate(R.id.action_practice_to_detail, bundle)
            },
            onItemLongClick = { session ->
                showDeleteDialog(session.id, session.sentence)
                true
            }
        )

        historyRecyclerView.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    /**
     * Observe ViewModel changes and update UI
     */
    private fun setupObservers() {
        viewModel.trainingHistory.observe(viewLifecycleOwner) { sessions ->
            historyAdapter.submitList(sessions)
            updateHistoryVisibility(sessions.isNotEmpty())
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.syncStatus.observe(viewLifecycleOwner) { status ->
            syncStatusText.text = status
        }
    }

    /**
     * Configure click listeners for interactive elements
     */
    private fun setupClickListeners() {
        generateButton.setOnClickListener {
            val sentence = inputEditText.text.toString().trim()
            if (sentence.isNotEmpty()) {
                // Navigate to practice detail with new session
                val bundle = Bundle().apply {
                    putString("sentence", sentence)
                    putBoolean("isResuming", false)
                }
                findNavController().navigate(R.id.action_practice_to_detail, bundle)
            } else {
                Toast.makeText(context, "Please enter something to practice", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Load history from ViewModel
     */
    private fun loadHistory() {
        viewModel.loadHistory()
    }

    /**
     * Show/hide empty state based on history availability
     */
    private fun updateHistoryVisibility(hasHistory: Boolean) {
        if (hasHistory) {
            historyContainer.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        } else {
            historyContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        }
    }

    /**
     * Show confirmation dialog for deleting training sessions
     */
    private fun showDeleteDialog(sessionId: String, sentence: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Practice Session")
            .setMessage("Delete practice for \"$sentence\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSession(sessionId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh history when returning to this fragment
        loadHistory()
    }
}