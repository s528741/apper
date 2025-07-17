package com.apper.android.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apper.android.ApperApplication
import com.apper.android.core.AppConstants
import com.apper.android.services.ApperMonitoringService
import com.apper.android.ui.onboarding.OnboardingScreen
import com.apper.android.ui.home.HomeScreen
import com.apper.android.ui.theme.ApperTheme
import com.apper.android.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // VPN permission granted
            mainViewModel?.onVpnPermissionGranted()
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Settings.canDrawOverlays(this)) {
            // Overlay permission granted
            mainViewModel?.onOverlayPermissionGranted()
        }
    }
    
    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if accessibility service is enabled
        mainViewModel?.checkAccessibilityPermission()
    }
    
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ApperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ApperApp()
                }
            }
        }
    }

    @Composable
    private fun ApperApp() {
        val navController = rememberNavController()
        val viewModel: MainViewModel = viewModel()
        mainViewModel = viewModel
        
        val preferencesManager = (application as ApperApplication).preferencesManager
        val isOnboardingCompleted by preferencesManager.isOnboardingCompleted.collectAsState(initial = false)
        
        val startDestination = if (isOnboardingCompleted) "home" else "onboarding"
        
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                        startMonitoringService()
                    },
                    onRequestVpnPermission = { requestVpnPermission() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                    viewModel = viewModel
                )
            }
            
            composable("home") {
                HomeScreen(
                    onSettingsClick = { /* Navigate to settings */ },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            // VPN permission already granted
            mainViewModel?.onVpnPermissionGranted()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityPermissionLauncher.launch(intent)
    }

    private fun startMonitoringService() {
        val intent = Intent(this, ApperMonitoringService::class.java)
        startForegroundService(intent)
    }

    override fun onResume() {
        super.onResume()
        // Check permissions when app resumes
        mainViewModel?.checkAllPermissions()
    }
} 