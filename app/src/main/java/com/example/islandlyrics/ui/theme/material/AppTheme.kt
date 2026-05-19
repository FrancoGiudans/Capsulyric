package com.example.islandlyrics.ui.theme.material

import android.app.Activity
import android.content.Context
import com.example.islandlyrics.ui.common.BaseActivity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Monet-derived palette from seed #3482FF (H≈220°, C≈80 in HCT).
// Primary palette: H=220, C=40 — toned down from the vivid Miuix blue.
val MaterialPrimaryLight = Color(0xFF005FAF)   // T40
val MaterialPrimaryDark  = Color(0xFFAAC7FF)   // T80

// Neutral surface tones — slightly blue-tinted (H=220, C=4), characteristic of Monet.
val MaterialPageBackgroundLight = Color(0xFFF8F9FF)  // N98
val MaterialPageBackgroundDark  = Color(0xFF111318)  // N6
val MaterialPageSurfaceLight    = Color(0xFFF8F9FF)  // N98
val MaterialPageSurfaceDark     = Color(0xFF111318)  // N6
val MaterialPageSurfaceVariantLight = Color(0xFFDFE2EB)  // NV90
val MaterialPageSurfaceVariantDark  = Color(0xFF43474E)  // NV30

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun IslandLyricsMaterialTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("theme_pure_black", false)) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", false)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "theme_follow_system" -> followSystem = prefs.getBoolean("theme_follow_system", true)
                "theme_dark_mode" -> darkMode = prefs.getBoolean("theme_dark_mode", false)
                "theme_pure_black" -> pureBlack = prefs.getBoolean("theme_pure_black", false)
                "theme_dynamic_color" -> dynamicColor = prefs.getBoolean("theme_dynamic_color", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = if (followSystem) isSystemDark else darkMode

    AppTheme(
        darkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack && useDarkTheme,
        content = content
    )
}

// MD3 shape scale
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// MD3 type scale — explicit so it's easy to customise later
val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// Default fallback colors — full Monet tonal palette from seed #3482FF.
// Primary:   H=220, C=40  |  Secondary: H=220, C=16
// Tertiary:  H=280, C=24  |  Neutral:   H=220, C=4   |  NeutralVariant: H=220, C=8
private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFFAAC7FF),  // P-80
    onPrimary            = Color(0xFF003063),  // P-20
    primaryContainer     = Color(0xFF004787),  // P-30
    onPrimaryContainer   = Color(0xFFD6E3FF),  // P-90
    secondary            = Color(0xFFBBC7DB),  // S-80
    onSecondary          = Color(0xFF253140),  // S-20
    secondaryContainer   = Color(0xFF3C4858),  // S-30
    onSecondaryContainer = Color(0xFFD8E3F8),  // S-90
    tertiary             = Color(0xFFD6BEE4),  // T-80
    onTertiary           = Color(0xFF3B2948),  // T-20
    tertiaryContainer    = Color(0xFF523F5F),  // T-30
    onTertiaryContainer  = Color(0xFFF2DAFF),  // T-90
    error                = Color(0xFFF2B8B5),
    onError              = Color(0xFF601410),
    errorContainer       = Color(0xFF8C1D18),
    onErrorContainer     = Color(0xFFF9DEDC),
    background           = Color(0xFF111318),  // N-6
    onBackground         = Color(0xFFE1E2E9),  // N-90
    surface              = Color(0xFF111318),  // N-6
    onSurface            = Color(0xFFE1E2E9),  // N-90
    surfaceVariant       = Color(0xFF43474E),  // NV-30
    onSurfaceVariant     = Color(0xFFC3C7CF),  // NV-80
    outline              = Color(0xFF8D9199),  // NV-60
    outlineVariant       = Color(0xFF43474E),  // NV-30
    scrim                = Color(0xFF000000),
    inverseSurface       = Color(0xFFE1E2E9),  // N-90
    inverseOnSurface     = Color(0xFF191C20),  // N-10
    inversePrimary       = Color(0xFF005FAF),  // P-40
    surfaceDim           = Color(0xFF111318),  // N-6
    surfaceBright        = Color(0xFF383A3F),  // N-24
    surfaceContainerLowest  = Color(0xFF0C0E13),  // N-4
    surfaceContainerLow     = Color(0xFF191C20),  // N-10
    surfaceContainer        = Color(0xFF1D2024),  // N-12
    surfaceContainerHigh    = Color(0xFF282A2F),  // N-17
    surfaceContainerHighest = Color(0xFF33353A),  // N-22
)

private val LightColorScheme = lightColorScheme(
    primary              = Color(0xFF005FAF),  // P-40
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFD6E3FF),  // P-90
    onPrimaryContainer   = Color(0xFF001B3E),  // P-10
    secondary            = Color(0xFF545F71),  // S-40
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFD8E3F8),  // S-90
    onSecondaryContainer = Color(0xFF111C2B),  // S-10
    tertiary             = Color(0xFF6B5778),  // T-40
    onTertiary           = Color(0xFFFFFFFF),
    tertiaryContainer    = Color(0xFFF2DAFF),  // T-90
    onTertiaryContainer  = Color(0xFF251431),  // T-10
    error                = Color(0xFFB3261E),
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFFF9DEDC),
    onErrorContainer     = Color(0xFF410E0B),
    background           = Color(0xFFF8F9FF),  // N-98
    onBackground         = Color(0xFF191C20),  // N-10
    surface              = Color(0xFFF8F9FF),  // N-98
    onSurface            = Color(0xFF191C20),  // N-10
    surfaceVariant       = Color(0xFFDFE2EB),  // NV-90
    onSurfaceVariant     = Color(0xFF43474E),  // NV-30
    outline              = Color(0xFF73777F),  // NV-50
    outlineVariant       = Color(0xFFC3C7CF),  // NV-80
    scrim                = Color(0xFF000000),
    inverseSurface       = Color(0xFF2E3036),  // N-17
    inverseOnSurface     = Color(0xFFEFF0F7),  // N-95
    inversePrimary       = Color(0xFFAAC7FF),  // P-80
    surfaceDim           = Color(0xFFD9DADF),  // N-87
    surfaceBright        = Color(0xFFF8F9FF),  // N-98
    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF2F3FA),  // N-96
    surfaceContainer        = Color(0xFFECEDF4),  // N-94
    surfaceContainerHigh    = Color(0xFFE6E8EF),  // N-92
    surfaceContainerHighest = Color(0xFFE1E2E9),  // N-90
)

private val PureBlackColorScheme = darkColorScheme(
    primary              = Color(0xFFAAC7FF),  // P-80
    onPrimary            = Color(0xFF003063),
    primaryContainer     = Color(0xFF004787),
    onPrimaryContainer   = Color(0xFFD6E3FF),
    secondary            = Color(0xFF505868),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFF3C4858),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary             = Color(0xFFD6BEE4),
    onTertiary           = Color(0xFF3B2948),
    tertiaryContainer    = Color(0xFF523F5F),
    onTertiaryContainer  = Color(0xFFF2DAFF),
    background           = Color.Black,
    onBackground         = Color.White,
    surface              = Color.Black,
    onSurface            = Color(0xFFE1E2E9),
    surfaceVariant       = Color(0xFF242424),
    onSurfaceVariant     = Color(0xCCFFFFFF),
    outline              = Color(0xFF404040),
    outlineVariant       = Color(0xFF393939),
    scrim                = Color(0xFF000000),
    inverseSurface       = Color(0xFFE1E2E9),
    inverseOnSurface     = Color(0xFF191C20),
    inversePrimary       = Color(0xFF005FAF),
    surfaceDim           = Color.Black,
    surfaceBright        = Color(0xFF2D2D2D),
    surfaceContainerLowest  = Color.Black,
    surfaceContainerLow     = Color(0xFF1A1A1A),
    surfaceContainer        = Color(0xFF242424),
    surfaceContainerHigh    = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
)
