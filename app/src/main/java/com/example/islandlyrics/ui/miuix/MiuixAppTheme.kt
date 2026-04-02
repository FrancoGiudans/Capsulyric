package com.example.islandlyrics.ui.miuix

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

    val isDynamicColor = remember { prefs.getBoolean("theme_dynamic_color", true) }
    val followSystem    = remember { prefs.getBoolean("theme_follow_system", true) }
    val forceDark       = remember { prefs.getBoolean("theme_dark_mode", false) }
    val cardBlurEnabled = remember { prefs.getBoolean("card_blur_enabled", false) }
    val isSystemDark    = isSystemInDarkTheme()

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
