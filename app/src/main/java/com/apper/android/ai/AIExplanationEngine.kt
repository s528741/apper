package com.apper.android.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

class AIExplanationEngine(private val context: Context) {

    companion object {
        private const val TAG = "AIExplanationEngine"
    }

    data class Explanation(
        val title: String,
        val summary: String,
        val detailedExplanation: String,
        val keyPoints: List<String>,
        val userActions: List<String>,
        val educationalContent: EducationalContent,
        val confidenceLevel: Float
    )

    data class EducationalContent(
        val whatIsIt: String,
        val whyItMatters: String,
        val howToIdentify: List<String>,
        val preventionTips: List<String>,
        val relatedConcepts: List<String>
    )

    suspend fun explainDeepfakeDetection(result: DeepfakeDetectionService.DeepfakeAnalysisResult): Explanation {
        return withContext(Dispatchers.Default) {
            val confidence = result.confidence
            val isDeepfake = result.isDeepfake
            val riskLevel = result.detectionDetails.riskLevel

            val title = if (isDeepfake) "Deepfake Detected" else "Content Analysis Complete"
            
            val summary = when (riskLevel) {
                DeepfakeDetectionService.RiskLevel.CRITICAL -> 
                    "High confidence that this content has been artificially generated or heavily manipulated."
                DeepfakeDetectionService.RiskLevel.HIGH -> 
                    "Strong indicators suggest this content may be artificially generated."
                DeepfakeDetectionService.RiskLevel.MEDIUM -> 
                    "Some suspicious characteristics detected that warrant caution."
                DeepfakeDetectionService.RiskLevel.LOW -> 
                    "Minor inconsistencies detected, but content appears mostly authentic."
            }

            val detailedExplanation = buildDeepfakeExplanation(result)
            val keyPoints = extractKeyPoints(result)
            val userActions = generateUserActions(result)
            val educationalContent = createDeepfakeEducation()

            Explanation(
                title = title,
                summary = summary,
                detailedExplanation = detailedExplanation,
                keyPoints = keyPoints,
                userActions = userActions,
                educationalContent = educationalContent,
                confidenceLevel = confidence
            )
        }
    }

    suspend fun explainContentAnalysis(result: ContentAnalyzer.ContentAnalysisResult): Explanation {
        return withContext(Dispatchers.Default) {
            val riskLevel = result.riskLevel
            val concerns = result.concerns
            
            val title = when (riskLevel) {
                ContentAnalyzer.RiskLevel.DANGEROUS -> "Dangerous Content Detected"
                ContentAnalyzer.RiskLevel.HIGH_RISK -> "High-Risk Content Found"
                ContentAnalyzer.RiskLevel.MEDIUM_RISK -> "Potentially Concerning Content"
                ContentAnalyzer.RiskLevel.LOW_RISK -> "Minor Content Concerns"
                ContentAnalyzer.RiskLevel.SAFE -> "Content Appears Safe"
            }

            val summary = generateContentSummary(result)
            val detailedExplanation = buildContentExplanation(result)
            val keyPoints = extractContentKeyPoints(result)
            val userActions = result.recommendations
            val educationalContent = createContentEducation(result)

            Explanation(
                title = title,
                summary = summary,
                detailedExplanation = detailedExplanation,
                keyPoints = keyPoints,
                userActions = userActions,
                educationalContent = educationalContent,
                confidenceLevel = result.confidence
            )
        }
    }

    suspend fun explainThreatDetection(threatType: String, confidence: Float, evidence: List<String>): Explanation {
        return withContext(Dispatchers.Default) {
            val title = "Threat Detection: ${threatType.replaceFirstChar { it.uppercase() }}"
            
            val summary = when (threatType.lowercase()) {
                "phishing" -> "This content appears to be attempting to steal personal information or credentials."
                "malware" -> "This content may contain malicious software designed to harm your device."
                "scam" -> "This content shows characteristics of fraudulent schemes designed to deceive users."
                "misinformation" -> "This content may contain false or misleading information."
                else -> "Potentially harmful content has been detected."
            }

            val detailedExplanation = buildThreatExplanation(threatType, confidence, evidence)
            val keyPoints = generateThreatKeyPoints(threatType, evidence)
            val userActions = generateThreatActions(threatType)
            val educationalContent = createThreatEducation(threatType)

            Explanation(
                title = title,
                summary = summary,
                detailedExplanation = detailedExplanation,
                keyPoints = keyPoints,
                userActions = userActions,
                educationalContent = educationalContent,
                confidenceLevel = confidence
            )
        }
    }

    private fun buildDeepfakeExplanation(result: DeepfakeDetectionService.DeepfakeAnalysisResult): String {
        val technicalDetails = if (result.detectionDetails.technicalIndicators.isNotEmpty()) {
            "\n\nTechnical Analysis:\n" + result.detectionDetails.technicalIndicators.joinToString("\n") { "• $it" }
        } else ""

        val visualArtifacts = if (result.detectionDetails.visualArtifacts.isNotEmpty()) {
            "\n\nVisual Artifacts Detected:\n" + result.detectionDetails.visualArtifacts.joinToString("\n") { "• $it" }
        } else ""

        return """
            Our AI analysis examined this visual content for signs of artificial generation or manipulation. 
            The analysis considers facial features, lighting consistency, temporal coherence, and other factors 
            that may indicate synthetic content.
            
            Analysis Results:
            • Fake probability: ${(result.detectionDetails.fakeScore * 100).toInt()}%
            • Authentic probability: ${(result.detectionDetails.realScore * 100).toInt()}%
            • Risk level: ${result.detectionDetails.riskLevel.description}
            $technicalDetails$visualArtifacts
        """.trimIndent()
    }

    private fun buildContentExplanation(result: ContentAnalyzer.ContentAnalysisResult): String {
        val concernsText = if (result.concerns.isNotEmpty()) {
            "\n\nSpecific Concerns Identified:\n" + result.concerns.joinToString("\n") { concern ->
                "• ${concern.type.name.lowercase().replace('_', ' ')}: ${concern.description}"
            }
        } else ""

        val contextualInfo = result.contextualInfo.let { info ->
            if (info.educationalContext.isNotBlank()) {
                "\n\nContext: ${info.educationalContext}"
            } else ""
        }

        return """
            Our content analysis system examined this text and visual content for potentially harmful, 
            misleading, or inappropriate material. The analysis considers language patterns, 
            sentiment, factual claims, and known indicators of problematic content.
            
            Analysis Results:
            • Content type: ${result.contentType.name.lowercase()}
            • Risk level: ${result.riskLevel.name.replace('_', ' ').lowercase()}
            • Confidence: ${(result.confidence * 100).toInt()}%
            $concernsText$contextualInfo
        """.trimIndent()
    }

    private fun buildThreatExplanation(threatType: String, confidence: Float, evidence: List<String>): String {
        val evidenceText = if (evidence.isNotEmpty()) {
            "\n\nEvidence Found:\n" + evidence.joinToString("\n") { "• $it" }
        } else ""

        return """
            Our threat detection system identified potential ${threatType.lowercase()} characteristics 
            in this content. This determination is based on pattern analysis, known threat signatures, 
            and behavioral indicators.
            
            Detection Details:
            • Threat type: ${threatType.replaceFirstChar { it.uppercase() }}
            • Confidence level: ${(confidence * 100).toInt()}%
            • Analysis method: Pattern matching and AI classification
            $evidenceText
        """.trimIndent()
    }

    private fun extractKeyPoints(result: DeepfakeDetectionService.DeepfakeAnalysisResult): List<String> {
        val points = mutableListOf<String>()
        
        points.add("Analysis confidence: ${(result.confidence * 100).toInt()}%")
        points.add("Risk assessment: ${result.detectionDetails.riskLevel.description}")
        
        if (result.detectionDetails.fakeScore > 0.6f) {
            points.add("High probability of artificial generation")
        }
        
        if (result.detectionDetails.technicalIndicators.isNotEmpty()) {
            points.add("${result.detectionDetails.technicalIndicators.size} technical indicators detected")
        }
        
        if (result.recommendations.isNotEmpty()) {
            points.add("${result.recommendations.size} recommendations provided")
        }
        
        return points
    }

    private fun extractContentKeyPoints(result: ContentAnalyzer.ContentAnalysisResult): List<String> {
        val points = mutableListOf<String>()
        
        points.add("Content type: ${result.contentType.name.lowercase()}")
        points.add("Risk level: ${result.riskLevel.name.replace('_', ' ').lowercase()}")
        points.add("Analysis confidence: ${(result.confidence * 100).toInt()}%")
        
        if (result.concerns.isNotEmpty()) {
            val criticalConcerns = result.concerns.count { it.severity == ContentAnalyzer.Severity.CRITICAL }
            if (criticalConcerns > 0) {
                points.add("$criticalConcerns critical concerns identified")
            }
        }
        
        if (result.contextualInfo.topicCategory != "General") {
            points.add("Topic category: ${result.contextualInfo.topicCategory}")
        }
        
        return points
    }

    private fun generateUserActions(result: DeepfakeDetectionService.DeepfakeAnalysisResult): List<String> {
        val actions = mutableListOf<String>()
        
        when (result.detectionDetails.riskLevel) {
            DeepfakeDetectionService.RiskLevel.CRITICAL, DeepfakeDetectionService.RiskLevel.HIGH -> {
                actions.addAll(listOf(
                    "Do not share or forward this content",
                    "Verify the source and authenticity",
                    "Report to platform moderators if appropriate",
                    "Inform others about potential manipulation"
                ))
            }
            DeepfakeDetectionService.RiskLevel.MEDIUM -> {
                actions.addAll(listOf(
                    "Exercise caution when sharing",
                    "Verify through additional sources",
                    "Consider the context and source"
                ))
            }
            DeepfakeDetectionService.RiskLevel.LOW -> {
                actions.addAll(listOf(
                    "Content appears mostly authentic",
                    "Use normal judgment when sharing",
                    "Stay alert for inconsistencies"
                ))
            }
        }
        
        actions.addAll(result.recommendations)
        return actions.distinct()
    }

    private fun generateContentSummary(result: ContentAnalyzer.ContentAnalysisResult): String {
        return when (result.riskLevel) {
            ContentAnalyzer.RiskLevel.DANGEROUS -> 
                "This content contains dangerous information that could cause harm. Avoid sharing or acting on this information."
            ContentAnalyzer.RiskLevel.HIGH_RISK -> 
                "This content has significant concerns and may be harmful or misleading. Verify carefully before sharing."
            ContentAnalyzer.RiskLevel.MEDIUM_RISK -> 
                "This content has some concerning elements that warrant caution and verification."
            ContentAnalyzer.RiskLevel.LOW_RISK -> 
                "This content has minor concerns but is generally acceptable with normal caution."
            ContentAnalyzer.RiskLevel.SAFE -> 
                "This content appears to be safe and trustworthy based on our analysis."
        }
    }

    private fun generateThreatKeyPoints(threatType: String, evidence: List<String>): List<String> {
        val points = mutableListOf<String>()
        
        points.add("Threat type: ${threatType.replaceFirstChar { it.uppercase() }}")
        points.add("Evidence indicators: ${evidence.size}")
        
        when (threatType.lowercase()) {
            "phishing" -> points.add("Attempts to steal personal information")
            "malware" -> points.add("May contain harmful software")
            "scam" -> points.add("Fraudulent scheme detected")
            "misinformation" -> points.add("Contains potentially false information")
        }
        
        return points
    }

    private fun generateThreatActions(threatType: String): List<String> {
        return when (threatType.lowercase()) {
            "phishing" -> listOf(
                "Do not enter personal information",
                "Verify sender through official channels",
                "Report phishing attempt",
                "Check URL authenticity"
            )
            "malware" -> listOf(
                "Do not download or install anything",
                "Run security scan",
                "Avoid clicking links",
                "Report malicious content"
            )
            "scam" -> listOf(
                "Do not send money or personal information",
                "Verify claims independently",
                "Report fraudulent activity",
                "Warn others about the scam"
            )
            "misinformation" -> listOf(
                "Verify with reliable sources",
                "Check fact-checking websites",
                "Consider multiple perspectives",
                "Avoid sharing unverified information"
            )
            else -> listOf(
                "Exercise caution",
                "Verify through reliable sources",
                "Report if suspicious",
                "Seek expert advice if needed"
            )
        }
    }

    private fun createDeepfakeEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Deepfakes are AI-generated videos, images, or audio that replace a person's likeness with someone else's. They use machine learning to create convincing but fake content.",
            whyItMatters = "Deepfakes can spread misinformation, damage reputations, commit fraud, and undermine trust in authentic media. They're becoming increasingly sophisticated and harder to detect.",
            howToIdentify = listOf(
                "Look for unnatural facial movements or expressions",
                "Check for inconsistent lighting or shadows",
                "Notice blurring around face edges",
                "Watch for unusual blinking patterns",
                "Listen for voice inconsistencies"
            ),
            preventionTips = listOf(
                "Verify content through multiple reliable sources",
                "Use reverse image/video search tools",
                "Check official accounts and sources",
                "Be skeptical of sensational content",
                "Report suspected deepfakes"
            ),
            relatedConcepts = listOf(
                "Synthetic media",
                "AI-generated content",
                "Digital manipulation",
                "Media literacy",
                "Authentication technology"
            )
        )
    }

    private fun createContentEducation(result: ContentAnalyzer.ContentAnalysisResult): EducationalContent {
        val primaryConcern = result.concerns.maxByOrNull { it.severity.ordinal }
        
        return when (primaryConcern?.type) {
            ContentAnalyzer.ConcernType.MISINFORMATION -> createMisinformationEducation()
            ContentAnalyzer.ConcernType.FINANCIAL_SCAM -> createScamEducation()
            ContentAnalyzer.ConcernType.HEALTH_MISINFORMATION -> createHealthEducation()
            ContentAnalyzer.ConcernType.PRIVACY_RISK -> createPrivacyEducation()
            else -> createGeneralContentEducation()
        }
    }

    private fun createThreatEducation(threatType: String): EducationalContent {
        return when (threatType.lowercase()) {
            "phishing" -> createPhishingEducation()
            "malware" -> createMalwareEducation()
            "scam" -> createScamEducation()
            "misinformation" -> createMisinformationEducation()
            else -> createGeneralThreatEducation()
        }
    }

    private fun createMisinformationEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Misinformation is false or inaccurate information, regardless of intent. It can spread rapidly on social media and cause real-world harm.",
            whyItMatters = "Misinformation can influence important decisions, affect public health, undermine democratic processes, and erode trust in legitimate institutions.",
            howToIdentify = listOf(
                "Check the source and author credentials",
                "Look for supporting evidence and citations",
                "Verify with multiple reliable sources",
                "Check publication date and context",
                "Be wary of emotional or sensational language"
            ),
            preventionTips = listOf(
                "Verify before sharing",
                "Use fact-checking websites",
                "Check multiple news sources",
                "Consider the source's bias and agenda",
                "Pause before reacting emotionally"
            ),
            relatedConcepts = listOf(
                "Disinformation",
                "Fact-checking",
                "Media literacy",
                "Source verification",
                "Information quality"
            )
        )
    }

    private fun createScamEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Scams are fraudulent schemes designed to deceive people out of money, personal information, or other valuables through false promises or fake identities.",
            whyItMatters = "Scams cause billions in financial losses annually and can lead to identity theft, emotional distress, and long-term financial damage.",
            howToIdentify = listOf(
                "Too-good-to-be-true offers",
                "Urgent pressure to act quickly",
                "Requests for upfront payments",
                "Poor grammar and spelling",
                "Unsolicited contact"
            ),
            preventionTips = listOf(
                "Never send money to strangers",
                "Verify independently before acting",
                "Be suspicious of unsolicited offers",
                "Protect personal information",
                "Trust your instincts"
            ),
            relatedConcepts = listOf(
                "Fraud prevention",
                "Identity theft",
                "Financial security",
                "Social engineering",
                "Consumer protection"
            )
        )
    }

    private fun createHealthEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Health misinformation includes false or misleading claims about medical treatments, diseases, or health practices that can harm public health.",
            whyItMatters = "Health misinformation can delay proper treatment, promote dangerous practices, undermine public health efforts, and erode trust in medical science.",
            howToIdentify = listOf(
                "Claims of miracle cures",
                "Attacks on medical consensus",
                "Anecdotal evidence presented as proof",
                "Promotion of unproven treatments",
                "Conspiracy theories about health organizations"
            ),
            preventionTips = listOf(
                "Consult healthcare professionals",
                "Check with medical organizations",
                "Verify through peer-reviewed sources",
                "Be wary of testimonials",
                "Consider scientific consensus"
            ),
            relatedConcepts = listOf(
                "Medical misinformation",
                "Evidence-based medicine",
                "Health literacy",
                "Scientific method",
                "Public health"
            )
        )
    }

    private fun createPrivacyEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Privacy risks involve potential exposure or misuse of personal information through data collection, sharing, or security breaches.",
            whyItMatters = "Privacy violations can lead to identity theft, financial fraud, stalking, discrimination, and loss of personal autonomy.",
            howToIdentify = listOf(
                "Requests for unnecessary personal information",
                "Unclear privacy policies",
                "Excessive data collection",
                "Third-party data sharing",
                "Insecure data handling"
            ),
            preventionTips = listOf(
                "Read privacy policies",
                "Limit information sharing",
                "Use privacy settings",
                "Be cautious with permissions",
                "Regularly review account settings"
            ),
            relatedConcepts = listOf(
                "Data protection",
                "Information security",
                "Digital privacy",
                "Identity protection",
                "Surveillance"
            )
        )
    }

    private fun createPhishingEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Phishing is a cyberattack that uses disguised emails, websites, or messages to steal sensitive information like passwords or credit card numbers.",
            whyItMatters = "Phishing attacks can lead to identity theft, financial loss, data breaches, and unauthorized access to personal or business accounts.",
            howToIdentify = listOf(
                "Urgent or threatening language",
                "Suspicious sender addresses",
                "Generic greetings",
                "Spelling and grammar errors",
                "Requests for sensitive information"
            ),
            preventionTips = listOf(
                "Verify sender through official channels",
                "Check URLs carefully",
                "Don't click suspicious links",
                "Use two-factor authentication",
                "Keep software updated"
            ),
            relatedConcepts = listOf(
                "Cybersecurity",
                "Social engineering",
                "Email security",
                "Identity theft",
                "Digital fraud"
            )
        )
    }

    private fun createMalwareEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Malware is malicious software designed to damage, disable, or gain unauthorized access to computer systems and data.",
            whyItMatters = "Malware can steal personal information, damage files, slow down devices, spy on activities, and provide unauthorized access to cybercriminals.",
            howToIdentify = listOf(
                "Suspicious downloads or attachments",
                "Unexpected pop-ups or ads",
                "Slow device performance",
                "Unusual network activity",
                "Unauthorized software installations"
            ),
            preventionTips = listOf(
                "Use reputable antivirus software",
                "Keep systems updated",
                "Avoid suspicious downloads",
                "Be cautious with email attachments",
                "Use official app stores"
            ),
            relatedConcepts = listOf(
                "Computer viruses",
                "Spyware",
                "Ransomware",
                "Trojan horses",
                "Cybersecurity"
            )
        )
    }

    private fun createGeneralContentEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Content analysis involves examining text, images, and other media for potentially harmful, misleading, or inappropriate material.",
            whyItMatters = "Harmful content can spread misinformation, promote dangerous behaviors, cause psychological harm, and undermine social trust.",
            howToIdentify = listOf(
                "Inflammatory or divisive language",
                "Unsubstantiated claims",
                "Manipulative emotional appeals",
                "Promotion of harmful behaviors",
                "Lack of credible sources"
            ),
            preventionTips = listOf(
                "Critical thinking and media literacy",
                "Verify information independently",
                "Consider multiple perspectives",
                "Check source credibility",
                "Be aware of personal biases"
            ),
            relatedConcepts = listOf(
                "Media literacy",
                "Critical thinking",
                "Information quality",
                "Content moderation",
                "Digital citizenship"
            )
        )
    }

    private fun createGeneralThreatEducation(): EducationalContent {
        return EducationalContent(
            whatIsIt = "Digital threats encompass various forms of malicious activities designed to harm users, steal information, or compromise security.",
            whyItMatters = "Digital threats can cause financial loss, privacy violations, identity theft, and significant disruption to personal and professional life.",
            howToIdentify = listOf(
                "Suspicious communications",
                "Unusual system behavior",
                "Unexpected requests for information",
                "Warning signs from security software",
                "Reports from trusted sources"
            ),
            preventionTips = listOf(
                "Maintain security awareness",
                "Use updated security software",
                "Practice safe browsing habits",
                "Verify suspicious activities",
                "Report threats to authorities"
            ),
            relatedConcepts = listOf(
                "Cybersecurity",
                "Digital safety",
                "Threat intelligence",
                "Risk management",
                "Security awareness"
            )
        )
    }
} 