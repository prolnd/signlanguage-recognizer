package com.example.signtranslator.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.signtranslator.databinding.FragmentCameraBinding
import com.example.signtranslator.utils.HandLandmarkerHelper
import com.example.signtranslator.viewmodels.DetectionViewModel
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by viewModels({ requireParentFragment() })

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper

    // ImageCapture for actual camera capture
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val TAG = "CameraFragment"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showMessage("Camera permission required")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupHandLandmarker()
        checkPermissionAndStartCamera()

        // Set this fragment as the capture provider for the DetectionViewModel
        detectionViewModel.setCameraFragment(this)
    }

    private fun setupHandLandmarker() {
        try {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerHelperListener = object : HandLandmarkerHelper.LandmarkerListener {
                    override fun onError(error: String, errorCode: Int) {
                        showMessage("ML Error: $error")
                    }

                    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
                        if (resultBundle.results.isNotEmpty()) {
                            binding.overlayView.setResults(
                                resultBundle.results[0],
                                resultBundle.inputImageWidth,
                                resultBundle.inputImageHeight
                            )
                            binding.overlayView.invalidate()

                            // Pass results to DetectionViewModel (no camera view needed now)
                            detectionViewModel.processHandLandmarks(resultBundle.results[0])
                        }
                    }
                }
            )
        } catch (e: Exception) {
            showMessage("Failed to initialize ML: ${e.message}")
        }
    }

    private fun checkPermissionAndStartCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            @Suppress("DEPRECATION") // Using deprecated API for reliable resolution
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        handLandmarkerHelper.detectLiveStream(imageProxy, true)
                    }
                }

            // Add ImageCapture use case for actual photo capture
            // Try to get true square format, or capture large and don't crop
            val displayMetrics = resources.displayMetrics
            val phoneWidth = displayMetrics.widthPixels

            @Suppress("DEPRECATION")
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(phoneWidth, phoneWidth)) // Try square first
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            Log.d(TAG, "ImageCapture configured for ${phoneWidth}x${phoneWidth} target square format")

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                // Bind all three use cases: Preview, ImageAnalysis, and ImageCapture
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer,
                    imageCapture
                )
                Log.d(TAG, "Camera with ImageCapture bound successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
                showMessage("Camera binding failed")
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Capture the current camera frame and return it as a bitmap
     */
    fun captureCurrentFrame(callback: (Bitmap?) -> Unit) {
        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture not initialized")
            callback(null)
            return
        }

        Log.d(TAG, "Capturing current camera frame...")

        // Create a temporary file for the image
        val tempFile = File(requireContext().cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Image capture succeeded: ${tempFile.absolutePath}")

                    try {
                        // Load the captured image as bitmap
                        var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)

                        if (bitmap != null) {
                            Log.d(TAG, "Original captured image: ${bitmap.width}x${bitmap.height}")

                            // Mirror the bitmap for front camera (to match preview)
                            val mirroredBitmap = mirrorBitmap(bitmap)
                            bitmap.recycle()
                            bitmap = mirroredBitmap

                            // DON'T crop - keep the original size
                            Log.d(TAG, "✅ Successfully captured and mirrored bitmap: ${bitmap.width}x${bitmap.height} (no cropping)")
                            callback(bitmap)
                        } else {
                            Log.e(TAG, "Failed to decode captured image")
                            callback(null)
                        }

                        // Clean up temp file
                        tempFile.delete()

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing captured image", e)
                        tempFile.delete()
                        callback(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    tempFile.delete()
                    callback(null)
                }
            }
        )
    }

    private fun mirrorBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            preScale(-1.0f, 1.0f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::handLandmarkerHelper.isInitialized) {
            handLandmarkerHelper.clearHandLandmarker()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}