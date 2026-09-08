package com.example.voiceapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// High-contrast dark palette for accessibility
private val BaseerDarkColors = darkColorScheme(
    primary = Color(0xFF4DD0E1),         // Cyan/teal accent
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFFB2CCD1),
    onSecondary = Color(0xFF1D3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFCEE8ED),
    tertiary = Color(0xFFFFA726),         // Orange for warnings/highlights
    background = Color(0xFF0D1117),       // Deep dark background
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF161B22),          // Slightly lighter surface
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF1E252E),
    onSurfaceVariant = Color(0xFFC1C7CE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val BaseerTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
)

@Composable
fun BaseerTheme(content: @Composable () -> Unit) {
    val colorScheme = BaseerDarkColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BaseerTypography,
        content = content
    )
}
