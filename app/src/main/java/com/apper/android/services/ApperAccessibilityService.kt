package com.apper.android.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.apper.android.ApperApplication
import com.apper.android.ai.*
import com.apper.android.core.AppConstants
import com.apper.android.ui.OverlayManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class ApperAccessibilityService : AccessibilityService() {

    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private lateinit var preferencesManager: com.apper.android.core.PreferencesManager
    
    // AI Components
    private lateinit var deepfakeDetectionService: DeepfakeDetectionService
    private lateinit var contentAnalyzer: ContentAnalyzer
    private lateinit var overlayManager: OverlayManager
    
    private var lastScanTime = 0L
    private val scanCooldownMs = AppConstants.SCAN_INTERVAL_SECONDS * 1000
    
    private lateinit var threatReceiver: BroadcastReceiver
    
    // Analytics
    private val contentScanned = AtomicInteger(0)
    private val threatsDetected = AtomicInteger(0)
    private val deepfakesFound = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ApperAccessibilityService created")
        preferencesManager = (application as ApperApplication).preferencesManager
        
        // Initialize AI components
        initializeAIComponents()
        
        // Set up threat blocking receiver
        setupThreatReceiver()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "ApperAccessibilityService connected")
        
        serviceScope.launch {
            preferencesManager.setAccessibilityEnabled(true)
            
            // Initialize AI services asynchronously
            try {
                val deepfakeInitialized = deepfakeDetectionService.initialize()
                val contentInitialized = contentAnalyzer.initialize()
                
                Log.i(TAG, "AI Services initialized - Deepfake: $deepfakeInitialized, Content: $contentInitialized")
                
                if (!deepfakeInitialized || !contentInitialized) {
                    Log.w(TAG, "Some AI services failed to initialize, functionality may be limited")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AI services", e)
            }
        }
    }

    private fun initializeAIComponents() {
        try {
            deepfakeDetectionService = DeepfakeDetectionService(this)
            contentAnalyzer = ContentAnalyzer(this)
            overlayManager = OverlayManager(this)
            
            Log.d(TAG, "AI components initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AI components", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        // Rate limit scanning to avoid performance issues
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < scanCooldownMs) {
            return
        }
        lastScanTime = currentTime

        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleContentChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    handleViewScrolled(event)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    handleTextChanged(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "ApperAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ApperAccessibilityService destroyed")
        
        serviceScope.launch {
            preferencesManager.setAccessibilityEnabled(false)
        }
        
        // Cleanup AI components
        try {
            deepfakeDetectionService.cleanup()
            contentAnalyzer.cleanup()
            overlayManager.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AI components", e)
        }
        
        // Unregister threat receiver
        try {
            unregisterReceiver(threatReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering threat receiver", e)
        }
        
        serviceJob.cancel()
        
        // Log final statistics
        logAnalyticsStats()
    }

    fun getAIAnalyticsStats(): AIAnalyticsStats {
        return AIAnalyticsStats(
            contentScanned = contentScanned.get(),
            threatsDetected = threatsDetected.get(),
            deepfakesFound = deepfakesFound.get(),
            deepfakeStats = deepfakeDetectionService.getAnalysisStats(),
            contentStats = contentAnalyzer.getAnalysisStats()
        )
    }

    private fun logAnalyticsStats() {
        val stats = getAIAnalyticsStats()
        Log.i(TAG, "AI Analytics - Content Scanned: ${stats.contentScanned}, " +
               "Threats: ${stats.threatsDetected}, Deepfakes: ${stats.deepfakesFound}")
    }

    data class AIAnalyticsStats(
        val contentScanned: Int,
        val threatsDetected: Int,
        val deepfakesFound: Int,
        val deepfakeStats: DeepfakeDetectionService.AnalysisStats,
        val contentStats: ContentAnalyzer.AnalysisStats
    )

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        Log.d(TAG, "Window state changed: $packageName")
        
        when {
            isBrowserApp(packageName) -> {
                Log.d(TAG, "Browser app detected: $packageName")
                processBrowserContent(event)
            }
            isSocialMediaApp(packageName) -> {
                Log.d(TAG, "Social media app detected: $packageName")
                processSocialMediaContent(event)
            }
            else -> {
                Log.d(TAG, "Other app detected: $packageName")
                processGeneralContent(event)
            }
        }
    }

    private fun handleContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        if (isSocialMediaApp(packageName)) {
            // This is where we'd scan for deepfakes and harmful content
            processSocialMediaContent(event)
        }
    }

    private fun handleViewScrolled(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        if (isSocialMediaApp(packageName)) {
            // Scan new content that becomes visible after scrolling
            Log.d(TAG, "Social media scroll detected, scanning new content")
            processSocialMediaContent(event)
        }
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        // Monitor text input in supported apps
        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "Text changed in $packageName")
    }

    private fun processBrowserContent(event: AccessibilityEvent) {
        serviceScope.launch {
            try {
                val rootNode = rootInActiveWindow ?: return@launch
                
                // Look for URL bars and content
                val urlNodes = findNodesByText(rootNode, "http")
                val contentNodes = findNodesByClassName(rootNode, "android.webkit.WebView")
                val textNodes = findNodesByClassName(rootNode, "android.widget.TextView")
                
                Log.d(TAG, "Browser scan - URLs: ${urlNodes.size}, Content: ${contentNodes.size}, Text: ${textNodes.size}")
                
                contentScanned.incrementAndGet()
                
                // Extract and analyze URLs
                processBrowserUrls(urlNodes)
                
                // Analyze visible text content on the webpage
                processBrowserTextContent(textNodes)
                
                // Clean up node references
                urlNodes.forEach { it.recycle() }
                contentNodes.forEach { it.recycle() }
                textNodes.forEach { it.recycle() }
                rootNode.recycle()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing browser content", e)
            }
        }
    }

    private suspend fun processBrowserUrls(urlNodes: List<AccessibilityNodeInfo>) {
        withContext(Dispatchers.Default) {
            try {
                val urls = urlNodes.mapNotNull { node ->
                    node.text?.toString()?.takeIf { it.startsWith("http") }
                }
                
                urls.forEach { url ->
                    Log.d(TAG, "Analyzing browser URL: $url")
                    
                    // Use existing threat detector (from VPN service) for URL analysis
                    // This creates synergy between browser protection and accessibility analysis
                    
                    // For demonstration, show help for suspicious patterns
                    if (isSuspiciousUrl(url)) {
                        threatsDetected.incrementAndGet()
                        
                        val helpData = OverlayManager.HelpOverlayData(
                            title = "Suspicious URL Detected",
                            explanation = "The current webpage URL contains patterns that may indicate a suspicious or potentially harmful website.",
                            tips = listOf(
                                "Verify the website URL is correct",
                                "Check for misspellings in the domain",
                                "Look for HTTPS security indicator",
                                "Be cautious with personal information",
                                "Consider navigating away if unsure"
                            ),
                            relatedInfo = listOf(
                                "Report suspicious websites",
                                "Check with security vendors",
                                "Use browser security features"
                            )
                        )
                        
                        withContext(Dispatchers.Main) {
                            overlayManager.showHelpOverlay(helpData)
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing browser URLs", e)
            }
        }
    }

    private suspend fun processBrowserTextContent(textNodes: List<AccessibilityNodeInfo>) {
        withContext(Dispatchers.Default) {
            try {
                // Extract visible text content from the webpage
                val webContent = textNodes.mapNotNull { node ->
                    node.text?.toString()?.takeIf { 
                        it.isNotBlank() && it.length > 20 && !it.startsWith("http")
                    }
                }.joinToString(" ")
                
                if (webContent.isBlank()) return@withContext
                
                Log.d(TAG, "Analyzing browser content: ${webContent.take(100)}...")
                
                // Analyze webpage content for misinformation, scams, etc.
                val analysisResult = contentAnalyzer.analyzeContent(text = webContent)
                
                // Show warnings for concerning web content
                if (analysisResult.riskLevel != ContentAnalyzer.RiskLevel.SAFE) {
                    threatsDetected.incrementAndGet()
                    
                    val warningData = OverlayManager.WarningOverlayData(
                        title = "Webpage Content Alert",
                        message = "This webpage contains content that may be misleading or harmful.",
                        riskLevel = analysisResult.riskLevel,
                        concerns = analysisResult.concerns,
                        recommendations = analysisResult.recommendations,
                        contextualInfo = analysisResult.contextualInfo
                    )
                    
                    withContext(Dispatchers.Main) {
                        overlayManager.showContentWarning(warningData)
                    }
                    
                    Log.w(TAG, "Flagged browser content: ${analysisResult.riskLevel}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing browser content", e)
            }
        }
    }

    private fun isSuspiciousUrl(url: String): Boolean {
        val urlLower = url.lowercase()
        val suspiciousPatterns = listOf(
            "phishing", "scam", "fake", "malware", "virus",
            "free-money", "urgent-update", "security-alert",
            "account-suspended", "verify-now"
        )
        
        return suspiciousPatterns.any { urlLower.contains(it) } ||
               url.count { it == '.' } > 4 || // Too many subdomains
               url.matches(Regex(".*[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}.*")) // IP address
    }

    private fun processSocialMediaContent(event: AccessibilityEvent) {
        serviceScope.launch {
            try {
                val rootNode = rootInActiveWindow ?: return@launch
                
                // Look for image, video, and text content
                val imageNodes = findNodesByClassName(rootNode, "android.widget.ImageView")
                val videoNodes = findNodesByClassName(rootNode, "android.widget.VideoView")
                val textNodes = findNodesByClassName(rootNode, "android.widget.TextView")
                
                Log.d(TAG, "Social media scan - Images: ${imageNodes.size}, Videos: ${videoNodes.size}, Text: ${textNodes.size}")
                
                contentScanned.incrementAndGet()
                
                // Process text content for harmful patterns
                processTextContent(textNodes)
                
                // Process visual content for deepfakes and harmful content
                processVisualContent(imageNodes, videoNodes, event.packageName?.toString())
                
                // Clean up node references
                imageNodes.forEach { it.recycle() }
                videoNodes.forEach { it.recycle() }
                textNodes.forEach { it.recycle() }
                rootNode.recycle()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing social media content", e)
            }
        }
    }

    private suspend fun processTextContent(textNodes: List<AccessibilityNodeInfo>) {
        withContext(Dispatchers.Default) {
            try {
                // Extract text from nodes
                val textContent = textNodes.mapNotNull { node ->
                    node.text?.toString()?.takeIf { it.isNotBlank() && it.length > 10 }
                }.joinToString(" ")
                
                if (textContent.isBlank()) return@withContext
                
                Log.d(TAG, "Analyzing text content: ${textContent.take(100)}...")
                
                // Analyze combined text content
                val analysisResult = contentAnalyzer.analyzeContent(text = textContent)
                
                // Show warning if content is concerning
                if (analysisResult.riskLevel != ContentAnalyzer.RiskLevel.SAFE) {
                    threatsDetected.incrementAndGet()
                    
                    val warningData = OverlayManager.WarningOverlayData(
                        title = "Content Analysis Alert",
                        message = analysisResult.explanation,
                        riskLevel = analysisResult.riskLevel,
                        concerns = analysisResult.concerns,
                        recommendations = analysisResult.recommendations,
                        contextualInfo = analysisResult.contextualInfo
                    )
                    
                    withContext(Dispatchers.Main) {
                        overlayManager.showContentWarning(warningData)
                    }
                    
                    Log.w(TAG, "Flagged social media text content: ${analysisResult.riskLevel}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing text content", e)
            }
        }
    }

    private suspend fun processVisualContent(
        imageNodes: List<AccessibilityNodeInfo>, 
        videoNodes: List<AccessibilityNodeInfo>,
        packageName: String?
    ) {
        withContext(Dispatchers.Default) {
            try {
                // For demonstration, we'll simulate image/video analysis
                // In a real implementation, this would extract actual image data
                
                val totalVisualContent = imageNodes.size + videoNodes.size
                if (totalVisualContent == 0) return@withContext
                
                Log.d(TAG, "Processing $totalVisualContent visual content items")
                
                // Simulate deepfake detection on visible content
                // In production, this would extract actual bitmaps from the UI
                simulateVisualContentAnalysis(totalVisualContent, packageName)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing visual content", e)
            }
        }
    }

    private suspend fun simulateVisualContentAnalysis(contentCount: Int, packageName: String?) {
        withContext(Dispatchers.Default) {
            try {
                // Simulate AI analysis delay
                delay(200)
                
                // Random chance of detecting concerning content (for demo)
                val riskThreshold = when (packageName) {
                    "com.instagram.android" -> 0.15 // 15% chance on Instagram
                    "com.zhiliaoapp.musically" -> 0.20 // 20% chance on TikTok
                    "com.facebook.katana" -> 0.10 // 10% chance on Facebook
                    else -> 0.05 // 5% chance on other platforms
                }
                
                if (Math.random() < riskThreshold) {
                    // Simulate different types of concerning content
                    val contentTypes = listOf("deepfake", "misleading", "harmful")
                    val detectedType = contentTypes.random()
                    
                    when (detectedType) {
                        "deepfake" -> {
                            deepfakesFound.incrementAndGet()
                            threatsDetected.incrementAndGet()
                            
                            val deepfakeData = OverlayManager.DeepfakeWarningData(
                                confidence = 0.7f + (Math.random() * 0.3).toFloat(),
                                riskLevel = DeepfakeDetectionService.RiskLevel.HIGH,
                                explanation = "This visual content shows signs of artificial generation or manipulation.",
                                technicalIndicators = listOf(
                                    "Facial feature inconsistencies detected",
                                    "Unnatural lighting patterns identified",
                                    "High confidence in AI model prediction"
                                ),
                                recommendations = listOf(
                                    "Verify content authenticity through reliable sources",
                                    "Be cautious when sharing this content",
                                    "Check fact-checking websites"
                                )
                            )
                            
                            withContext(Dispatchers.Main) {
                                overlayManager.showDeepfakeWarning(deepfakeData)
                            }
                            
                            Log.w(TAG, "Simulated deepfake detection in $packageName")
                        }
                        
                        "misleading", "harmful" -> {
                            threatsDetected.incrementAndGet()
                            
                            val concerns = listOf(
                                ContentAnalyzer.ContentConcern(
                                    type = if (detectedType == "misleading") 
                                        ContentAnalyzer.ConcernType.MISINFORMATION 
                                        else ContentAnalyzer.ConcernType.HARMFUL_CONTENT,
                                    severity = ContentAnalyzer.Severity.WARNING,
                                    description = "Visual content may contain ${detectedType} information",
                                    evidence = listOf("AI analysis detected suspicious patterns")
                                )
                            )
                            
                            val warningData = OverlayManager.WarningOverlayData(
                                title = "Visual Content Alert",
                                message = "This visual content may contain ${detectedType} information that could be harmful or misleading.",
                                riskLevel = ContentAnalyzer.RiskLevel.MEDIUM_RISK,
                                concerns = concerns,
                                recommendations = listOf(
                                    "Verify information with reliable sources",
                                    "Be cautious when sharing this content",
                                    "Consider the source and context"
                                )
                            )
                            
                            withContext(Dispatchers.Main) {
                                overlayManager.showContentWarning(warningData)
                            }
                            
                            Log.w(TAG, "Simulated $detectedType content detection in $packageName")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in simulated visual content analysis", e)
            }
        }
    }

    private fun processGeneralContent(event: AccessibilityEvent) {
        serviceScope.launch {
            try {
                // Provide universal help features
                Log.d(TAG, "Processing general app content for universal help")
                
                // In a real implementation, this would:
                // 1. Analyze current screen context
                // 2. Provide relevant help or explanations
                // 3. Offer accessibility improvements
                // 4. Detect potentially confusing UI elements
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing general content", e)
            }
        }
    }

    // Method removed - replaced with real AI analysis

    private fun findNodesByText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            val foundNodes = root.findAccessibilityNodeInfosByText(text)
            if (foundNodes != null) {
                nodes.addAll(foundNodes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nodes by text", e)
        }
        return nodes
    }

    private fun findNodesByClassName(root: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            findNodesByClassNameRecursive(root, className, nodes)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding nodes by class name", e)
        }
        return nodes
    }

    private fun findNodesByClassNameRecursive(
        node: AccessibilityNodeInfo, 
        className: String, 
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.className?.toString() == className) {
            result.add(AccessibilityNodeInfo.obtain(node))
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassNameRecursive(child, className, result)
            child.recycle()
        }
    }

    private fun isBrowserApp(packageName: String): Boolean {
        return AppConstants.SUPPORTED_BROWSERS.contains(packageName)
    }

    private fun isSocialMediaApp(packageName: String): Boolean {
        return AppConstants.SUPPORTED_SOCIAL_APPS.contains(packageName)
    }

    private fun setupThreatReceiver() {
        threatReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ApperVpnService.ACTION_THREAT_BLOCKED -> {
                        val blockedUrl = intent.getStringExtra(ApperVpnService.EXTRA_BLOCKED_URL)
                        val threatType = intent.getStringExtra(ApperVpnService.EXTRA_THREAT_TYPE)
                        
                        Log.w(TAG, "Threat blocked: $blockedUrl (type: $threatType)")
                        
                        // Show user-friendly warning
                        showThreatWarning(blockedUrl, threatType)
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ApperVpnService.ACTION_THREAT_BLOCKED)
        }
        
        try {
            registerReceiver(threatReceiver, filter)
            Log.d(TAG, "Threat receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering threat receiver", e)
        }
    }

    private fun showThreatWarning(blockedUrl: String?, threatType: String?) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Showing threat warning for: $blockedUrl")
                
                // In a real implementation, this would:
                // 1. Create an overlay window with warning message
                // 2. Show explanation of what was blocked and why
                // 3. Provide options to allow/block permanently
                // 4. Update user about the protection working
                
                // For now, just log the event
                Log.i(TAG, "THREAT BLOCKED: $blockedUrl ($threatType)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing threat warning", e)
            }
        }
    }

    companion object {
        private const val TAG = "ApperAccessibilityService"
    }
} 