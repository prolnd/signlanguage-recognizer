package com.example.signtranslator


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.example.signtranslator.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var overlayView: OverlayView
    private lateinit var gestureClassifier: Recognizer

    // UI components for displaying results
    private lateinit var tvCurrentLetter: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvSentence: TextView
    private lateinit var btnAddLetter: Button
    private lateinit var btnAddSpace: Button
    private lateinit var btnClear: Button
    private lateinit var switchAutoAdd: Switch

    // Current detection results
    private var currentLetter = ""
    private var currentConfidence = 0f
    private var sentence = ""

    // Gesture detection stability
    private var lastDetection = ""
    private var detectionCount = 0
    private val stableDetectionThreshold = 3

    // Auto-add functionality
    private var isAutoAddEnabled = false
    private var lastAutoAddTime = 0L
    private val autoAddDelayMs = 800L // Reduced delay for fluency
    private var autoAddGestureStart = 0L
    private var autoAddCurrentGesture = ""
    private val autoAddHoldTime = 1000L // Reduced hold time for fluency
    private var lastAddedGesture = ""
    private val preventDuplicateTimeMs = 1500L // Prevent same letter for 1.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        cameraExecutor = Executors.newSingleThreadExecutor()
        gestureClassifier = Recognizer(this)

        // Get reference to views
        overlayView = findViewById(R.id.overlayView)
        tvCurrentLetter = findViewById(R.id.tvCurrentLetter)
        tvConfidence = findViewById(R.id.tvConfidence)
        tvSentence = findViewById(R.id.tvSentence)
        btnAddLetter = findViewById(R.id.btnAddLetter)
        btnAddSpace = findViewById(R.id.btnAddSpace)
        btnClear = findViewById(R.id.btnClear)
        switchAutoAdd = findViewById(R.id.switchAutoAdd)

        // Set up button listeners
        setupButtonListeners()

        // Initialize HandLandmarkerHelper with try-catch
        try {
            handLandmarkerHelper = HandLandmarkerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerHelperListener = object : HandLandmarkerHelper.LandmarkerListener {
                    override fun onError(error: String, errorCode: Int) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "ML Error: $error", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
                        runOnUiThread {
                            if (resultBundle.results.isNotEmpty()) {
                                overlayView.setResults(
                                    resultBundle.results[0],
                                    resultBundle.inputImageWidth,
                                    resultBundle.inputImageHeight
                                )
                                overlayView.invalidate()

                                // Perform gesture recognition
                                recognizeGesture(resultBundle.results[0])
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Request camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // Image Analysis for hand detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        handLandmarkerHelper.detectLiveStream(imageProxy, true)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera binding failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun setupButtonListeners() {
        // Auto-add switch
        switchAutoAdd.setOnCheckedChangeListener { _, isChecked ->
            isAutoAddEnabled = isChecked
            if (isChecked) {
                Toast.makeText(this, "Auto-add enabled: Hold gesture for 1 second", Toast.LENGTH_LONG).show()
                // Hide manual add button when auto-add is enabled
                btnAddLetter.alpha = 0.5f
                btnAddLetter.text = "Auto Mode"
            } else {
                Toast.makeText(this, "Auto-add disabled: Use manual buttons", Toast.LENGTH_SHORT).show()
                btnAddLetter.alpha = 1.0f
                btnAddLetter.text = "Add Letter"
            }
        }

        btnAddLetter.setOnClickListener {
            if (!isAutoAddEnabled) {
                if (currentLetter.isNotEmpty() && currentConfidence > 0.7f) {
                    addLetterToSentence(currentLetter)
                } else {
                    Toast.makeText(this, "Low confidence or no detection", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Auto-add is enabled. Hold gesture to add.", Toast.LENGTH_SHORT).show()
            }
        }

        btnAddSpace.setOnClickListener {
            sentence += " "
            updateSentenceDisplay()
        }

        btnClear.setOnClickListener {
            sentence = ""
            currentLetter = ""
            currentConfidence = 0f
            lastDetection = ""
            detectionCount = 0
            lastAddedGesture = ""
            resetAutoAddState()
            updateSentenceDisplay()
            updateDetectionDisplay()
        }
    }

    private fun recognizeGesture(result: HandLandmarkerResult) {
        if (result.landmarks().isEmpty()) return

        // Extract landmarks
        val landmarks = result.landmarks()[0].map { landmark ->
            listOf(landmark.x(), landmark.y(), landmark.z())
        }

        // Classify gesture using TensorFlow Lite
        val prediction = gestureClassifier.classifyGesture(landmarks)

        prediction?.let { (gesture, confidence) ->
            // Add stability check to reduce flickering
            if (gesture == lastDetection) {
                detectionCount++
            } else {
                lastDetection = gesture
                detectionCount = 1
            }

            // Only update if detection is stable and confident
            if (detectionCount >= stableDetectionThreshold && confidence > 0.6f) {
                currentLetter = gesture
                currentConfidence = confidence
                updateDetectionDisplay()

                // Handle auto-add functionality
                if (isAutoAddEnabled) {
                    handleAutoAdd(gesture, confidence)
                }
            }
        }
    }

    private fun handleAutoAdd(gesture: String, confidence: Float) {
        val currentTime = System.currentTimeMillis()

        // Only auto-add if confidence is high enough
        if (confidence < 0.70f) { // Lowered threshold for better fluency
            resetAutoAddState()
            return
        }

        // Don't add the same letter again too quickly
        if (gesture == lastAddedGesture &&
            (currentTime - lastAutoAddTime) < preventDuplicateTimeMs) {
            return
        }

        // Check if this is the same gesture being held
        if (gesture == autoAddCurrentGesture) {
            // Gesture is being held, check if held long enough
            if (autoAddGestureStart > 0L) {
                val holdTime = currentTime - autoAddGestureStart

                if (holdTime >= autoAddHoldTime &&
                    (currentTime - lastAutoAddTime) >= autoAddDelayMs) {

                    // Auto-add the gesture
                    addLetterToSentence(gesture)
                    lastAutoAddTime = currentTime
                    lastAddedGesture = gesture
                    resetAutoAddState()

                    // Subtle feedback (no toast for fluency)
                }
            }
        } else {
            // New gesture detected
            autoAddCurrentGesture = gesture
            autoAddGestureStart = currentTime
        }
    }

    private fun resetAutoAddState() {
        autoAddCurrentGesture = ""
        autoAddGestureStart = 0L
    }

    private fun addLetterToSentence(letter: String) {
        //there is a space hand sign
        if (letter == "space") {
            sentence += " "
        } else {
            // Clean the letter of any unwanted characters and convert to lowercase
            val cleanLetter = letter.lowercase().trim()
            sentence += cleanLetter
        }

        updateSentenceDisplay()

        // Visual feedback
        runOnUiThread {
            // Brief highlight effect
            tvCurrentLetter.setBackgroundColor(Color.parseColor("#4CAF50"))
            tvCurrentLetter.postDelayed({
                updateDetectionDisplay() // Restore original color
            }, 200)
        }
    }

    private fun updateDetectionDisplay() {
        if (currentLetter.isEmpty()) {
            tvCurrentLetter.text = "Detected: -"
            tvConfidence.text = "Confidence: -"
            tvCurrentLetter.setBackgroundColor(Color.parseColor("#F0F0F0"))
            return
        }

        val autoAddStatus = if (isAutoAddEnabled) {
            val currentTime = System.currentTimeMillis()
            if (autoAddCurrentGesture == currentLetter && autoAddGestureStart > 0L) {
                val holdTime = currentTime - autoAddGestureStart
                val progress = (holdTime.toFloat() / autoAddHoldTime * 100).toInt().coerceAtMost(100)
                when {
                    progress >= 100 -> " ✓"
                    progress >= 50 -> " ●●○"
                    progress >= 25 -> " ●○○"
                    else -> " ○○○"
                }
            } else if (currentLetter == lastAddedGesture &&
                (currentTime - lastAutoAddTime) < preventDuplicateTimeMs) {
                " (wait)"
            } else " (auto)"
        } else ""

        tvCurrentLetter.text = "Detected: $currentLetter$autoAddStatus"
        tvConfidence.text = "Confidence: ${(currentConfidence * 100).toInt()}%"

        // Change color based on confidence
        val color = when {
            currentConfidence > 0.8f -> "#4CAF50"  // Green - High confidence
            currentConfidence > 0.6f -> "#FF9800"  // Orange - Medium confidence
            else -> "#F44336"  // Red - Low confidence
        }
        tvCurrentLetter.setBackgroundColor(Color.parseColor(color))

        // Enable/disable add button based on confidence and auto-add mode
        if (!isAutoAddEnabled) {
            btnAddLetter.isEnabled = currentConfidence > 0.7f
            btnAddLetter.alpha = if (currentConfidence > 0.7f) 1.0f else 0.5f
        }
    }

    private fun updateSentenceDisplay() {
        tvSentence.text = "Sentence: $sentence"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handLandmarkerHelper.clearHandLandmarker()
        gestureClassifier.close()
    }
}