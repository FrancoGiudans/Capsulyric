package com.example.islandlyrics.ui.miuix.theme

import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.theme.ThemeHelper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
// Remove library-layer blur import and use app-layer one implicitly or explicitly

private const val MIUIX_THEME_COLOR_SOURCE_PREF_KEY = AppPreferences.Keys.MIUIX_THEME_COLOR_SOURCE

/**
 * Miuix-styled app theme wrapper.
 * Supports Monet dynamic color via ThemeController, driven by existing pref keys:
 *   - "theme_dynamic_color" (Boolean) — enables Monet wallpaper color extraction
 *   - "theme_follow_system" (Boolean) — follow system dark mode
 *   - "theme_dark_mode"     (Boolean) — explicit dark override (only when follow_system=false)
 */
@Composable
fun MiuixAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.of(context) }

    var isDynamicColor by remember { mutableStateOf(prefs.getBoolean(AppPreferences.Keys.THEME_DYNAMIC_COLOR, true)) }
    var followSystem by remember { mutableStateOf(prefs.getBoolean(AppPreferences.Keys.THEME_FOLLOW_SYSTEM, true)) }
    var forceDark by remember { mutableStateOf(prefs.getBoolean(AppPreferences.Keys.THEME_DARK_MODE, false)) }
    var cardBlurEnabled by remember { mutableStateOf(prefs.getBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, false)) }
    var customThemeColorArgb by remember { mutableStateOf(prefs.getInt(AppPreferences.Keys.THEME_CUSTOM_COLOR, 0xFF3482FF.toInt())) }
    var customThemeColorSource by remember {
        mutableStateOf(
            prefs.getString(
                MIUIX_THEME_COLOR_SOURCE_PREF_KEY,
                if (prefs.getBoolean(AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT, false)) {
                    ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM
                } else {
                    ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                }
            ) ?: ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
        )
    }
    var customThemeGlobalTintEnabled by remember {
        mutableStateOf(prefs.getBoolean(AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT, false))
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                AppPreferences.Keys.THEME_DYNAMIC_COLOR -> isDynamicColor = prefs.getBoolean(AppPreferences.Keys.THEME_DYNAMIC_COLOR, true)
                AppPreferences.Keys.THEME_FOLLOW_SYSTEM -> followSystem = prefs.getBoolean(AppPreferences.Keys.THEME_FOLLOW_SYSTEM, true)
                AppPreferences.Keys.THEME_DARK_MODE -> forceDark = prefs.getBoolean(AppPreferences.Keys.THEME_DARK_MODE, false)
                AppPreferences.Keys.CARD_BLUR_ENABLED -> cardBlurEnabled = prefs.getBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, false)
                AppPreferences.Keys.THEME_CUSTOM_COLOR -> customThemeColorArgb = prefs.getInt(AppPreferences.Keys.THEME_CUSTOM_COLOR, 0xFF3482FF.toInt())
                MIUIX_THEME_COLOR_SOURCE_PREF_KEY, AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT -> {
                    customThemeColorSource = prefs.getString(
                        MIUIX_THEME_COLOR_SOURCE_PREF_KEY,
                        if (prefs.getBoolean(AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT, false)) {
                            ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM
                        } else {
                            ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                        }
                    ) ?: ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_DEFAULT
                    customThemeGlobalTintEnabled = prefs.getBoolean(AppPreferences.Keys.THEME_CUSTOM_COLOR_GLOBAL_TINT, false)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isDark = if (followSystem) isSystemInDarkTheme() else forceDark
    val controller = rememberIslandLyricsMiuixThemeController(
        dynamicColor = isDynamicColor,
        followSystem = followSystem,
        forceDark = forceDark,
        customThemeColorArgb = customThemeColorArgb,
        customThemeColorSource = customThemeColorSource,
        customThemeGlobalTintEnabled = customThemeGlobalTintEnabled
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(isDark) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    val activity = context as? ComponentActivity
    val navigationEventDispatcher = remember {
        NavigationEventDispatcher { (activity as? Activity)?.finish() }
    }
    DisposableEffect(navigationEventDispatcher, activity) {
        if (activity != null && Build.VERSION.SDK_INT >= 33) {
            val input = OnBackInvokedDefaultInput(activity.onBackInvokedDispatcher)
            navigationEventDispatcher.addInput(input)
            onDispose { navigationEventDispatcher.removeInput(input) }
        } else {
            onDispose { }
        }
    }
    val navigationEventDispatcherOwner = remember(navigationEventDispatcher) {
        object : androidx.navigationevent.NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = navigationEventDispatcher
        }
    }

    val backDispatcherOwner =
        androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current

    androidx.compose.runtime.CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner,
        LocalMiuixBlurEnabled provides cardBlurEnabled
    ) {
        MiuixTheme(controller = controller) {
            if (backDispatcherOwner != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides backDispatcherOwner
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

@Composable
fun rememberIslandLyricsMiuixThemeController(
    dynamicColor: Boolean,
    followSystem: Boolean,
    forceDark: Boolean,
    customThemeColorArgb: Int,
    customThemeColorSource: String,
    customThemeGlobalTintEnabled: Boolean
): ThemeController {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = if (followSystem) isSystemDark else forceDark
    val useCustomThemeColor =
        customThemeColorSource == ThemeHelper.MATERIAL_THEME_COLOR_SOURCE_CUSTOM && !dynamicColor
    val useCustomThemeGlobalTint = useCustomThemeColor && customThemeGlobalTintEnabled

    val colorSchemeMode = when {
        dynamicColor && followSystem -> ColorSchemeMode.MonetSystem
        dynamicColor && !isDark -> ColorSchemeMode.MonetLight
        dynamicColor -> ColorSchemeMode.MonetDark
        useCustomThemeGlobalTint && followSystem -> ColorSchemeMode.MonetSystem
        useCustomThemeGlobalTint && !isDark -> ColorSchemeMode.MonetLight
        useCustomThemeGlobalTint -> ColorSchemeMode.MonetDark
        followSystem -> ColorSchemeMode.System
        forceDark -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.Light
    }

    val customThemeColor = remember(customThemeColorArgb) { Color(customThemeColorArgb) }
    val themeLightColors = remember(customThemeColorArgb, useCustomThemeColor) {
        if (useCustomThemeColor) buildCustomLightColors(customThemeColor) else lightColorScheme()
    }
    val themeDarkColors = remember(customThemeColorArgb, useCustomThemeColor) {
        if (useCustomThemeColor) buildCustomDarkColors(customThemeColor) else darkColorScheme()
    }

    return remember(
        colorSchemeMode,
        customThemeColorArgb,
        dynamicColor,
        customThemeColorSource,
        customThemeGlobalTintEnabled
    ) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            lightColors = themeLightColors,
            darkColors = themeDarkColors,
            keyColor = if (useCustomThemeGlobalTint) customThemeColor else null,
            paletteStyle = ThemePaletteStyle.TonalSpot
        )
    }
}

/**
 * Helper to check if miuix UI mode is enabled.
 */
fun isMiuixEnabled(context: Context): Boolean {
    return AppPreferences.of(context)
        .getBoolean(AppPreferences.Keys.UI_USE_MIUIX, true)
}

private fun buildCustomLightColors(seed: Color): Colors {
    val base = lightColorScheme()
    val container = lerp(seed, Color.White, 0.18f)
    val tertiary = lerp(seed, Color.White, 0.86f)
    val disabled = lerp(seed, Color.White, 0.72f)
    return base.copy(
        primary = seed,
        primaryVariant = seed,
        onPrimaryVariant = container,
        disabledPrimary = disabled,
        disabledPrimaryButton = disabled,
        disabledPrimarySlider = lerp(seed, Color.White, 0.58f),
        primaryContainer = container,
        onPrimaryContainer = Color.White,
        tertiaryContainer = tertiary,
        onTertiaryContainer = seed,
        tertiaryContainerVariant = tertiary,
        sliderKeyPointForeground = seed
    )
}

private fun buildCustomDarkColors(seed: Color): Colors {
    val base = darkColorScheme()
    val container = lerp(seed, Color.Black, 0.2f)
    val tertiary = lerp(seed, base.surface, 0.78f)
    val disabled = lerp(seed, base.surface, 0.68f)
    return base.copy(
        primary = seed,
        primaryVariant = lerp(seed, Color.Black, 0.12f),
        onPrimaryVariant = lerp(seed, Color.White, 0.48f),
        disabledPrimary = disabled,
        disabledPrimaryButton = disabled,
        disabledPrimarySlider = lerp(seed, base.surface, 0.52f),
        primaryContainer = container,
        onPrimaryContainer = Color.White,
        tertiaryContainer = tertiary,
        onTertiaryContainer = seed,
        tertiaryContainerVariant = tertiary,
        sliderKeyPointForeground = seed
    )
}


