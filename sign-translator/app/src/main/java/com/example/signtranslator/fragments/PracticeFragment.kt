// fragments/PracticeFragment.kt (Final fix - removed lifecycle saving)
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

class PracticeFragment : Fragment() {

    private lateinit var viewModel: PracticeViewModel
    private lateinit var inputEditText: EditText
    private lateinit var generateButton: Button
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var backButton: Button
    private lateinit var saveButton: Button
    private lateinit var historyRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var historyContainer: LinearLayout
    private lateinit var slideshowContainer: LinearLayout
    private lateinit var inputContainer: LinearLayout
    private lateinit var emptyState: LinearLayout

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

    private fun initViews(view: View) {
        inputEditText = view.findViewById(R.id.input_edit_text)
        generateButton = view.findViewById(R.id.generate_button)
        viewPager = view.findViewById(R.id.practice_view_pager)
        pageIndicator = view.findViewById(R.id.page_indicator)
        backButton = view.findViewById(R.id.back_button)
        saveButton = view.findViewById(R.id.save_button)
        historyRecyclerView = view.findViewById(R.id.history_recycler_view)
        historyContainer = view.findViewById(R.id.history_container)
        slideshowContainer = view.findViewById(R.id.slideshow_container)
        inputContainer = view.findViewById(R.id.input_container)
        emptyState = view.findViewById(R.id.empty_state)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[PracticeViewModel::class.java]
    }

    private fun setupAdapters() {
        // Sign slideshow adapter
        signAdapter = SignTrainerAdapter()
        viewPager.adapter = signAdapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageIndicator(position)
                viewModel.onPageChanged(position)
            }
        })

        // History adapter
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
    }

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
            // Clean exit - saves and clears everything
            viewModel.exitPractice()
            inputEditText.text.clear()
        }

        saveButton.setOnClickListener {
            viewModel.saveCurrentSession()
            Toast.makeText(context, "Practice session saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateViewState(inPracticeMode: Boolean) {
        if (inPracticeMode) {
            // Show slideshow mode
            historyContainer.visibility = View.GONE
            emptyState.visibility = View.GONE
            slideshowContainer.visibility = View.VISIBLE

            // Update input section for practice mode
            generateButton.text = "🔄 Practice Again"
            backButton.visibility = View.VISIBLE
            saveButton.visibility = View.VISIBLE

            updatePageIndicator(0)
        } else {
            // Show history/input mode
            slideshowContainer.visibility = View.GONE
            historyContainer.visibility = View.VISIBLE

            // Reset input section
            generateButton.text = "📚 Start"
            backButton.visibility = View.GONE
            saveButton.visibility = View.GONE
        }
    }

    private fun updateHistoryVisibility(hasHistory: Boolean) {
        if (!hasHistory && slideshowContainer.visibility == View.GONE) {
            historyContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        }
    }

    private fun updatePageIndicator(position: Int) {
        val total = signAdapter.itemCount
        if (total > 0) {
            pageIndicator.text = "Letter ${position + 1} of $total"
        }
    }

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