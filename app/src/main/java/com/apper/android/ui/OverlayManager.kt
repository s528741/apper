package com.apper.android.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apper.android.ai.ContentAnalyzer
import com.apper.android.ai.DeepfakeDetectionService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class OverlayManager(private val context: Context) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeOverlays = ConcurrentHashMap<String, OverlayInfo>()
    private val overlayScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "OverlayManager"
        private const val MAX_OVERLAYS = 3
        private const val AUTO_DISMISS_DELAY = 8000L // 8 seconds
    }

    data class OverlayInfo(
        val id: String,
        val view: View,
        val layoutParams: WindowManager.LayoutParams,
        val dismissJob: Job? = null
    )

    data class WarningOverlayData(
        val title: String,
        val message: String,
        val riskLevel: ContentAnalyzer.RiskLevel,
        val concerns: List<ContentAnalyzer.ContentConcern>,
        val recommendations: List<String>,
        val contextualInfo: ContentAnalyzer.ContextualInfo? = null,
        val showDismiss: Boolean = true,
        val autoDismiss: Boolean = true
    )

    data class DeepfakeWarningData(
        val confidence: Float,
        val riskLevel: DeepfakeDetectionService.RiskLevel,
        val explanation: String,
        val technicalIndicators: List<String>,
        val recommendations: List<String>
    )

    data class HelpOverlayData(
        val title: String,
        val explanation: String,
        val tips: List<String>,
        val relatedInfo: List<String> = emptyList()
    )

    fun showContentWarning(data: WarningOverlayData): String {
        val overlayId = "warning_${System.currentTimeMillis()}"
        
        try {
            // Remove oldest overlay if at limit
            if (activeOverlays.size >= MAX_OVERLAYS) {
                val oldestId = activeOverlays.keys.firstOrNull()
                oldestId?.let { dismissOverlay(it) }
            }
            
            val composeView = ComposeView(context).apply {
                setContent {
                    ContentWarningOverlay(
                        data = data,
                        onDismiss = { dismissOverlay(overlayId) },
                        onMoreInfo = { showDetailedInfo(data) }
                    )
                }
            }
            
            val layoutParams = createOverlayLayoutParams()
            
            windowManager.addView(composeView, layoutParams)
            
            val dismissJob = if (data.autoDismiss) {
                overlayScope.launch {
                    delay(AUTO_DISMISS_DELAY)
                    dismissOverlay(overlayId)
                }
            } else null
            
            activeOverlays[overlayId] = OverlayInfo(overlayId, composeView, layoutParams, dismissJob)
            
            Log.d(TAG, "Showed content warning overlay: $overlayId")
            return overlayId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing content warning overlay", e)
            return ""
        }
    }

    fun showDeepfakeWarning(data: DeepfakeWarningData): String {
        val overlayId = "deepfake_${System.currentTimeMillis()}"
        
        try {
            val composeView = ComposeView(context).apply {
                setContent {
                    DeepfakeWarningOverlay(
                        data = data,
                        onDismiss = { dismissOverlay(overlayId) },
                        onLearnMore = { showDeepfakeEducation() }
                    )
                }
            }
            
            val layoutParams = createOverlayLayoutParams()
            windowManager.addView(composeView, layoutParams)
            
            val dismissJob = overlayScope.launch {
                delay(AUTO_DISMISS_DELAY)
                dismissOverlay(overlayId)
            }
            
            activeOverlays[overlayId] = OverlayInfo(overlayId, composeView, layoutParams, dismissJob)
            
            Log.d(TAG, "Showed deepfake warning overlay: $overlayId")
            return overlayId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing deepfake warning overlay", e)
            return ""
        }
    }

    fun showHelpOverlay(data: HelpOverlayData): String {
        val overlayId = "help_${System.currentTimeMillis()}"
        
        try {
            val composeView = ComposeView(context).apply {
                setContent {
                    HelpOverlay(
                        data = data,
                        onDismiss = { dismissOverlay(overlayId) }
                    )
                }
            }
            
            val layoutParams = createOverlayLayoutParams()
            windowManager.addView(composeView, layoutParams)
            
            activeOverlays[overlayId] = OverlayInfo(overlayId, composeView, layoutParams, null)
            
            Log.d(TAG, "Showed help overlay: $overlayId")
            return overlayId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing help overlay", e)
            return ""
        }
    }

    private fun createOverlayLayoutParams(): WindowManager.LayoutParams {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Offset from top
        }
    }

    fun dismissOverlay(overlayId: String) {
        activeOverlays[overlayId]?.let { overlayInfo ->
            try {
                overlayInfo.dismissJob?.cancel()
                windowManager.removeView(overlayInfo.view)
                activeOverlays.remove(overlayId)
                Log.d(TAG, "Dismissed overlay: $overlayId")
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing overlay: $overlayId", e)
            }
        }
    }

    fun dismissAllOverlays() {
        val overlayIds = activeOverlays.keys.toList()
        overlayIds.forEach { dismissOverlay(it) }
    }

    private fun showDetailedInfo(data: WarningOverlayData) {
        val detailedData = HelpOverlayData(
            title = "Detailed Analysis",
            explanation = buildDetailedExplanation(data),
            tips = data.recommendations,
            relatedInfo = data.contextualInfo?.relatedSources ?: emptyList()
        )
        showHelpOverlay(detailedData)
    }

    private fun showDeepfakeEducation() {
        val educationData = HelpOverlayData(
            title = "Understanding Deepfakes",
            explanation = "Deepfakes are AI-generated videos or images that can make people appear to say or do things they never actually did. They can be used to spread misinformation or create fraudulent content.",
            tips = listOf(
                "Look for unnatural facial movements or expressions",
                "Check for inconsistent lighting or shadows",
                "Verify content through reverse image search",
                "Cross-reference with reliable news sources",
                "Be skeptical of sensational or controversial content"
            ),
            relatedInfo = listOf(
                "Learn more about digital literacy",
                "Check fact-checking websites",
                "Report suspicious content"
            )
        )
        showHelpOverlay(educationData)
    }

    private fun buildDetailedExplanation(data: WarningOverlayData): String {
        val concerns = data.concerns.joinToString("\n") { concern ->
            "• ${concern.description}"
        }
        
        val contextInfo = data.contextualInfo?.let { info ->
            "\n\nTopic: ${info.topicCategory}\n${info.educationalContext}"
        } ?: ""
        
        return "${data.message}\n\nSpecific Concerns:\n$concerns$contextInfo"
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up overlay manager...")
        dismissAllOverlays()
        overlayScope.cancel()
    }
}

@Composable
private fun ContentWarningOverlay(
    data: OverlayManager.WarningOverlayData,
    onDismiss: () -> Unit,
    onMoreInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = getWarningColor(data.riskLevel)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getWarningIcon(data.riskLevel),
                        contentDescription = "Warning",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = data.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (data.showDismiss) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = data.message,
                color = Color.White,
                fontSize = 14.sp
            )
            
            if (data.concerns.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Key Concerns:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                data.concerns.take(2).forEach { concern ->
                    Text(
                        text = "• ${concern.description}",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onMoreInfo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Learn More", color = Color.White, fontSize = 12.sp)
                }
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DeepfakeWarningOverlay(
    data: OverlayManager.DeepfakeWarningData,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFD32F2F) // Red for deepfake warning
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Deepfake Warning",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Potential Deepfake Detected",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = data.explanation,
                color = Color.White,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Confidence: ${(data.confidence * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (data.technicalIndicators.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Technical Indicators:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                data.technicalIndicators.take(2).forEach { indicator ->
                    Text(
                        text = "• $indicator",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLearnMore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Learn About Deepfakes", color = Color.White, fontSize = 12.sp)
                }
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HelpOverlay(
    data: OverlayManager.HelpOverlayData,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1976D2) // Blue for help
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Help",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = data.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = data.explanation,
                color = Color.White,
                fontSize = 14.sp
            )
            
            if (data.tips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tips:",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                data.tips.forEach { tip ->
                    Text(
                        text = "• $tip",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
            
            if (data.relatedInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Related Information:",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                data.relatedInfo.forEach { info ->
                    Text(
                        text = "• $info",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Got It", color = Color.White)
            }
        }
    }
}

private fun getWarningColor(riskLevel: ContentAnalyzer.RiskLevel): Color {
    return when (riskLevel) {
        ContentAnalyzer.RiskLevel.SAFE -> Color(0xFF4CAF50) // Green
        ContentAnalyzer.RiskLevel.LOW_RISK -> Color(0xFFFF9800) // Orange
        ContentAnalyzer.RiskLevel.MEDIUM_RISK -> Color(0xFFFF5722) // Deep Orange
        ContentAnalyzer.RiskLevel.HIGH_RISK -> Color(0xFFD32F2F) // Red
        ContentAnalyzer.RiskLevel.DANGEROUS -> Color(0xFF8E24AA) // Purple
    }
}

private fun getWarningIcon(riskLevel: ContentAnalyzer.RiskLevel): androidx.compose.ui.graphics.vector.ImageVector {
    return when (riskLevel) {
        ContentAnalyzer.RiskLevel.SAFE -> Icons.Default.CheckCircle
        ContentAnalyzer.RiskLevel.LOW_RISK -> Icons.Default.Info
        ContentAnalyzer.RiskLevel.MEDIUM_RISK -> Icons.Default.Warning
        ContentAnalyzer.RiskLevel.HIGH_RISK -> Icons.Default.Error
        ContentAnalyzer.RiskLevel.DANGEROUS -> Icons.Default.Block
    }
} 