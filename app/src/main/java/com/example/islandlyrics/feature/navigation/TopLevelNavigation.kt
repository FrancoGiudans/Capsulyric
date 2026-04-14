package com.example.islandlyrics.feature.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.parserrule.ParserRuleActivity
import com.example.islandlyrics.feature.settings.SettingsActivity
import com.example.islandlyrics.ui.miuix.LocalMiuixBlurBackdrop
import com.example.islandlyrics.ui.miuix.LocalMiuixBlurEnabled
import com.example.islandlyrics.ui.miuix.miuixSurfaceBlur
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class TopLevelDestination(
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    PARSER_RULES(R.string.tab_rules, Icons.AutoMirrored.Filled.FormatListBulleted),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

fun Context.createTopLevelIntent(destination: TopLevelDestination): Intent {
    val target = when (destination) {
        TopLevelDestination.HOME -> MainActivity::class.java
        TopLevelDestination.PARSER_RULES -> ParserRuleActivity::class.java
        TopLevelDestination.SETTINGS -> SettingsActivity::class.java
    }
    return Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

fun Activity.navigateToTopLevel(destination: TopLevelDestination) {
    startActivity(createTopLevelIntent(destination))
}

@Composable
fun MaterialTopLevelNavigationBar(
    currentDestination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar(
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                alwaysShowLabel = false,
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes)
                    )
                },
                label = {
                    androidx.compose.material3.Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
fun MiuixTopLevelFloatingNavigationBar(
    currentDestination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val shouldUseBlur = blurEnabled && backdrop != null
    val navShape = RoundedCornerShape(28.dp)
    val navColor = MiuixTheme.colorScheme.surface
    val navModifier = Modifier.miuixSurfaceBlur(
        enabled = shouldUseBlur,
        backdrop = backdrop,
        shape = navShape,
        fallbackColor = navColor
    )

    FloatingNavigationBar(
        modifier = navModifier,
        color = if (shouldUseBlur) Color.Transparent else navColor,
        cornerRadius = 28.dp,
        horizontalOutSidePadding = 24.dp,
        mode = FloatingNavigationBarDisplayMode.IconOnly
    ) {
        TopLevelDestination.entries.forEach { destination ->
            FloatingNavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                icon = destination.icon,
                label = stringResource(destination.labelRes)
            )
        }
    }
}
