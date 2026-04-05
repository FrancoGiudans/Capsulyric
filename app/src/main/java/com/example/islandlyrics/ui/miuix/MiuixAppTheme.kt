package com.example.islandlyrics.ui.miuix

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
// Remove library-layer blur import and use app-layer one implicitly or explicitly

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
    val prefs = remember { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }

    var isDynamicColor by remember { mutableStateOf(prefs.getBoolean("theme_dynamic_color", true)) }
    var followSystem by remember { mutableStateOf(prefs.getBoolean("theme_follow_system", true)) }
    var forceDark by remember { mutableStateOf(prefs.getBoolean("theme_dark_mode", false)) }
    var cardBlurEnabled by remember { mutableStateOf(prefs.getBoolean("card_blur_enabled", false)) }
    val isSystemDark    = isSystemInDarkTheme()

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "theme_dynamic_color" -> isDynamicColor = prefs.getBoolean("theme_dynamic_color", true)
                "theme_follow_system" -> followSystem = prefs.getBoolean("theme_follow_system", true)
                "theme_dark_mode" -> forceDark = prefs.getBoolean("theme_dark_mode", false)
                "card_blur_enabled" -> cardBlurEnabled = prefs.getBoolean("card_blur_enabled", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Determine whether we should be in dark mode right now
    val isDark = if (followSystem) isSystemDark else forceDark

    // Pick the appropriate ColorSchemeMode
    val colorSchemeMode = when {
        isDynamicColor && followSystem  -> ColorSchemeMode.MonetSystem
        isDynamicColor && !isDark       -> ColorSchemeMode.MonetLight
        isDynamicColor                  -> ColorSchemeMode.MonetDark
        followSystem                    -> ColorSchemeMode.System
        forceDark                       -> ColorSchemeMode.Dark
        else                            -> ColorSchemeMode.Light
    }

    val controller = remember(colorSchemeMode) { ThemeController(colorSchemeMode) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(isDark) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    val navigationEventDispatcher = remember { NavigationEventDispatcher() }
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

/**
 * Helper to check if miuix UI mode is enabled.
 */
fun isMiuixEnabled(context: Context): Boolean {
    return context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        .getBoolean("ui_use_miuix", false)
}
