package com.example.signtranslator.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.signtranslator.databinding.FragmentControlsBinding
import com.example.signtranslator.models.DetectionState
import com.example.signtranslator.viewmodels.DetectionViewModel

/**
 * Fragment containing the control panel for sign language detection.
 * Displays current detection results, sentence building, and control buttons.
 * Features auto-add toggle, manual letter addition, and sentence management.
 */
class ControlsFragment : Fragment() {

    private var _binding: FragmentControlsBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
    }

    /**
     * Configure click listeners for all control buttons
     */
    private fun setupClickListeners() {
        binding.btnAddLetter.setOnClickListener {
            detectionViewModel.addLetterManually()
        }

        binding.btnAddSpace.setOnClickListener {
            detectionViewModel.addSpace()
        }

        binding.btnSaveToHistory.setOnClickListener {
            detectionViewModel.saveToHistory()
        }

        binding.btnClear.setOnClickListener {
            detectionViewModel.clearSentence()
        }

        binding.switchAutoAdd.setOnCheckedChangeListener { _, _ ->
            detectionViewModel.toggleAutoAdd()
        }
    }

    /**
     * Observe ViewModel changes and update UI accordingly
     */
    private fun observeViewModel() {
        detectionViewModel.detectionState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }

        detectionViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

    }

    /**
     * Update all UI elements based on current detection state
     */
    private fun updateUI(state: DetectionState) {
        updateDetectionDisplay(state)
        updateSentenceDisplay(state.sentence)
        updateControlsState(state)
    }

    /**
     * Update the detection result display with confidence color coding
     */
    private fun updateDetectionDisplay(state: DetectionState) {
        val result = state.currentResult

        if (result == null) {
            binding.tvCurrentLetter.text = "Detected: -"
            binding.tvConfidence.text = "Confidence: -"
            binding.tvCurrentLetter.setBackgroundColor(Color.parseColor("#F0F0F0"))
            return
        }

        // Build status text based on auto-add mode and progress
        val statusText = if (state.isAutoAddEnabled) {
            when {
                detectionViewModel.isWaitingForCooldown(result.sign) -> " (wait)"
                detectionViewModel.getAutoAddCurrentSign() == result.sign -> {
                    val progress = detectionViewModel.getAutoAddProgress()
                    when {
                        progress >= 100 -> " ✓"
                        progress >= 50 -> " ●●○"
                        progress >= 25 -> " ●○○"
                        else -> " ○○○"
                    }
                }
                else -> " (auto)"
            }
        } else ""

        binding.tvCurrentLetter.text = "Detected: ${result.sign}$statusText"
        binding.tvConfidence.text = "Confidence: ${(result.confidence * 100).toInt()}%"

        // Apply confidence-based color coding
        val color = when {
            result.confidence > 0.8f -> "#4CAF50" // Green
            result.confidence > 0.6f -> "#FF9800" // Orange
            else -> "#F44336" // Red
        }
        binding.tvCurrentLetter.setBackgroundColor(Color.parseColor(color))
    }

    /**
     * Update the sentence display
     */
    private fun updateSentenceDisplay(sentence: String) {
        binding.tvSentence.text = "Sentence: $sentence"
    }

    /**
     * Update control button states based on current detection state
     */
    private fun updateControlsState(state: DetectionState) {
        binding.switchAutoAdd.isChecked = state.isAutoAddEnabled

        // Configure Add Letter button based on mode
        if (state.isAutoAddEnabled) {
            binding.btnAddLetter.alpha = 0.5f
            binding.btnAddLetter.text = "Auto Mode"
        } else {
            binding.btnAddLetter.alpha = 1.0f
            binding.btnAddLetter.text = "Add Letter"
            binding.btnAddLetter.isEnabled = state.currentResult?.confidence ?: 0f > 0.7f
        }

        // Configure Save button based on content availability
        val hasSentence = state.sentence.isNotBlank()
        binding.btnSaveToHistory.isEnabled = hasSentence
        binding.btnSaveToHistory.alpha = if (hasSentence) 1.0f else 0.5f

        binding.btnSaveToHistory.text = if (hasSentence) {
            "Save to History"
        } else {
            "Nothing to Save"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}