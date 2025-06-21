package com.example.signtranslator.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.signtranslator.R
import com.example.signtranslator.databinding.FragmentHomeBinding
import com.example.signtranslator.viewmodels.DetectionViewModel

/**
 * Main container fragment for the sign language detection interface.
 * Manages layout with camera (top 2/3) and controls (bottom 1/3).
 * Hosts CameraFragment and ControlsFragment as child fragments.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            setupChildFragments()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up child fragments", e)
        }
    }

    /**
     * Initialize and add child fragments for camera and controls
     */
    private fun setupChildFragments() {
        try {
            // Add camera fragment to top container (2/3 of screen)
            val cameraFragment = CameraFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.cameraContainer, cameraFragment)
                .commitAllowingStateLoss()

            // Add controls fragment to bottom container (1/3 of screen)
            val controlsFragment = ControlsFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.controlsContainer, controlsFragment)
                .commitAllowingStateLoss()

        } catch (e: Exception) {
            Log.e(TAG, "Error adding child fragments", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}