package com.example.signtranslator.utils

import android.content.Context
import com.example.signtranslator.models.SignResult
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Manages TensorFlow Lite model for sign language classification.
 * Loads model, labels, and scaler parameters from assets and provides
 * sign classification from hand landmark coordinates.
 */
class SignClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var signClasses: Array<String> = arrayOf()
    private var scalerParams: ScalerParams? = null

    /**
     * Data class for feature scaling parameters
     */
    data class ScalerParams(
        val mean: FloatArray?,
        val scale: FloatArray?,
        val scalerType: String
    )

    companion object {
        private const val MODEL_FILE = "gesture_model.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val SCALER_FILE = "scaler_params.json"
        private const val NUM_LANDMARKS = 21
        private const val NUM_COORDINATES = 2
        private const val INPUT_SIZE = NUM_LANDMARKS * NUM_COORDINATES
    }

    init {
        loadModel()
        loadLabels()
        loadScalerParams()
    }

    /**
     * Classify hand landmarks into a sign letter
     * @param landmarks List of 21 landmarks, each with [x, y, z] coordinates
     * @return SignResult with predicted letter and confidence, or null if classification fails
     */
    fun classifySign(landmarks: List<List<Float>>): SignResult? {
        interpreter ?: return null

        if (landmarks.size != NUM_LANDMARKS || signClasses.isEmpty()) return null

        return try {
            val features = extractFeatures(landmarks)
            val scaledFeatures = applyScaling(features)
            val inputBuffer = prepareInputBuffer(scaledFeatures)
            val outputArray = Array(1) { FloatArray(signClasses.size) }

            interpreter?.run(inputBuffer, outputArray)

            val predictions = outputArray[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: return null
            val confidence = predictions[maxIndex]
            val predictedSign = signClasses[maxIndex]

            SignResult(predictedSign, confidence)

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if the classifier is ready for use
     */
    fun isReady(): Boolean = interpreter != null && signClasses.isNotEmpty()

    /**
     * Clean up resources
     */
    fun cleanup() {
        interpreter?.close()
        interpreter = null
    }

    /**
     * Load TensorFlow Lite model from assets
     */
    private fun loadModel() {
        try {
            val modelFile = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // Handle error silently - isReady() will return false
        }
    }

    /**
     * Load sign class labels from assets
     */
    private fun loadLabels() {
        try {
            val labelsText = context.assets.open(LABELS_FILE).bufferedReader().use { it.readText() }
            signClasses = labelsText.trim().split('\n').toTypedArray()
        } catch (e: Exception) {
            // Handle error silently - isReady() will return false
        }
    }

    /**
     * Load feature scaling parameters from assets
     */
    private fun loadScalerParams() {
        try {
            val scalerText = context.assets.open(SCALER_FILE).bufferedReader().use { it.readText() }
            val json = JSONObject(scalerText)

            val meanArray = json.optJSONArray("mean")
            val scaleArray = json.optJSONArray("scale")
            val scalerType = json.optString("scaler_type", "StandardScaler")

            val mean = meanArray?.let { FloatArray(it.length()) { i -> it.getDouble(i).toFloat() } }
            val scale = scaleArray?.let { FloatArray(it.length()) { i -> it.getDouble(i).toFloat() } }

            scalerParams = ScalerParams(mean, scale, scalerType)
        } catch (e: Exception) {
            // Scaler parameters are optional - continue without them
        }
    }

    /**
     * Extract x,y coordinates from landmark data for model input
     */
    private fun extractFeatures(landmarks: List<List<Float>>): FloatArray {
        val features = FloatArray(INPUT_SIZE)
        var index = 0
        for (landmark in landmarks) {
            features[index++] = landmark[0] // x coordinate
            features[index++] = landmark[1] // y coordinate
            // Note: z coordinate is ignored for 2D model
        }
        return features
    }

    /**
     * Apply feature scaling using loaded scaler parameters
     */
    private fun applyScaling(features: FloatArray): FloatArray {
        scalerParams?.let { params ->
            if (params.scalerType == "StandardScaler" && params.mean != null && params.scale != null) {
                return FloatArray(features.size) { i ->
                    (features[i] - params.mean[i]) / params.scale[i]
                }
            }
        }
        return features // Return unscaled if no scaler parameters
    }

    /**
     * Prepare feature array as ByteBuffer for model input
     */
    private fun prepareInputBuffer(features: FloatArray): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (feature in features) {
            inputBuffer.putFloat(feature)
        }
        return inputBuffer
    }
}