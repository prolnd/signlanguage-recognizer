package com.example.signtranslator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.signtranslator.R
import com.example.signtranslator.databinding.FragmentHomeBinding
import com.example.signtranslator.viewmodels.DetectionViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChildFragments()
    }

    private fun setupChildFragments() {
        // Add camera fragment
        childFragmentManager.beginTransaction()
            .replace(R.id.cameraContainer, CameraFragment())
            .commit()

        // Add controls fragment
        childFragmentManager.beginTransaction()
            .replace(R.id.controlsContainer, ControlsFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}