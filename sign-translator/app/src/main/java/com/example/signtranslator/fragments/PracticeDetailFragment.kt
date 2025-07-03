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
import androidx.viewpager2.widget.ViewPager2
import com.example.signtranslator.R
import com.example.signtranslator.adapters.SignTrainerAdapter
import com.example.signtranslator.viewmodels.PracticeViewModel
import kotlin.getValue

/**
 * Fragment for practicing sign language with swipeable letter cards.
 * Displays sign reference images in a ViewPager2 with navigation controls.
 */
class PracticeDetailFragment : Fragment() {

    private val viewModel: PracticeViewModel by activityViewModels()
    private lateinit var signAdapter: SignTrainerAdapter

    // Practice slideshow views
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var backButton: Button
    private lateinit var saveButton: Button
    private lateinit var inputEditText: EditText
    private lateinit var practiceAgainButton: Button

    private var sentence: String = ""
    private var sessionId: String? = null
    private var isResuming: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            sentence = it.getString("sentence", "")
            sessionId = it.getString("sessionId")
            isResuming = it.getBoolean("isResuming", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_practice_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupAdapters()
        setupObservers()
        setupClickListeners()
        initializePracticeSession()
    }

    /**
     * Initialize all view references
     */
    private fun initViews(view: View) {
        viewPager = view.findViewById(R.id.practice_view_pager)
        pageIndicator = view.findViewById(R.id.page_indicator)
        backButton = view.findViewById(R.id.back_button)
        saveButton = view.findViewById(R.id.save_button)
        inputEditText = view.findViewById(R.id.input_edit_text)
        practiceAgainButton = view.findViewById(R.id.practice_button)
    }


    /**
     * Setup ViewPager adapter
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
    }

    /**
     * Observe ViewModel changes and update UI
     */
    private fun setupObservers() {
        viewModel.signLetters.observe(viewLifecycleOwner) { letters ->
            signAdapter.updateLetters(letters)
            updatePageIndicator(0)
        }

        viewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (position != viewPager.currentItem) {
                viewPager.setCurrentItem(position, true)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Configure click listeners for interactive elements
     */
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            // Navigate back to practice fragment
            findNavController().navigateUp()
        }

        saveButton.setOnClickListener {
            viewModel.saveCurrentSession()
            Toast.makeText(context, "Practice session saved!", Toast.LENGTH_SHORT).show()

            // Navigate back after saving
            findNavController().navigateUp()
        }

        practiceAgainButton.setOnClickListener {
            // Restart the practice session with the same sentence
            viewModel.generatePractice(sentence)
            Toast.makeText(context, "Restarting practice session", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Initialize practice session based on arguments
     */
    private fun initializePracticeSession() {
        inputEditText.setText(sentence)

        if (isResuming && sessionId != null) {
            // Load existing session
            viewModel.loadHistorySession(sessionId!!)
        } else {
            // Create new practice session
            viewModel.generatePractice(sentence)
        }
    }

    /**
     * Update page indicator text
     */
    private fun updatePageIndicator(position: Int) {
        val total = signAdapter.itemCount
        if (total > 0) {
            pageIndicator.text = "Letter ${position + 1} of $total"
        } else {
            pageIndicator.text = "Loading..."
        }
    }
}