package com.example.signtranslator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.signtranslator.adapters.SignDetailPagerAdapter
import com.example.signtranslator.databinding.FragmentHistoryDetailBinding
import com.example.signtranslator.viewmodels.DetectionViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment displaying detailed view of a translation history entry.
 * Shows swipeable sign images with ViewPager2, sentence info, and navigation controls.
 */
class HistoryDetailFragment : Fragment() {

    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by activityViewModels()
    private lateinit var signDetailAdapter: SignDetailPagerAdapter

    private var historyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyId = arguments?.getString("historyId")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        loadHistoryEntry()
    }

    /**
     * Configure click listeners for navigation
     */
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    /**
     * Load and display the history entry details
     */
    private fun loadHistoryEntry() {
        historyId?.let { id ->
            val entry = detectionViewModel.getHistoryEntry(id)
            if (entry != null) {
                // Display entry metadata
                binding.tvSentence.text = entry.sentence
                binding.tvTimestamp.text = formatTimestamp(entry.timestamp)
                binding.tvSignCount.text = "${entry.signEntries.size} signs detected"

                // Setup swipeable sign gallery
                setupSignGallery(entry.signEntries)
            } else {
                // Entry not found - navigate back
                findNavController().navigateUp()
            }
        } ?: run {
            // No ID provided - navigate back
            findNavController().navigateUp()
        }
    }

    /**
     * Configure ViewPager2 for swipeable sign images
     */
    private fun setupSignGallery(signEntries: List<com.example.signtranslator.models.SignHistoryEntry>) {
        signDetailAdapter = SignDetailPagerAdapter(signEntries)
        binding.viewPagerSigns.adapter = signDetailAdapter

        // Initialize page indicator
        updatePageIndicator(0, signEntries.size)

        // Update page indicator when swiping
        binding.viewPagerSigns.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageIndicator(position, signEntries.size)
            }
        })
    }

    /**
     * Update the page indicator text
     */
    private fun updatePageIndicator(currentPage: Int, totalPages: Int) {
        binding.tvPageIndicator.text = if (totalPages > 0) {
            "${currentPage + 1} / $totalPages"
        } else {
            "0 / 0"
        }
    }

    /**
     * Format timestamp for detailed view display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}