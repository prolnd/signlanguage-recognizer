package com.example.signtranslator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.signtranslator.R
import com.example.signtranslator.adapters.SignTrainerAdapter
import com.example.signtranslator.adapters.TrainingHistoryAdapter
import com.example.signtranslator.viewmodels.PracticeViewModel

/**
 * Fragment for sign language practice functionality.
 * Features text-to-sign conversion, swipeable letter gallery, and training history.
 * Allows users to practice signing by viewing ASL letter references.
 */
class PracticeFragment : Fragment() {

    private lateinit var viewModel: PracticeViewModel

    // Input section views
    private lateinit var inputEditText: EditText
    private lateinit var generateButton: Button
    private lateinit var inputContainer: LinearLayout

    // Practice slideshow views
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var backButton: Button
    private lateinit var saveButton: Button
    private lateinit var slideshowContainer: LinearLayout

    // History section views
    private lateinit var historyRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var historyContainer: LinearLayout
    private lateinit var emptyState: LinearLayout

    // Status and adapters
    private lateinit var syncStatusText: TextView
    private lateinit var signAdapter: SignTrainerAdapter
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
        initViewModel()
        setupAdapters()
        setupObservers()
        setupClickListeners()
    }

    /**
     * Initialize all view references
     */
    private fun initViews(view: View) {
        // Input section
        inputEditText = view.findViewById(R.id.input_edit_text)
        generateButton = view.findViewById(R.id.generate_button)
        inputContainer = view.findViewById(R.id.input_container)

        // Practice slideshow
        viewPager = view.findViewById(R.id.practice_view_pager)
        pageIndicator = view.findViewById(R.id.page_indicator)
        backButton = view.findViewById(R.id.back_button)
        saveButton = view.findViewById(R.id.save_button)
        slideshowContainer = view.findViewById(R.id.slideshow_container)

        // History section
        historyRecyclerView = view.findViewById(R.id.history_recycler_view)
        historyContainer = view.findViewById(R.id.history_container)
        emptyState = view.findViewById(R.id.empty_state)

        // Status
        syncStatusText = view.findViewById(R.id.sync_status_text)
    }

    /**
     * Initialize ViewModel
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[PracticeViewModel::class.java]
    }

    /**
     * Setup RecyclerView and ViewPager adapters
     */
    private fun setupAdapters() {
        // Sign slideshow adapter for practice mode
        signAdapter = SignTrainerAdapter()
        viewPager.adapter = signAdapter

        // Handle page changes in practice slideshow
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageIndicator(position)
                viewModel.onPageChanged(position)
            }
        })

        // Training history adapter
        historyAdapter = TrainingHistoryAdapter(
            onItemClick = { session ->
                // Load session for practice
                viewModel.loadHistorySession(session)
                inputEditText.setText(session.sentence)
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
        viewModel.signLetters.observe(viewLifecycleOwner) { letters ->
            signAdapter.updateLetters(letters)
            updateViewState(letters.isNotEmpty())
        }

        viewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (position != viewPager.currentItem) {
                viewPager.setCurrentItem(position, true)
            }
        }

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
                viewModel.generatePractice(sentence)
            } else {
                Toast.makeText(context, "Please enter something to practice", Toast.LENGTH_SHORT).show()
            }
        }

        backButton.setOnClickListener {
            // Exit practice mode and return to input/history view
            viewModel.exitPractice()
            inputEditText.text.clear()
        }

        saveButton.setOnClickListener {
            viewModel.saveCurrentSession()
            Toast.makeText(context, "Practice session saved!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update UI state based on practice mode
     */
    private fun updateViewState(inPracticeMode: Boolean) {
        if (inPracticeMode) {
            // Show practice slideshow
            historyContainer.visibility = View.GONE
            emptyState.visibility = View.GONE
            slideshowContainer.visibility = View.VISIBLE

            // Update controls for practice mode
            generateButton.text = "🔄 Practice Again"
            backButton.visibility = View.VISIBLE
            saveButton.visibility = View.VISIBLE

            updatePageIndicator(0)
        } else {
            // Show input and history
            slideshowContainer.visibility = View.GONE
            historyContainer.visibility = View.VISIBLE

            // Reset controls to input mode
            generateButton.text = "📚 Start"
            backButton.visibility = View.GONE
            saveButton.visibility = View.GONE
        }
    }

    /**
     * Show/hide empty state based on history availability
     */
    private fun updateHistoryVisibility(hasHistory: Boolean) {
        if (!hasHistory && slideshowContainer.visibility == View.GONE) {
            historyContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        }
    }

    /**
     * Update page indicator text
     */
    private fun updatePageIndicator(position: Int) {
        val total = signAdapter.itemCount
        if (total > 0) {
            pageIndicator.text = "Letter ${position + 1} of $total"
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
}