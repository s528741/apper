package com.apper.android.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.apper.android.ApperApplication
import com.apper.android.R
import com.apper.android.core.AppConstants
import com.apper.android.ui.MainActivity
import com.apper.android.workers.AppMonitoringWorker
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class ApperMonitoringService : Service() {
    
    private val binder = LocalBinder()
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private lateinit var notificationManager: NotificationManager
    private lateinit var preferencesManager: com.apper.android.core.PreferencesManager
    
    private var isRunning = false
    private var blockedThreats = 0
    private var scannedContent = 0
    
    inner class LocalBinder : Binder() {
        fun getService(): ApperMonitoringService = this@ApperMonitoringService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ApperMonitoringService created")
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        preferencesManager = (application as ApperApplication).preferencesManager
        
        // Initialize monitoring
        startForegroundService()
        scheduleBackgroundWork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ApperMonitoringService started")
        
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_STATS -> updateNotification()
            else -> startMonitoring()
        }
        
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ApperMonitoringService destroyed")
        
        stopMonitoring()
        serviceJob.cancel()
        
        // Cancel all background work
        WorkManager.getInstance(this).cancelAllWorkByTag(WORK_TAG_MONITORING)
    }

    private fun startForegroundService() {
        val notification = createServiceNotification()
        startForeground(AppConstants.FOREGROUND_SERVICE_ID, notification)
    }

    private fun startMonitoring() {
        if (isRunning) return
        
        isRunning = true
        Log.d(TAG, "Starting app monitoring")
        
        serviceScope.launch {
            preferencesManager.setVpnEnabled(true)
            
            // Start VPN service for browser protection
            startVpnService()
            
            // Start continuous monitoring loop
            monitoringLoop()
        }
        
        updateNotification()
    }

    private fun stopMonitoring() {
        if (!isRunning) return
        
        isRunning = false
        Log.d(TAG, "Stopping app monitoring")
        
        serviceScope.launch {
            preferencesManager.setVpnEnabled(false)
            
            // Stop VPN service
            stopVpnService()
        }
        
        updateNotification()
    }

    private suspend fun monitoringLoop() {
        while (isRunning) {
            try {
                // Simulate monitoring activities
                performMonitoringCheck()
                
                // Update stats periodically
                if (scannedContent % 10 == 0) {
                    updateNotification()
                }
                
                // Wait before next check
                delay(AppConstants.MONITORING_INTERVAL_SECONDS * 1000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in monitoring loop", e)
                delay(5000) // Wait longer on error
            }
        }
    }

    private suspend fun performMonitoringCheck() {
        withContext(Dispatchers.IO) {
            // Increment scanned content counter
            scannedContent++
            
            // Simulate threat detection (for demo purposes)
            if (Math.random() < 0.01) { // 1% chance of detecting a threat
                blockedThreats++
                showThreatNotification()
            }
            
            Log.d(TAG, "Monitoring check completed. Scanned: $scannedContent, Blocked: $blockedThreats")
        }
    }

    private fun scheduleBackgroundWork() {
        val workRequest = PeriodicWorkRequestBuilder<AppMonitoringWorker>(
            15, TimeUnit.MINUTES // Minimum interval for periodic work
        )
            .addTag(WORK_TAG_MONITORING)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_SERVICE)
            .setContentTitle("Apper Protection Active")
            .setContentText(getNotificationText())
            .setSmallIcon(R.drawable.ic_security) // We'll create this
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(createStopAction())
            .build()
    }

    private fun createStopAction(): NotificationCompat.Action {
        val stopIntent = Intent(this, ApperMonitoringService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_stop,
            "Stop",
            stopPendingIntent
        ).build()
    }

    private fun getNotificationText(): String {
        return if (isRunning) {
            "Protecting your browsing • $blockedThreats threats blocked"
        } else {
            "Protection paused"
        }
    }

    private fun updateNotification() {
        val notification = createServiceNotification()
        notificationManager.notify(AppConstants.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun showThreatNotification() {
        val notification = NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ALERTS)
            .setContentTitle("Threat Blocked")
            .setContentText("Apper blocked a potentially harmful website")
            .setSmallIcon(R.drawable.ic_warning)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notificationManager.notify(AppConstants.NOTIFICATION_ID_ALERT, notification)
    }

    private fun startVpnService() {
        try {
            Log.d(TAG, "Starting VPN service for browser protection")
            val vpnIntent = Intent(this, ApperVpnService::class.java).apply {
                action = ApperVpnService.ACTION_START_VPN
            }
            startService(vpnIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN service", e)
        }
    }

    private fun stopVpnService() {
        try {
            Log.d(TAG, "Stopping VPN service")
            val vpnIntent = Intent(this, ApperVpnService::class.java).apply {
                action = ApperVpnService.ACTION_STOP_VPN
            }
            startService(vpnIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN service", e)
        }
    }

    // Public methods for interaction
    fun getMonitoringStats(): Pair<Int, Int> {
        return Pair(scannedContent, blockedThreats)
    }

    fun isMonitoringActive(): Boolean {
        return isRunning
    }

    companion object {
        private const val TAG = "ApperMonitoringService"
        private const val WORK_TAG_MONITORING = "app_monitoring"
        
        const val ACTION_START_MONITORING = "com.apper.android.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.apper.android.STOP_MONITORING"
        const val ACTION_UPDATE_STATS = "com.apper.android.UPDATE_STATS"

        fun startService(context: Context) {
            val intent = Intent(context, ApperMonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ApperMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }
} 