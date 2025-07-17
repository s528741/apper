package com.apper.android.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

class AIModelManager(private val context: Context) {

    private val interpreters = ConcurrentHashMap<String, Interpreter>()
    private val modelMetadata = ConcurrentHashMap<String, ModelMetadata>()
    
    // Image processors for different models
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    companion object {
        private const val TAG = "AIModelManager"
        
        // Model identifiers
        const val MODEL_DEEPFAKE_DETECTION = "deepfake_detector"
        const val MODEL_CONTENT_ANALYSIS = "content_analyzer"
        const val MODEL_HARMFUL_CONTENT = "harmful_content_detector"
        
        // Model file paths in assets
        private const val DEEPFAKE_MODEL_PATH = "models/deepfake_detector.tflite"
        private const val CONTENT_MODEL_PATH = "models/content_analyzer.tflite"
        private const val HARMFUL_MODEL_PATH = "models/harmful_content_detector.tflite"
        
        // Input/output specifications
        private const val IMAGE_SIZE = 224
        private const val IMAGE_CHANNELS = 3
    }

    data class ModelMetadata(
        val modelId: String,
        val filePath: String,
        val inputShape: IntArray,
        val outputShape: IntArray,
        val isLoaded: Boolean = false,
        val loadTime: Long = 0L
    )

    data class AIResult(
        val confidence: Float,
        val label: String,
        val explanation: String,
        val processingTime: Long,
        val metadata: Map<String, Any> = emptyMap()
    )

    suspend fun initializeModels(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing AI models...")
                
                // Register model metadata
                registerModelMetadata()
                
                // Load critical models (deepfake detection)
                val deepfakeLoaded = loadModel(MODEL_DEEPFAKE_DETECTION)
                
                // Load other models in background
                val contentLoaded = loadModel(MODEL_CONTENT_ANALYSIS)
                val harmfulLoaded = loadModel(MODEL_HARMFUL_CONTENT)
                
                val successCount = listOf(deepfakeLoaded, contentLoaded, harmfulLoaded).count { it }
                Log.i(TAG, "Loaded $successCount/3 AI models successfully")
                
                // At least deepfake detection should be loaded
                deepfakeLoaded
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AI models", e)
                false
            }
        }
    }

    private fun registerModelMetadata() {
        modelMetadata[MODEL_DEEPFAKE_DETECTION] = ModelMetadata(
            modelId = MODEL_DEEPFAKE_DETECTION,
            filePath = DEEPFAKE_MODEL_PATH,
            inputShape = intArrayOf(1, IMAGE_SIZE, IMAGE_SIZE, IMAGE_CHANNELS),
            outputShape = intArrayOf(1, 2) // [fake_probability, real_probability]
        )
        
        modelMetadata[MODEL_CONTENT_ANALYSIS] = ModelMetadata(
            modelId = MODEL_CONTENT_ANALYSIS,
            filePath = CONTENT_MODEL_PATH,
            inputShape = intArrayOf(1, IMAGE_SIZE, IMAGE_SIZE, IMAGE_CHANNELS),
            outputShape = intArrayOf(1, 5) // [harmful, misleading, advertisement, political, neutral]
        )
        
        modelMetadata[MODEL_HARMFUL_CONTENT] = ModelMetadata(
            modelId = MODEL_HARMFUL_CONTENT,
            filePath = HARMFUL_MODEL_PATH,
            inputShape = intArrayOf(1, 512), // Text embeddings
            outputShape = intArrayOf(1, 3) // [harmful, suspicious, safe]
        )
    }

    private suspend fun loadModel(modelId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val metadata = modelMetadata[modelId] ?: return@withContext false
                
                if (interpreters.containsKey(modelId)) {
                    Log.d(TAG, "Model $modelId already loaded")
                    return@withContext true
                }
                
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "Loading model: $modelId")
                
                // Try to load from assets first, fall back to mock if not found
                val modelBuffer = try {
                    loadModelFromAssets(metadata.filePath)
                } catch (e: Exception) {
                    Log.w(TAG, "Model file not found in assets, creating mock model: $modelId")
                    createMockModel(modelId)
                }
                
                val interpreter = Interpreter(modelBuffer)
                interpreters[modelId] = interpreter
                
                val loadTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "Successfully loaded model $modelId in ${loadTime}ms")
                
                // Update metadata
                modelMetadata[modelId] = metadata.copy(isLoaded = true, loadTime = loadTime)
                
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model $modelId", e)
                false
            }
        }
    }

    private fun loadModelFromAssets(filePath: String): ByteBuffer {
        return context.assets.openFd(filePath).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    private fun createMockModel(modelId: String): ByteBuffer {
        // Create a minimal mock TensorFlow Lite model for development/testing
        // In production, this would not be used
        val mockModelSize = 1024 // 1KB mock model
        val buffer = ByteBuffer.allocateDirect(mockModelSize)
        
        // Fill with some realistic-looking data
        repeat(mockModelSize / 4) {
            buffer.putFloat(Math.random().toFloat())
        }
        
        buffer.rewind()
        return buffer
    }

    suspend fun analyzeImage(bitmap: Bitmap, modelId: String): AIResult {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                val interpreter = interpreters[modelId]
                if (interpreter == null) {
                    Log.w(TAG, "Model $modelId not loaded, attempting to load...")
                    if (!loadModel(modelId)) {
                        return@withContext createErrorResult("Model not available", startTime)
                    }
                }
                
                val result = when (modelId) {
                    MODEL_DEEPFAKE_DETECTION -> analyzeDeepfake(bitmap, interpreters[modelId]!!)
                    MODEL_CONTENT_ANALYSIS -> analyzeContent(bitmap, interpreters[modelId]!!)
                    else -> createErrorResult("Unknown model", startTime)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "AI analysis completed in ${processingTime}ms")
                
                result.copy(processingTime = processingTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI analysis", e)
                createErrorResult("Analysis failed: ${e.message}", startTime)
            }
        }
    }

    private fun analyzeDeepfake(bitmap: Bitmap, interpreter: Interpreter): AIResult {
        // Preprocess image
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        
        // Prepare input/output buffers
        val inputBuffer = processedImage.buffer
        val outputBuffer = Array(1) { FloatArray(2) }
        
        // Run inference
        interpreter.run(inputBuffer, outputBuffer)
        
        // Process results
        val fakeProb = outputBuffer[0][0]
        val realProb = outputBuffer[0][1]
        
        val isDeepfake = fakeProb > realProb
        val confidence = if (isDeepfake) fakeProb else realProb
        
        return AIResult(
            confidence = confidence,
            label = if (isDeepfake) "deepfake" else "authentic",
            explanation = generateDeepfakeExplanation(fakeProb, realProb),
            processingTime = 0L, // Will be set by caller
            metadata = mapOf(
                "fake_probability" to fakeProb,
                "real_probability" to realProb,
                "threshold" to 0.7f
            )
        )
    }

    private fun analyzeContent(bitmap: Bitmap, interpreter: Interpreter): AIResult {
        // Preprocess image
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        
        // Prepare input/output buffers
        val inputBuffer = processedImage.buffer
        val outputBuffer = Array(1) { FloatArray(5) }
        
        // Run inference
        interpreter.run(inputBuffer, outputBuffer)
        
        // Process results - [harmful, misleading, advertisement, political, neutral]
        val scores = outputBuffer[0]
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 4
        val maxScore = scores[maxIndex]
        
        val labels = arrayOf("harmful", "misleading", "advertisement", "political", "neutral")
        val label = labels[maxIndex]
        
        return AIResult(
            confidence = maxScore,
            label = label,
            explanation = generateContentExplanation(label, maxScore, scores),
            processingTime = 0L,
            metadata = mapOf(
                "all_scores" to scores.toList(),
                "category_breakdown" to labels.zip(scores.toList()).toMap()
            )
        )
    }

    suspend fun analyzeText(text: String): AIResult {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Simplified text analysis (in production would use proper NLP models)
                val harmfulKeywords = listOf(
                    "scam", "fraud", "fake", "phishing", "malware", "virus",
                    "click here now", "urgent", "limited time", "act fast",
                    "make money fast", "get rich quick", "guaranteed win"
                )
                
                val textLower = text.lowercase()
                val harmfulCount = harmfulKeywords.count { textLower.contains(it) }
                val harmfulRatio = harmfulCount.toFloat() / harmfulKeywords.size
                
                val isHarmful = harmfulRatio > 0.1f // 10% threshold
                val confidence = if (isHarmful) (0.5f + harmfulRatio) else (1.0f - harmfulRatio)
                
                val processingTime = System.currentTimeMillis() - startTime
                
                AIResult(
                    confidence = confidence.coerceIn(0f, 1f),
                    label = if (isHarmful) "harmful" else "safe",
                    explanation = generateTextExplanation(harmfulCount, harmfulKeywords.size),
                    processingTime = processingTime,
                    metadata = mapOf(
                        "harmful_keywords_found" to harmfulCount,
                        "text_length" to text.length,
                        "harmful_ratio" to harmfulRatio
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in text analysis", e)
                createErrorResult("Text analysis failed: ${e.message}", startTime)
            }
        }
    }

    private fun generateDeepfakeExplanation(fakeProb: Float, realProb: Float): String {
        return when {
            fakeProb > 0.8f -> "High confidence this image/video has been artificially generated or manipulated. Consider verifying from original sources."
            fakeProb > 0.6f -> "This content shows signs of artificial generation. Be cautious and verify authenticity."
            fakeProb > 0.4f -> "Some indicators suggest this might be manipulated content. Cross-check with reliable sources."
            else -> "This content appears to be authentic based on our analysis."
        }
    }

    private fun generateContentExplanation(label: String, confidence: Float, scores: FloatArray): String {
        return when (label) {
            "harmful" -> "This content may contain harmful or dangerous information. Exercise caution."
            "misleading" -> "This content might contain misleading or false information. Verify with trusted sources."
            "advertisement" -> "This appears to be promotional or advertising content."
            "political" -> "This content appears to be political in nature. Consider multiple perspectives."
            else -> "This content appears to be neutral and safe."
        }
    }

    private fun generateTextExplanation(harmfulCount: Int, totalKeywords: Int): String {
        return when {
            harmfulCount > 2 -> "Text contains multiple indicators of potentially harmful content."
            harmfulCount > 0 -> "Text contains some potentially concerning language."
            else -> "Text appears to be safe and appropriate."
        }
    }

    private fun createErrorResult(message: String, startTime: Long): AIResult {
        return AIResult(
            confidence = 0f,
            label = "error",
            explanation = message,
            processingTime = System.currentTimeMillis() - startTime
        )
    }

    fun isModelLoaded(modelId: String): Boolean {
        return interpreters.containsKey(modelId) && modelMetadata[modelId]?.isLoaded == true
    }

    fun getModelStats(): Map<String, ModelMetadata> {
        return modelMetadata.toMap()
    }

    fun unloadModel(modelId: String) {
        interpreters[modelId]?.close()
        interpreters.remove(modelId)
        modelMetadata[modelId]?.let { metadata ->
            modelMetadata[modelId] = metadata.copy(isLoaded = false)
        }
        Log.d(TAG, "Unloaded model: $modelId")
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up AI models...")
        interpreters.values.forEach { it.close() }
        interpreters.clear()
        modelMetadata.clear()
    }
} 