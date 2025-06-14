package com.example.signtranslator.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.example.signtranslator.databinding.FragmentControlsBinding
import com.example.signtranslator.models.DetectionState
import com.example.signtranslator.viewmodels.DetectionViewModel

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

    private fun observeViewModel() {
        detectionViewModel.detectionState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }

        detectionViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            Log.d("ControlsFragment", "Error message: $message")
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        detectionViewModel.historyUpdated.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                Toast.makeText(requireContext(), "Translation saved to history!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(state: DetectionState) {
        updateDetectionDisplay(state)
        updateSentenceDisplay(state.sentence)
        updateControlsState(state)
    }

    private fun updateDetectionDisplay(state: DetectionState) {
        val result = state.currentResult

        if (result == null) {
            binding.tvCurrentLetter.text = "Detected: -"
            binding.tvConfidence.text = "Confidence: -"
            binding.tvCurrentLetter.setBackgroundColor(Color.parseColor("#F0F0F0"))
            return
        }

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

        val color = when {
            result.confidence > 0.8f -> "#4CAF50"
            result.confidence > 0.6f -> "#FF9800"
            else -> "#F44336"
        }
        binding.tvCurrentLetter.setBackgroundColor(Color.parseColor(color))
    }

    private fun updateSentenceDisplay(sentence: String) {
        binding.tvSentence.text = "Sentence: $sentence"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            binding.tvSentence.letterSpacing = 0f
        }
    }

    private fun updateControlsState(state: DetectionState) {
        binding.switchAutoAdd.isChecked = state.isAutoAddEnabled

        // Update Add Letter button
        if (state.isAutoAddEnabled) {
            binding.btnAddLetter.alpha = 0.5f
            binding.btnAddLetter.text = "Auto Mode"
        } else {
            binding.btnAddLetter.alpha = 1.0f
            binding.btnAddLetter.text = "Add Letter"
            binding.btnAddLetter.isEnabled = state.currentResult?.confidence ?: 0f > 0.7f
        }

        // Update Save button - enable only if there's content to save
        val hasSentence = state.sentence.isNotBlank()
        binding.btnSaveToHistory.isEnabled = hasSentence
        binding.btnSaveToHistory.alpha = if (hasSentence) 1.0f else 0.5f

        // Update button text based on content
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