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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by activityViewModels()

    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        try {
            setupChildFragments()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up child fragments", e)
        }
    }

    private fun setupChildFragments() {
        Log.d(TAG, "Setting up child fragments")

        try {
            // Add camera fragment
            val cameraFragment = CameraFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.cameraContainer, cameraFragment)
                .commitAllowingStateLoss()

            // Add controls fragment
            val controlsFragment = ControlsFragment()
            childFragmentManager.beginTransaction()
                .replace(R.id.controlsContainer, controlsFragment)
                .commitAllowingStateLoss()

            Log.d(TAG, "Child fragments added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding child fragments", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        _binding = null
    }
}