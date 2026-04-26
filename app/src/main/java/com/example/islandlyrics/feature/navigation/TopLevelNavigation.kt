package com.example.islandlyrics.feature.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .widthIn(max = 348.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination == destination
                val label = stringResource(destination.labelRes)
                val selectedTextColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "navItemTint"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .clickable { onNavigate(destination) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = label,
                            tint = selectedTextColor
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                expandFrom = Alignment.Start
                            ),
                            exit = fadeOut() + shrinkHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                shrinkTowards = Alignment.Start
                            )
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp),
                                color = selectedTextColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
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
