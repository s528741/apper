package com.apper.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Apper brand colors
val ApperPrimary = Color(0xFF1976D2)
val ApperPrimaryVariant = Color(0xFF1565C0)
val ApperSecondary = Color(0xFF03DAC6)
val ApperSecondaryVariant = Color(0xFF018786)
val ApperBackground = Color(0xFFF5F5F5)
val ApperSurface = Color(0xFFFFFFFF)
val ApperError = Color(0xFFB00020)
val ApperOnPrimary = Color(0xFFFFFFFF)
val ApperOnSecondary = Color(0xFF000000)
val ApperOnBackground = Color(0xFF000000)
val ApperOnSurface = Color(0xFF000000)
val ApperOnError = Color(0xFFFFFFFF)

// Dark theme colors
val ApperDarkPrimary = Color(0xFF90CAF9)
val ApperDarkPrimaryVariant = Color(0xFF42A5F5)
val ApperDarkSecondary = Color(0xFF03DAC6)
val ApperDarkBackground = Color(0xFF121212)
val ApperDarkSurface = Color(0xFF1E1E1E)
val ApperDarkOnPrimary = Color(0xFF000000)
val ApperDarkOnSecondary = Color(0xFF000000)
val ApperDarkOnBackground = Color(0xFFFFFFFF)
val ApperDarkOnSurface = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = ApperPrimary,
    onPrimary = ApperOnPrimary,
    primaryContainer = ApperPrimaryVariant,
    onPrimaryContainer = ApperOnPrimary,
    secondary = ApperSecondary,
    onSecondary = ApperOnSecondary,
    secondaryContainer = ApperSecondaryVariant,
    onSecondaryContainer = ApperOnSecondary,
    tertiary = ApperSecondary,
    onTertiary = ApperOnSecondary,
    error = ApperError,
    onError = ApperOnError,
    errorContainer = ApperError,
    onErrorContainer = ApperOnError,
    background = ApperBackground,
    onBackground = ApperOnBackground,
    surface = ApperSurface,
    onSurface = ApperOnSurface,
    surfaceVariant = ApperBackground,
    onSurfaceVariant = ApperOnSurface,
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    inverseSurface = ApperDarkSurface,
    inverseOnSurface = ApperDarkOnSurface,
    inversePrimary = ApperDarkPrimary,
    surfaceDim = Color(0xFFDDD7E0),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF1ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9)
)

private val DarkColorScheme = darkColorScheme(
    primary = ApperDarkPrimary,
    onPrimary = ApperDarkOnPrimary,
    primaryContainer = ApperDarkPrimaryVariant,
    onPrimaryContainer = ApperDarkOnPrimary,
    secondary = ApperDarkSecondary,
    onSecondary = ApperDarkOnSecondary,
    secondaryContainer = ApperSecondaryVariant,
    onSecondaryContainer = ApperDarkOnSecondary,
    tertiary = ApperDarkSecondary,
    onTertiary = ApperDarkOnSecondary,
    error = ApperError,
    onError = ApperOnError,
    errorContainer = ApperError,
    onErrorContainer = ApperOnError,
    background = ApperDarkBackground,
    onBackground = ApperDarkOnBackground,
    surface = ApperDarkSurface,
    onSurface = ApperDarkOnSurface,
    surfaceVariant = ApperDarkBackground,
    onSurfaceVariant = ApperDarkOnSurface,
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    inverseSurface = ApperSurface,
    inverseOnSurface = ApperOnSurface,
    inversePrimary = ApperPrimary,
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B)
)

@Composable
fun ApperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
} 