package com.example.islandlyrics

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Fix: Respect Pure Black even in Dynamic Color mode (Dark Theme only)
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) {
                if (pureBlack) dynamicDarkColorScheme(context).copy(background = Color.Black, surface = Color.Black)
                else dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> if (pureBlack) PureBlackColorScheme else DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Use LaunchedEffect to run only when theme changes, preventing jitter on every recomposition
        LaunchedEffect(darkTheme, colorScheme) {
            val window = (view.context as Activity).window
            
            // Note: Edge-to-Edge is now handled in BaseActivity.onCreate to prevent jitter.
            
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// Default Fallback Colors (Strict Neutral Grays)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9E9E9E),      // Grey 500 (matches XML colorPrimary)
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF212121),
    onPrimaryContainer = Color(0xFFBDBDBD),
    secondary = Color(0xFF757575),    // Grey 600
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF212121),
    onSecondaryContainer = Color(0xFFBDBDBD),
    tertiary = Color(0xFF616161),     // Grey 700
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF212121),
    onTertiaryContainer = Color(0xFFBDBDBD),
    background = Color(0xFF121212),   // Material Dark
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFEEEEEE),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFF616161),
    scrim = Color(0x99000000),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = Color(0xFF424242),
    surfaceDim = Color(0xFF121212),
    surfaceBright = Color(0xFF424242),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1E1E1E),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF383838),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF424242),      // Grey 800
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF121212),
    secondary = Color(0xFF616161),    // Grey 700
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color(0xFF121212),
    tertiary = Color(0xFF757575),     // Grey 600
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5F5F5),
    onTertiaryContainer = Color(0xFF121212),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0x99000000),
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFE0E0E0),
    surfaceDim = Color(0xFFF5F5F5),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEEEEEE),
    surfaceContainerHigh = Color(0xFFE0E0E0),
    surfaceContainerHighest = Color(0xFFD6D6D6),
)

private val PureBlackColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),      // Grey 300
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFFBDBDBD),    // Grey 400
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF424242),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),     // Grey 500
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF424242),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFF424242),
    scrim = Color(0x99000000),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = Color(0xFF424242),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF424242),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1E1E1E),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF383838),
)
