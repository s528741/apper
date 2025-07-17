package com.apper.android.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class AIModelManagerTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var aiModelManager: AIModelManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        aiModelManager = AIModelManager(mockContext)
    }

    @Test
    fun `initializeModels should return true when models load successfully`() = runBlocking {
        // Test initialization
        val result = aiModelManager.initializeModels()
        
        // With mock models, this should succeed
        assertTrue("Model initialization should succeed", result)
    }

    @Test
    fun `analyzeText should return valid result for harmful content`() = runBlocking {
        // Initialize models first
        aiModelManager.initializeModels()
        
        val harmfulText = "This is a scam! Send money now for guaranteed returns!"
        val result = aiModelManager.analyzeText(harmfulText)
        
        assertNotNull("Result should not be null", result)
        assertTrue("Processing time should be positive", result.processingTime > 0)
        assertNotNull("Label should not be null", result.label)
        assertNotNull("Explanation should not be null", result.explanation)
    }

    @Test
    fun `analyzeText should return safe result for normal content`() = runBlocking {
        aiModelManager.initializeModels()
        
        val normalText = "This is a normal message about weather and news."
        val result = aiModelManager.analyzeText(normalText)
        
        assertNotNull("Result should not be null", result)
        assertEquals("Normal text should be classified as safe", "safe", result.label)
        assertTrue("Confidence should be reasonable", result.confidence >= 0.0f)
    }

    @Test
    fun `analyzeImage should handle mock bitmap correctly`() = runBlocking {
        aiModelManager.initializeModels()
        
        // Create a simple test bitmap
        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.RGB_565)
        val result = aiModelManager.analyzeImage(testBitmap, AIModelManager.MODEL_DEEPFAKE_DETECTION)
        
        assertNotNull("Result should not be null", result)
        assertTrue("Processing time should be positive", result.processingTime >= 0)
        assertNotNull("Label should not be null", result.label)
        assertTrue("Confidence should be between 0 and 1", 
                  result.confidence >= 0.0f && result.confidence <= 1.0f)
    }

    @Test
    fun `isModelLoaded should return correct status`() = runBlocking {
        // Before initialization
        assertFalse("Model should not be loaded initially", 
                   aiModelManager.isModelLoaded(AIModelManager.MODEL_DEEPFAKE_DETECTION))
        
        // After initialization
        aiModelManager.initializeModels()
        assertTrue("Model should be loaded after initialization", 
                  aiModelManager.isModelLoaded(AIModelManager.MODEL_DEEPFAKE_DETECTION))
    }

    @Test
    fun `getModelStats should return valid metadata`() = runBlocking {
        aiModelManager.initializeModels()
        
        val stats = aiModelManager.getModelStats()
        
        assertNotNull("Stats should not be null", stats)
        assertTrue("Stats should contain models", stats.isNotEmpty())
        assertTrue("Should contain deepfake model", 
                  stats.containsKey(AIModelManager.MODEL_DEEPFAKE_DETECTION))
    }

    @Test
    fun `cleanup should handle resources properly`() {
        // Should not throw any exceptions
        assertDoesNotThrow("Cleanup should handle resources safely") {
            aiModelManager.cleanup()
        }
    }

    @Test
    fun `analyzeImage with invalid model should return error`() = runBlocking {
        aiModelManager.initializeModels()
        
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        val result = aiModelManager.analyzeImage(testBitmap, "invalid_model")
        
        assertNotNull("Result should not be null", result)
        assertEquals("Should return error label", "error", result.label)
        assertEquals("Confidence should be 0 for errors", 0.0f, result.confidence)
    }

    private fun assertDoesNotThrow(message: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            fail("$message but threw: ${e.message}")
        }
    }
} 