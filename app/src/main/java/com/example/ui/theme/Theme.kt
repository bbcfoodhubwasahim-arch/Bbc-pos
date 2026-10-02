package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// BBC FOOD HUB - HIGH-CONTRAST LIGHT COLOR SCHEME
private val PosLightColorScheme = lightColorScheme(
    primary = WarmAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = FoodHubCharcoal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = TextPrimary,
    tertiary = DeepAmber,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = CrispWhite,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CrispCardBorder,
    outlineVariant = Color(0xFFCBD5E1),
    error = ErrorRed,
    onError = Color.White
)

// BBC FOOD HUB - HIGH-CONTRAST DARK COLOR SCHEME
private val PosDarkColorScheme = darkColorScheme(
    primary = SaffronGold,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = TextPrimaryDark,
    tertiary = SaffronGold,
    onTertiary = Color(0xFF0F172A),
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkCardBorder,
    outlineVariant = Color(0xFF64748B),
    error = ErrorRed,
    onError = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun posTopAppBarColors(isDark: Boolean = false) = TopAppBarDefaults.topAppBarColors(
    containerColor = if (isDark) Color(0xFF0F172A) else FoodHubCharcoal,
    titleContentColor = Color.White,
    actionIconContentColor = SaffronGold,
    navigationIconContentColor = SaffronGold
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Always default to false
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    // FORCE LIGHT MODE (Always use PosLightColorScheme for 100% crisp visibility)
    val colorScheme = PosLightColorScheme

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Ensure system status bar and navigation bar have dark icons for high contrast on light background
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


