package com.apper.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.apper.android.ApperApplication
import com.apper.android.services.ApperMonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootReceiver received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                handleBootCompleted(context)
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Device boot completed, checking if monitoring should be restarted")

        val preferencesManager = (context.applicationContext as ApperApplication).preferencesManager

        // Use coroutine scope to read preferences
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isOnboardingCompleted = preferencesManager.isOnboardingCompleted.first()
                val isVpnEnabled = preferencesManager.isVpnEnabled.first()

                Log.d(TAG, "Onboarding completed: $isOnboardingCompleted, VPN enabled: $isVpnEnabled")

                if (isOnboardingCompleted && isVpnEnabled) {
                    Log.d(TAG, "Starting monitoring service after boot")
                    restartMonitoringService(context)
                } else {
                    Log.d(TAG, "Monitoring service not started - user hasn't completed setup or disabled service")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error reading preferences on boot", e)
            }
        }
    }

    private fun restartMonitoringService(context: Context) {
        try {
            ApperMonitoringService.startService(context)
            Log.d(TAG, "Monitoring service restart initiated")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart monitoring service", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
} 