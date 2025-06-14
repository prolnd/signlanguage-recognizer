package com.example.signtranslator.utils

import android.content.Context
import android.util.Log
import com.example.signtranslator.models.SignResult
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class SignClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var signClasses: Array<String> = arrayOf()
    private var scalerParams: ScalerParams? = null

    data class ScalerParams(
        val mean: FloatArray?,
        val scale: FloatArray?,
        val scalerType: String
    )

    companion object {
        private const val TAG = "SignClassifier"
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

    fun classifySign(landmarks: List<List<Float>>): SignResult? {
        interpreter ?: return null

        if (landmarks.size != NUM_LANDMARKS || signClasses.isEmpty()) return null

        try {
            val features = extractFeatures(landmarks)
            val scaledFeatures = applyScaling(features)
            val inputBuffer = prepareInputBuffer(scaledFeatures)
            val outputArray = Array(1) { FloatArray(signClasses.size) }

            interpreter?.run(inputBuffer, outputArray)

            val predictions = outputArray[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: return null
            val confidence = predictions[maxIndex]
            val predictedSign = signClasses[maxIndex]

            return SignResult(predictedSign, confidence)

        } catch (e: Exception) {
            Log.e(TAG, "Error during sign classification", e)
            return null
        }
    }

    fun isReady(): Boolean = interpreter != null && signClasses.isNotEmpty()

    fun cleanup() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModel() {
        try {
            val modelFile = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            interpreter = Interpreter(modelBuffer)
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    private fun loadLabels() {
        try {
            val labelsText = context.assets.open(LABELS_FILE).bufferedReader().use { it.readText() }
            signClasses = labelsText.trim().split('\n').toTypedArray()
            Log.d(TAG, "Loaded ${signClasses.size} sign classes")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading labels", e)
        }
    }

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
            Log.d(TAG, "No scaler parameters found")
        }
    }

    private fun extractFeatures(landmarks: List<List<Float>>): FloatArray {
        val features = FloatArray(INPUT_SIZE)
        var index = 0
        for (landmark in landmarks) {
            features[index++] = landmark[0] // x
            features[index++] = landmark[1] // y
        }
        return features
    }

    private fun applyScaling(features: FloatArray): FloatArray {
        scalerParams?.let { params ->
            if (params.scalerType == "StandardScaler" && params.mean != null && params.scale != null) {
                return FloatArray(features.size) { i ->
                    (features[i] - params.mean[i]) / params.scale[i]
                }
            }
        }
        return features
    }

    private fun prepareInputBuffer(features: FloatArray): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (feature in features) {
            inputBuffer.putFloat(feature)
        }
        return inputBuffer
    }
}