package com.apper.android.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apper_preferences")

class PreferencesManager(private val context: Context) {

    // Preference keys
    private val onboardingCompletedKey = booleanPreferencesKey(AppConstants.PREF_ONBOARDING_COMPLETED)
    private val vpnEnabledKey = booleanPreferencesKey(AppConstants.PREF_VPN_ENABLED)
    private val accessibilityEnabledKey = booleanPreferencesKey(AppConstants.PREF_ACCESSIBILITY_ENABLED)
    private val browserProtectionEnabledKey = booleanPreferencesKey(AppConstants.PREF_BROWSER_PROTECTION_ENABLED)
    private val socialMediaProtectionEnabledKey = booleanPreferencesKey(AppConstants.PREF_SOCIAL_MEDIA_PROTECTION_ENABLED)
    private val universalHelpEnabledKey = booleanPreferencesKey(AppConstants.PREF_UNIVERSAL_HELP_ENABLED)

    // Flow-based preference accessors
    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[onboardingCompletedKey] ?: false
    }

    val isVpnEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[vpnEnabledKey] ?: false
    }

    val isAccessibilityEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[accessibilityEnabledKey] ?: false
    }

    val isBrowserProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[browserProtectionEnabledKey] ?: true
    }

    val isSocialMediaProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[socialMediaProtectionEnabledKey] ?: true
    }

    val isUniversalHelpEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[universalHelpEnabledKey] ?: false
    }

    // Preference setters
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }

    suspend fun setVpnEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[vpnEnabledKey] = enabled
        }
    }

    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[accessibilityEnabledKey] = enabled
        }
    }

    suspend fun setBrowserProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[browserProtectionEnabledKey] = enabled
        }
    }

    suspend fun setSocialMediaProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[socialMediaProtectionEnabledKey] = enabled
        }
    }

    suspend fun setUniversalHelpEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[universalHelpEnabledKey] = enabled
        }
    }

    // Reset all preferences (for testing or factory reset)
    suspend fun resetAllPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
} 