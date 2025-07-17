package com.apper.android.ui.viewmodels

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apper.android.ApperApplication
import com.apper.android.core.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionState(
    val vpnGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false
)

data class AppState(
    val isLoading: Boolean = false,
    val isServiceRunning: Boolean = false,
    val permissions: PermissionState = PermissionState(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context: Context = application.applicationContext
    private val preferencesManager = (application as ApperApplication).preferencesManager
    private val permissionChecker = PermissionChecker(context)
    
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    init {
        checkAllPermissions()
    }
    
    fun checkAllPermissions() {
        viewModelScope.launch {
            val currentState = _appState.value
            val newPermissions = PermissionState(
                vpnGranted = permissionChecker.isVpnPermissionGranted(),
                accessibilityGranted = permissionChecker.isAccessibilityServiceEnabled(),
                overlayGranted = permissionChecker.isOverlayPermissionGranted(),
                notificationsGranted = permissionChecker.isNotificationPermissionGranted(),
                batteryOptimizationDisabled = permissionChecker.isBatteryOptimizationDisabled()
            )
            
            _appState.value = currentState.copy(permissions = newPermissions)
        }
    }
    
    fun onVpnPermissionGranted() {
        viewModelScope.launch {
            preferencesManager.setVpnEnabled(true)
            checkAllPermissions()
        }
    }
    
    fun onOverlayPermissionGranted() {
        checkAllPermissions()
    }
    
    fun checkAccessibilityPermission() {
        viewModelScope.launch {
            val isEnabled = permissionChecker.isAccessibilityServiceEnabled()
            preferencesManager.setAccessibilityEnabled(isEnabled)
            checkAllPermissions()
        }
    }
    
    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesManager.setOnboardingCompleted(true)
        }
    }
    
    fun setServiceRunning(running: Boolean) {
        val currentState = _appState.value
        _appState.value = currentState.copy(isServiceRunning = running)
    }
    
    fun setLoading(loading: Boolean) {
        val currentState = _appState.value
        _appState.value = currentState.copy(isLoading = loading)
    }
    
    fun setError(error: String?) {
        val currentState = _appState.value
        _appState.value = currentState.copy(error = error)
    }
    
    fun areAllCriticalPermissionsGranted(): Boolean {
        val permissions = _appState.value.permissions
        return permissions.vpnGranted && 
               permissions.accessibilityGranted && 
               permissions.overlayGranted &&
               permissions.notificationsGranted
    }
} 