package com.apper.android.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class ContentAnalyzer(private val context: Context) {

    private val aiModelManager = AIModelManager(context)
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val analyzedContent = AtomicInteger(0)
    private val flaggedContent = AtomicInteger(0)
    
    companion object {
        private const val TAG = "ContentAnalyzer"
        private const val ANALYSIS_TIMEOUT_MS = 2000L // 2 seconds
    }

    data class ContentAnalysisResult(
        val contentType: ContentType,
        val riskLevel: RiskLevel,
        val confidence: Float,
        val explanation: String,
        val concerns: List<ContentConcern>,
        val contextualInfo: ContextualInfo,
        val recommendations: List<String>,
        val processingTime: Long
    )

    data class ContentConcern(
        val type: ConcernType,
        val severity: Severity,
        val description: String,
        val evidence: List<String>
    )

    data class ContextualInfo(
        val topicCategory: String,
        val factCheckSuggestions: List<String>,
        val relatedSources: List<String>,
        val educationalContext: String
    )

    enum class ContentType {
        TEXT, IMAGE, VIDEO, MIXED, UNKNOWN
    }

    enum class RiskLevel {
        SAFE, LOW_RISK, MEDIUM_RISK, HIGH_RISK, DANGEROUS
    }

    enum class ConcernType {
        MISINFORMATION, HARMFUL_CONTENT, MANIPULATION, BIAS, 
        PRIVACY_RISK, FINANCIAL_SCAM, HEALTH_MISINFORMATION,
        POLITICAL_MANIPULATION, DEEPFAKE, ADVERTISEMENT_DISGUISED
    }

    enum class Severity {
        INFO, WARNING, CRITICAL
    }

    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing Content Analyzer...")
                val initialized = aiModelManager.initializeModels()
                
                if (initialized) {
                    Log.i(TAG, "Content Analyzer initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize AI models for content analysis")
                }
                
                initialized
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing content analyzer", e)
                false
            }
        }
    }

    suspend fun analyzeContent(
        text: String? = null,
        image: Bitmap? = null,
        context: String? = null
    ): ContentAnalysisResult {
        return withContext(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            
            try {
                Log.d(TAG, "Analyzing content - Text: ${text != null}, Image: ${image != null}")
                
                val contentType = determineContentType(text, image)
                val analysisResults = mutableListOf<AIModelManager.AIResult>()
                
                // Analyze text if present
                if (!text.isNullOrBlank()) {
                    val textResult = withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
                        aiModelManager.analyzeText(text)
                    }
                    textResult?.let { analysisResults.add(it) }
                }
                
                // Analyze image if present
                if (image != null) {
                    val imageResult = withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
                        aiModelManager.analyzeImage(image, AIModelManager.MODEL_CONTENT_ANALYSIS)
                    }
                    imageResult?.let { analysisResults.add(it) }
                }
                
                // Process combined results
                val finalResult = processAnalysisResults(
                    contentType, 
                    analysisResults, 
                    text, 
                    context, 
                    startTime
                )
                
                // Update statistics
                analyzedContent.incrementAndGet()
                if (finalResult.riskLevel != RiskLevel.SAFE) {
                    flaggedContent.incrementAndGet()
                }
                
                finalResult
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing content", e)
                createErrorResult(startTime, e.message ?: "Analysis failed")
            }
        }
    }

    private fun determineContentType(text: String?, image: Bitmap?): ContentType {
        return when {
            text != null && image != null -> ContentType.MIXED
            text != null -> ContentType.TEXT
            image != null -> ContentType.IMAGE
            else -> ContentType.UNKNOWN
        }
    }

    private fun processAnalysisResults(
        contentType: ContentType,
        results: List<AIModelManager.AIResult>,
        originalText: String?,
        context: String?,
        startTime: Long
    ): ContentAnalysisResult {
        
        if (results.isEmpty()) {
            return createNoAnalysisResult(contentType, startTime)
        }
        
        // Combine confidence scores
        val avgConfidence = results.map { it.confidence }.average().toFloat()
        
        // Determine overall risk level
        val riskLevel = determineOverallRiskLevel(results, originalText)
        
        // Generate concerns
        val concerns = generateConcerns(results, originalText, contentType)
        
        // Generate contextual information
        val contextualInfo = generateContextualInfo(originalText, context, results)
        
        // Generate explanation
        val explanation = generateCombinedExplanation(results, riskLevel, contentType)
        
        // Generate recommendations
        val recommendations = generateRecommendations(riskLevel, concerns, contentType)
        
        val processingTime = System.currentTimeMillis() - startTime
        
        return ContentAnalysisResult(
            contentType = contentType,
            riskLevel = riskLevel,
            confidence = avgConfidence,
            explanation = explanation,
            concerns = concerns,
            contextualInfo = contextualInfo,
            recommendations = recommendations,
            processingTime = processingTime
        )
    }

    private fun determineOverallRiskLevel(results: List<AIModelManager.AIResult>, text: String?): RiskLevel {
        val harmfulResults = results.filter { it.label in listOf("harmful", "misleading", "deepfake") }
        
        return when {
            harmfulResults.isEmpty() -> RiskLevel.SAFE
            harmfulResults.any { it.confidence > 0.8f } -> RiskLevel.HIGH_RISK
            harmfulResults.any { it.confidence > 0.6f } -> RiskLevel.MEDIUM_RISK
            harmfulResults.any { it.confidence > 0.4f } -> RiskLevel.LOW_RISK
            else -> {
                // Additional text-based analysis
                text?.let { analyzeTextPatterns(it) } ?: RiskLevel.SAFE
            }
        }
    }

    private fun analyzeTextPatterns(text: String): RiskLevel {
        val textLower = text.lowercase()
        
        val highRiskPatterns = listOf(
            "click here now", "urgent action required", "limited time offer",
            "guaranteed money", "get rich quick", "miracle cure",
            "government doesn't want you to know", "doctors hate this trick"
        )
        
        val mediumRiskPatterns = listOf(
            "breaking:", "exclusive:", "secret", "leaked",
            "you won't believe", "shocking truth", "must see"
        )
        
        return when {
            highRiskPatterns.any { textLower.contains(it) } -> RiskLevel.HIGH_RISK
            mediumRiskPatterns.any { textLower.contains(it) } -> RiskLevel.MEDIUM_RISK
            else -> RiskLevel.SAFE
        }
    }

    private fun generateConcerns(
        results: List<AIModelManager.AIResult>,
        text: String?,
        contentType: ContentType
    ): List<ContentConcern> {
        val concerns = mutableListOf<ContentConcern>()
        
        // Process AI results
        results.forEach { result ->
            when (result.label) {
                "harmful" -> {
                    concerns.add(ContentConcern(
                        type = ConcernType.HARMFUL_CONTENT,
                        severity = if (result.confidence > 0.7f) Severity.CRITICAL else Severity.WARNING,
                        description = "Content may contain harmful or dangerous information",
                        evidence = listOf("AI analysis detected harmful patterns")
                    ))
                }
                "misleading" -> {
                    concerns.add(ContentConcern(
                        type = ConcernType.MISINFORMATION,
                        severity = if (result.confidence > 0.7f) Severity.CRITICAL else Severity.WARNING,
                        description = "Content appears to contain misleading information",
                        evidence = listOf("Potential misinformation detected by AI analysis")
                    ))
                }
                "deepfake" -> {
                    concerns.add(ContentConcern(
                        type = ConcernType.DEEPFAKE,
                        severity = Severity.CRITICAL,
                        description = "Visual content may be artificially generated or manipulated",
                        evidence = listOf("Deepfake detection algorithm flagged this content")
                    ))
                }
                "advertisement" -> {
                    concerns.add(ContentConcern(
                        type = ConcernType.ADVERTISEMENT_DISGUISED,
                        severity = Severity.INFO,
                        description = "Content appears to be promotional or advertising material",
                        evidence = listOf("Advertisement detection patterns found")
                    ))
                }
            }
        }
        
        // Additional text-based analysis
        text?.let { concerns.addAll(analyzeTextConcerns(it)) }
        
        return concerns
    }

    private fun analyzeTextConcerns(text: String): List<ContentConcern> {
        val concerns = mutableListOf<ContentConcern>()
        val textLower = text.lowercase()
        
        // Financial scam indicators
        val financialScamKeywords = listOf("investment opportunity", "guaranteed returns", "crypto mining", "bitcoin", "forex trading")
        if (financialScamKeywords.any { textLower.contains(it) }) {
            concerns.add(ContentConcern(
                type = ConcernType.FINANCIAL_SCAM,
                severity = Severity.WARNING,
                description = "Content may contain financial scam indicators",
                evidence = financialScamKeywords.filter { textLower.contains(it) }
            ))
        }
        
        // Health misinformation
        val healthMisInfoKeywords = listOf("miracle cure", "doctors don't want", "natural remedy", "instant weight loss")
        if (healthMisInfoKeywords.any { textLower.contains(it) }) {
            concerns.add(ContentConcern(
                type = ConcernType.HEALTH_MISINFORMATION,
                severity = Severity.CRITICAL,
                description = "Content may contain health misinformation",
                evidence = healthMisInfoKeywords.filter { textLower.contains(it) }
            ))
        }
        
        // Privacy risks
        val privacyRiskKeywords = listOf("share your location", "upload your contacts", "access your photos")
        if (privacyRiskKeywords.any { textLower.contains(it) }) {
            concerns.add(ContentConcern(
                type = ConcernType.PRIVACY_RISK,
                severity = Severity.WARNING,
                description = "Content may request access to personal information",
                evidence = privacyRiskKeywords.filter { textLower.contains(it) }
            ))
        }
        
        return concerns
    }

    private fun generateContextualInfo(text: String?, context: String?, results: List<AIModelManager.AIResult>): ContextualInfo {
        val topicCategory = determineTopicCategory(text, results)
        val factCheckSuggestions = generateFactCheckSuggestions(topicCategory, text)
        val relatedSources = generateRelatedSources(topicCategory)
        val educationalContext = generateEducationalContext(topicCategory, text)
        
        return ContextualInfo(
            topicCategory = topicCategory,
            factCheckSuggestions = factCheckSuggestions,
            relatedSources = relatedSources,
            educationalContext = educationalContext
        )
    }

    private fun determineTopicCategory(text: String?, results: List<AIModelManager.AIResult>): String {
        text?.let { 
            val textLower = it.lowercase()
            return when {
                textLower.contains("health") || textLower.contains("medical") -> "Health & Medicine"
                textLower.contains("politics") || textLower.contains("election") -> "Politics & Government"
                textLower.contains("science") || textLower.contains("research") -> "Science & Technology"
                textLower.contains("finance") || textLower.contains("investment") -> "Finance & Economics"
                textLower.contains("climate") || textLower.contains("environment") -> "Environment & Climate"
                else -> "General"
            }
        }
        
        return results.firstOrNull()?.let { result ->
            when (result.label) {
                "political" -> "Politics & Government"
                "harmful" -> "Safety & Security"
                else -> "General"
            }
        } ?: "General"
    }

    private fun generateFactCheckSuggestions(category: String, text: String?): List<String> {
        val baseSuggestions = listOf(
            "Cross-reference with multiple reliable sources",
            "Check publication date and author credentials",
            "Look for peer review or official verification"
        )
        
        val categorySuggestions = when (category) {
            "Health & Medicine" -> listOf(
                "Consult medical professionals",
                "Check with WHO or CDC for health information",
                "Verify with peer-reviewed medical journals"
            )
            "Politics & Government" -> listOf(
                "Check official government sources",
                "Verify with multiple news outlets",
                "Look for primary source documents"
            )
            "Science & Technology" -> listOf(
                "Check scientific journals and publications",
                "Verify with academic institutions",
                "Look for research methodology and peer review"
            )
            else -> emptyList()
        }
        
        return baseSuggestions + categorySuggestions
    }

    private fun generateRelatedSources(category: String): List<String> {
        return when (category) {
            "Health & Medicine" -> listOf("WHO.int", "CDC.gov", "PubMed", "Mayo Clinic")
            "Politics & Government" -> listOf("Government official websites", "AP News", "Reuters", "BBC")
            "Science & Technology" -> listOf("Nature.com", "Science.org", "IEEE", "ArXiv")
            "Finance & Economics" -> listOf("SEC.gov", "Federal Reserve", "Bloomberg", "Financial Times")
            "Environment & Climate" -> listOf("NOAA", "NASA Climate", "IPCC", "Nature Climate Change")
            else -> listOf("Snopes", "FactCheck.org", "PolitiFact", "Reuters Fact Check")
        }
    }

    private fun generateEducationalContext(category: String, text: String?): String {
        return when (category) {
            "Health & Medicine" -> "Health information should always be verified with medical professionals. Be cautious of claims about miracle cures or treatments that seem too good to be true."
            "Politics & Government" -> "Political information can be heavily biased. Always check multiple sources across the political spectrum and look for primary sources."
            "Science & Technology" -> "Scientific claims should be backed by peer-reviewed research. Be wary of studies with small sample sizes or those not replicated."
            "Finance & Economics" -> "Investment advice should come from licensed professionals. Be extremely cautious of get-rich-quick schemes or guaranteed returns."
            else -> "Always verify important information with multiple reliable sources before making decisions or sharing content."
        }
    }

    private fun generateCombinedExplanation(
        results: List<AIModelManager.AIResult>,
        riskLevel: RiskLevel,
        contentType: ContentType
    ): String {
        val baseExplanation = when (riskLevel) {
            RiskLevel.SAFE -> "Content appears to be safe and trustworthy."
            RiskLevel.LOW_RISK -> "Content has some minor concerns but is generally acceptable."
            RiskLevel.MEDIUM_RISK -> "Content has moderate concerns and should be verified before sharing."
            RiskLevel.HIGH_RISK -> "Content has significant concerns and may be harmful or misleading."
            RiskLevel.DANGEROUS -> "Content is potentially dangerous and should not be shared."
        }
        
        val aiInsights = results.map { it.explanation }.distinct().joinToString(" ")
        
        return if (aiInsights.isNotBlank()) {
            "$baseExplanation $aiInsights"
        } else {
            baseExplanation
        }
    }

    private fun generateRecommendations(
        riskLevel: RiskLevel,
        concerns: List<ContentConcern>,
        contentType: ContentType
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (riskLevel) {
            RiskLevel.SAFE -> {
                recommendations.add("Content appears safe, but always verify important information")
            }
            RiskLevel.LOW_RISK -> {
                recommendations.add("Exercise normal caution when sharing")
                recommendations.add("Consider verifying key claims")
            }
            RiskLevel.MEDIUM_RISK -> {
                recommendations.add("Verify information before sharing")
                recommendations.add("Check with reliable sources")
                recommendations.add("Be cautious of making decisions based on this content")
            }
            RiskLevel.HIGH_RISK, RiskLevel.DANGEROUS -> {
                recommendations.add("Do not share this content")
                recommendations.add("Verify with multiple reliable sources")
                recommendations.add("Report if content violates platform policies")
                recommendations.add("Consult experts before making any decisions")
            }
        }
        
        // Add specific recommendations based on concerns
        concerns.forEach { concern ->
            when (concern.type) {
                ConcernType.HEALTH_MISINFORMATION -> {
                    recommendations.add("Consult healthcare professionals for medical advice")
                }
                ConcernType.FINANCIAL_SCAM -> {
                    recommendations.add("Never invest money based on social media advice")
                }
                ConcernType.DEEPFAKE -> {
                    recommendations.add("Verify authenticity through reverse image search")
                }
                ConcernType.PRIVACY_RISK -> {
                    recommendations.add("Protect your personal information")
                }
                else -> {}
            }
        }
        
        return recommendations.distinct()
    }

    private fun createNoAnalysisResult(contentType: ContentType, startTime: Long): ContentAnalysisResult {
        return ContentAnalysisResult(
            contentType = contentType,
            riskLevel = RiskLevel.SAFE,
            confidence = 0f,
            explanation = "No content to analyze",
            concerns = emptyList(),
            contextualInfo = ContextualInfo("General", emptyList(), emptyList(), ""),
            recommendations = listOf("Unable to analyze content"),
            processingTime = System.currentTimeMillis() - startTime
        )
    }

    private fun createErrorResult(startTime: Long, message: String): ContentAnalysisResult {
        return ContentAnalysisResult(
            contentType = ContentType.UNKNOWN,
            riskLevel = RiskLevel.MEDIUM_RISK,
            confidence = 0f,
            explanation = "Analysis failed: $message",
            concerns = listOf(
                ContentConcern(
                    type = ConcernType.HARMFUL_CONTENT,
                    severity = Severity.WARNING,
                    description = "Unable to analyze content for safety",
                    evidence = listOf("Analysis error occurred")
                )
            ),
            contextualInfo = ContextualInfo("General", emptyList(), emptyList(), ""),
            recommendations = listOf("Verify content manually", "Exercise caution"),
            processingTime = System.currentTimeMillis() - startTime
        )
    }

    fun getAnalysisStats(): AnalysisStats {
        return AnalysisStats(
            totalAnalyzed = analyzedContent.get(),
            contentFlagged = flaggedContent.get(),
            flaggedPercentage = if (analyzedContent.get() > 0) {
                (flaggedContent.get().toFloat() / analyzedContent.get() * 100)
            } else 0f
        )
    }

    data class AnalysisStats(
        val totalAnalyzed: Int,
        val contentFlagged: Int,
        val flaggedPercentage: Float
    )

    fun cleanup() {
        Log.d(TAG, "Cleaning up Content Analyzer...")
        analysisScope.cancel()
        aiModelManager.cleanup()
    }
} 