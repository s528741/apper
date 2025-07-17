package com.apper.android.core

object AppConstants {
    // Notification channels
    const val NOTIFICATION_CHANNEL_SERVICE = "apper_service_channel"
    const val NOTIFICATION_CHANNEL_ALERTS = "apper_alerts_channel"
    
    // Notification IDs
    const val NOTIFICATION_ID_SERVICE = 1001
    const val NOTIFICATION_ID_ALERT = 1002
    
    // Service constants
    const val FOREGROUND_SERVICE_ID = 1001
    
    // Permission request codes
    const val REQUEST_CODE_VPN = 2001
    const val REQUEST_CODE_ACCESSIBILITY = 2002
    const val REQUEST_CODE_OVERLAY = 2003
    const val REQUEST_CODE_NOTIFICATIONS = 2004
    const val REQUEST_CODE_BATTERY_OPTIMIZATION = 2005
    
    // Shared preferences keys
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
    const val PREF_VPN_ENABLED = "vpn_enabled"
    const val PREF_ACCESSIBILITY_ENABLED = "accessibility_enabled"
    const val PREF_BROWSER_PROTECTION_ENABLED = "browser_protection_enabled"
    const val PREF_SOCIAL_MEDIA_PROTECTION_ENABLED = "social_media_protection_enabled"
    const val PREF_UNIVERSAL_HELP_ENABLED = "universal_help_enabled"
    
    // AI Model constants
    const val AI_MODEL_DEEPFAKE_DETECTION = "deepfake_detector.tflite"
    const val AI_MODEL_CONTENT_ANALYSIS = "content_analyzer.tflite"
    const val AI_MODEL_MAX_SIZE_MB = 10
    
    // Performance targets
    const val MAX_LATENCY_MS = 200L
    const val MAX_BATTERY_DRAIN_PERCENT_PER_HOUR = 5
    
    // VPN configuration
    const val VPN_DNS_PRIMARY = "94.140.14.14" // AdGuard DNS
    const val VPN_DNS_SECONDARY = "94.140.15.15" // AdGuard DNS
    const val VPN_MTU = 1500
    
    // App monitoring intervals
    const val MONITORING_INTERVAL_SECONDS = 2L
    const val SCAN_INTERVAL_SECONDS = 5L
    
    // Supported browsers
    val SUPPORTED_BROWSERS = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.brave.browser",
        "com.duckduckgo.mobile.android"
    )
    
    // Supported social media apps
    val SUPPORTED_SOCIAL_APPS = setOf(
        "com.facebook.katana",
        "com.instagram.android",
        "com.twitter.android",
        "com.snapchat.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.linkedin.android",
        "com.reddit.frontpage"
    )
} 