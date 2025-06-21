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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment responsible for camera functionality in the sign language translator.
 * Manages three camera use cases:
 * - Preview: Live camera feed for user to see
 * - ImageAnalysis: Processes frames for hand detection (640x480)
 * - ImageCapture: Takes actual photos when letters are detected (square format)
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val detectionViewModel: DetectionViewModel by viewModels({ requireParentFragment() })

    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarkerHelper: HandLandmarkerHelper? = null

    // Camera use cases
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null

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

        try {
            cameraExecutor = Executors.newSingleThreadExecutor()
            setupHandLandmarker()
            checkPermissionAndStartCamera()

            // Register this fragment as the capture provider
            detectionViewModel.setCameraFragment(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            showMessage("Error setting up camera: ${e.message}")
        }
    }

    /**
     * Initialize MediaPipe hand landmark detection
     */
    private fun setupHandLandmarker() {
        try {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerHelperListener = object : HandLandmarkerHelper.LandmarkerListener {
                    override fun onError(error: String, errorCode: Int) {
                        requireActivity().runOnUiThread {
                            showMessage("ML Error: $error")
                        }
                    }

                    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
                        try {
                            // Ensure fragment is still attached before updating UI
                            if (isAdded && _binding != null && resultBundle.results.isNotEmpty()) {
                                requireActivity().runOnUiThread {
                                    try {
                                        // Update overlay with hand landmarks
                                        binding.overlayView.setResults(
                                            resultBundle.results[0],
                                            resultBundle.inputImageWidth,
                                            resultBundle.inputImageHeight
                                        )
                                        binding.overlayView.invalidate()

                                        // Send results to detection pipeline
                                        detectionViewModel.processHandLandmarks(resultBundle.results[0])
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error updating UI with results", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing ML results", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HandLandmarker", e)
            showMessage("Failed to initialize ML: ${e.message}")
        }
    }

    /**
     * Check camera permission and start camera if granted
     */
    private fun checkPermissionAndStartCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Initialize and configure camera with all three use cases
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Ensure fragment is still attached
            if (!isAdded || _binding == null) {
                return@addListener
            }

            try {
                val cameraProvider = cameraProviderFuture.get()

                // Configure Preview for user interface
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

                // Configure ImageAnalysis for hand detection
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                if (isAdded && handLandmarkerHelper != null && !handLandmarkerHelper!!.isClose()) {
                                    handLandmarkerHelper?.detectLiveStream(imageProxy, true)
                                } else {
                                    imageProxy.close()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in image analysis", e)
                                imageProxy.close()
                            }
                        }
                    }

                // Configure ImageCapture for actual photo capture
                val displayMetrics = resources.displayMetrics
                val phoneWidth = displayMetrics.widthPixels

                imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(phoneWidth, phoneWidth)) // Square format
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    // Bind all three use cases
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Camera binding failed", exc)
                    safeShowMessage("Camera binding failed: ${exc.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error setting up camera", e)
                safeShowMessage("Camera setup failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Capture the current camera frame and return it as a properly oriented bitmap.
     * Called by DetectionViewModel when a letter needs to be recorded.
     */
    fun captureCurrentFrame(callback: (Bitmap?) -> Unit) {
        // Validate fragment state
        if (!isAdded || _binding == null) {
            callback(null)
            return
        }

        val imageCapture = imageCapture ?: run {
            callback(null)
            return
        }

        try {
            val context = context ?: run {
                callback(null)
                return
            }

            // Create temporary file for capture
            val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        try {
                            // Load and process the captured image
                            var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)

                            if (bitmap != null) {
                                // Apply proper orientation correction for front camera
                                val correctedBitmap = correctImageOrientation(bitmap)

                                if (correctedBitmap != bitmap) {
                                    bitmap.recycle()
                                }

                                callback(correctedBitmap)
                            } else {
                                callback(null)
                            }

                            // Clean up temporary file
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
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating capture", e)
            callback(null)
        }
    }

    /**
     * Correct image orientation for front camera capture.
     * Handles rotation and mirroring to match what user sees in preview.
     */
    private fun correctImageOrientation(bitmap: Bitmap): Bitmap {
        return try {
            val matrix = Matrix().apply {
                // Counter-rotate by 90° to fix the 270° camera rotation
                postRotate(-90f, bitmap.width / 2f, bitmap.height / 2f)
                // Mirror for front camera (flip horizontally)
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }

            val correctedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            correctedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error correcting image orientation, using original", e)
            bitmap
        }
    }

    /**
     * Check if camera permission is granted
     */
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Safely show message only if fragment is still attached
     */
    private fun safeShowMessage(message: String) {
        try {
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing message: $message", e)
        }
    }

    private fun showMessage(message: String) {
        safeShowMessage(message)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraExecutor.shutdown()
            handLandmarkerHelper?.clearHandLandmarker()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}