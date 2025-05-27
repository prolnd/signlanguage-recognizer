package com.example.signtranslator


import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class Recognizer(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gestureClasses: Array<String> = arrayOf()
    private var scalerParams: ScalerParams? = null

    data class ScalerParams(
        val mean: FloatArray?,
        val scale: FloatArray?,
        val scalerType: String
    )

    companion object {
        private const val TAG = "GestureClassifier"
        private const val MODEL_FILE = "gesture_model.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val SCALER_FILE = "scaler_params.json"
        private const val NUM_LANDMARKS = 21
        private const val NUM_COORDINATES = 2  // x, y coordinates
        private const val INPUT_SIZE = NUM_LANDMARKS * NUM_COORDINATES // 42
    }

    init {
        loadModel()
        loadLabels()
        loadScalerParams()
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
            Log.d(TAG, "TensorFlow Lite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow Lite model", e)
        }
    }

    private fun loadLabels() {
        try {
            val labelsText = context.assets.open(LABELS_FILE).bufferedReader().use { it.readText() }
            gestureClasses = labelsText.trim().split('\n').toTypedArray()
            Log.d(TAG, "Loaded ${gestureClasses.size} gesture classes: ${gestureClasses.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading labels", e)
            // Fallback to auto-detection
            interpreter?.let { interpreter ->
                val outputShape = interpreter.getOutputTensor(0).shape()
                val numClasses = outputShape[1]
                gestureClasses = Array(numClasses) { index ->
                    if (numClasses == 26) ('A' + index).toString() else index.toString()
                }
                Log.d(TAG, "Generated ${gestureClasses.size} default gesture classes")
            }
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
            Log.d(TAG, "Loaded scaler parameters: $scalerType")
        } catch (e: Exception) {
            Log.d(TAG, "No scaler parameters found, using raw landmarks")
        }
    }

    fun classifyGesture(landmarks: List<List<Float>>): Pair<String, Float>? {
        interpreter ?: return null

        if (landmarks.size != NUM_LANDMARKS) {
            Log.w(TAG, "Expected $NUM_LANDMARKS landmarks, got ${landmarks.size}")
            return null
        }

        if (gestureClasses.isEmpty()) {
            Log.w(TAG, "No gesture classes loaded")
            return null
        }

        try {
            // Extract features (x, y coordinates only, like your Python training)
            val features = FloatArray(INPUT_SIZE)
            var index = 0
            for (landmark in landmarks) {
                features[index++] = landmark[0] // x
                features[index++] = landmark[1] // y
            }

            // Apply scaling if scaler parameters are available
            val scaledFeatures = applyScaling(features)

            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (feature in scaledFeatures) {
                inputBuffer.putFloat(feature)
            }

            // Prepare output buffer
            val outputArray = Array(1) { FloatArray(gestureClasses.size) }

            // Run inference
            interpreter?.run(inputBuffer, outputArray)

            // Get prediction results
            val predictions = outputArray[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: return null
            val confidence = predictions[maxIndex]
            val predictedGesture = gestureClasses[maxIndex]

            Log.d(TAG, "Predicted: $predictedGesture with confidence: $confidence")
            return Pair(predictedGesture, confidence)

        } catch (e: Exception) {
            Log.e(TAG, "Error during gesture classification", e)
            return null
        }
    }

    private fun applyScaling(features: FloatArray): FloatArray {
        scalerParams?.let { params ->
            when (params.scalerType) {
                "StandardScaler" -> {
                    if (params.mean != null && params.scale != null) {
                        return FloatArray(features.size) { i ->
                            (features[i] - params.mean[i]) / params.scale[i]
                        }
                    } else {

                    }
                }
                "MinMaxScaler" -> {
                    // Implement MinMaxScaler if needed
                    Log.d(TAG, "MinMaxScaler not implemented, using raw features")
                }
                "RobustScaler" -> {
                    // Implement RobustScaler if needed
                    Log.d(TAG, "RobustScaler not implemented, using raw features")
                }

                else -> {}
            }
        }

        // Return raw features if no scaling
        return features
    }

    fun getGestureClasses(): Array<String> {
        return gestureClasses
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}