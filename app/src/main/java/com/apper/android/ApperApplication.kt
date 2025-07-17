package com.apper.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.apper.android.core.AppConstants
import com.apper.android.core.PreferencesManager
import com.google.firebase.FirebaseApp

class ApperApplication : Application(), Configuration.Provider {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize preferences
        preferencesManager = PreferencesManager(this)
        
        // Create notification channels
        createNotificationChannels()
        
        // Initialize WorkManager
        WorkManager.initialize(this, workManagerConfiguration)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Foreground service notification channel
            val serviceChannel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_SERVICE,
                "Apper Protection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Apper is actively protecting your browsing and social media"
                setShowBadge(false)
            }
            
            // Alerts notification channel
            val alertsChannel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important security alerts from Apper"
            }
            
            notificationManager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()
} 