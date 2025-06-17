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

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadHistoryEntry() {
        historyId?.let { id ->
            val entry = detectionViewModel.getHistoryEntry(id)
            if (entry != null) {
                binding.tvSentence.text = entry.sentence
                binding.tvTimestamp.text = formatTimestamp(entry.timestamp)
                binding.tvSignCount.text = "${entry.signEntries.size} signs detected"

                // Setup ViewPager for swipeable signs
                signDetailAdapter = SignDetailPagerAdapter(entry.signEntries)
                binding.viewPagerSigns.adapter = signDetailAdapter

                // Setup page indicator
                binding.tvPageIndicator.text = if (entry.signEntries.isNotEmpty()) {
                    "1 / ${entry.signEntries.size}"
                } else {
                    "0 / 0"
                }

                // Update page indicator when swiping
                binding.viewPagerSigns.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        binding.tvPageIndicator.text = "${position + 1} / ${entry.signEntries.size}"
                    }
                })

            } else {
                // Entry not found, go back
                findNavController().navigateUp()
            }
        } ?: run {
            // No ID provided, go back
            findNavController().navigateUp()
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}