package com.example.islandlyrics.ui.theme.material

import android.app.Activity
import com.example.islandlyrics.ui.common.BaseActivity
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

val MaterialPrimaryLight = Color(0xFF3482FF)
val MaterialPrimaryDark = Color(0xFF277AF7)
val MaterialPageBackgroundLight = Color(0xFFF7F7F7)
val MaterialPageBackgroundDark = Color(0xFF242424)
val MaterialPageSurfaceLight = Color(0xFFFFFFFF)
val MaterialPageSurfaceDark = Color(0xFF000000)
val MaterialPageSurfaceVariantLight = Color(0xFFFFFFFF)
val MaterialPageSurfaceVariantDark = Color(0xFF242424)

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

// Default fallback colors aligned with the static Miuix palette.
private val DarkColorScheme = darkColorScheme(
    primary = MaterialPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF338FE4),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF505050),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF434343),
    onSecondaryContainer = Color(0xFFD9D9D9),
    tertiary = Color(0xFF4788FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2B3B54),
    onTertiaryContainer = Color(0xFF4788FF),
    background = MaterialPageBackgroundDark,
    onBackground = Color(0xFFE6FFFFFF),
    surface = MaterialPageSurfaceDark,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = MaterialPageSurfaceVariantDark,
    onSurfaceVariant = Color(0xCCFFFFFF),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF393939),
    scrim = Color(0x99000000),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = MaterialPrimaryLight,
    surfaceDim = MaterialPageBackgroundDark,
    surfaceBright = Color(0xFF2D2D2D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
)

private val LightColorScheme = lightColorScheme(
    primary = MaterialPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5D9BFF),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF303030),
    tertiary = MaterialPrimaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAF2FF),
    onTertiaryContainer = MaterialPrimaryLight,
    background = MaterialPageBackgroundLight,
    onBackground = Color.Black,
    surface = MaterialPageSurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = MaterialPageSurfaceVariantLight,
    onSurfaceVariant = Color(0x99000000),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0x99000000),
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    inversePrimary = MaterialPrimaryDark,
    surfaceDim = MaterialPageBackgroundLight,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F0F0),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE8E8E8),
)

private val PureBlackColorScheme = darkColorScheme(
    primary = MaterialPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF338FE4),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF505050),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF434343),
    onSecondaryContainer = Color(0xFFD9D9D9),
    tertiary = Color(0xFF4788FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2B3B54),
    onTertiaryContainer = Color(0xFF4788FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xCCFFFFFF),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF393939),
    scrim = Color(0x99000000),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = MaterialPrimaryLight,
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF2D2D2D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
)
