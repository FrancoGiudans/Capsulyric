/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.feature.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted as MaterialFormatListBulleted
import androidx.compose.material.icons.filled.Home as MaterialHome
import androidx.compose.material.icons.filled.Settings as MaterialSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.parserrule.ParserRuleActivity
import com.example.islandlyrics.feature.settings.SettingsActivity
import com.example.islandlyrics.ui.material.blur.LocalMaterialBlurEnabled
import com.example.islandlyrics.ui.material.blur.LocalMaterialBlurRadius
import com.example.islandlyrics.ui.material.blur.materialBlurPanel
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurBackdrop
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurNavigationBar
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop as liquidLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight as LiquidHighlight
import com.kyant.backdrop.shadow.InnerShadow as LiquidInnerShadow
import com.kyant.backdrop.shadow.Shadow as LiquidShadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class TopLevelDestination(
    val labelRes: Int,
    val materialIcon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Filled.MaterialHome),
    PARSER_RULES(R.string.tab_rules, Icons.AutoMirrored.Filled.MaterialFormatListBulleted),
    SETTINGS(R.string.tab_settings, Icons.Filled.MaterialSettings),
}

private fun TopLevelDestination.miuixIcon(selected: Boolean): ImageVector {
    return when (this) {
        TopLevelDestination.HOME -> if (selected) MiuixIcons.Demibold.Home else MiuixIcons.Home
        TopLevelDestination.PARSER_RULES -> if (selected) MiuixIcons.Demibold.File else MiuixIcons.File
        TopLevelDestination.SETTINGS -> if (selected) MiuixIcons.Demibold.Settings else MiuixIcons.Settings
    }
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
    val blurEnabled = LocalMaterialBlurEnabled.current
    val blurRadius = LocalMaterialBlurRadius.current
    val navShape = RoundedCornerShape(26.dp)
    val navColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val navFallbackColor = navColor.copy(alpha = if (blurEnabled) 0.82f else 1f)
    val navTint = navColor.copy(alpha = 0.46f)

    Box(
        modifier = Modifier
            .width(300.dp)
            .shadow(elevation = 6.dp, shape = navShape, clip = false)
            .clip(navShape)
            .background(navFallbackColor)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .materialBlurPanel(
                    shape = navShape,
                    radius = blurRadius,
                    tint = navTint,
                    backgroundColor = Color.Transparent,
                )
        )

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
                val itemTint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
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
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
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
                            imageVector = destination.materialIcon,
                            contentDescription = label,
                            tint = itemTint
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
                                color = itemTint,
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
    val navColor = MiuixTheme.colorScheme.surfaceContainer
    val isDark = navColor.luminance() < 0.5f
    val floatingHighlight = remember(isDark) {
        if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
    }
    val navModifier = if (shouldUseBlur) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = navShape,
            blurRadius = 25f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = navColor.copy(alpha = 0.6f))
                )
            ),
            highlight = floatingHighlight
        )
    } else {
        Modifier
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val barWidth = maxWidth * 0.69f
        val totalHorizontalPadding = 24.dp
        val totalItemSpacing = 24.dp
        val itemWidth = (barWidth - totalHorizontalPadding - totalItemSpacing) / TopLevelDestination.entries.size

        FloatingNavigationBar(
            modifier = navModifier.width(barWidth),
            color = if (shouldUseBlur) Color.Transparent else navColor,
            cornerRadius = 28.dp,
            horizontalOutSidePadding = 24.dp,
            shadowElevation = 0.dp
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination == destination
                FloatingNavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination) },
                    icon = destination.miuixIcon(selected),
                    label = stringResource(destination.labelRes),
                    modifier = Modifier.width(itemWidth)
                )
            }
        }
    }
}

@Composable
fun MiuixTopLevelLiquidGlassNavigationBar(
    currentDestination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val destinations = TopLevelDestination.entries
    val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)
    val latestSelectedIndex = rememberUpdatedState(selectedIndex)
    val latestOnNavigate = rememberUpdatedState(onNavigate)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val navigationInteractionSource = remember { MutableInteractionSource() }
    val isNavigationItemPressed by navigationInteractionSource.collectIsPressedAsState()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val selectedPosition = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var dragTargetPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragStartIndex by remember { mutableStateOf(selectedIndex) }
    var dragNavigationIndex by remember { mutableStateOf(selectedIndex) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedIndex) {
        if (!isDragging) {
            dragTargetPosition = selectedIndex.toFloat()
            dragStartIndex = selectedIndex
            dragNavigationIndex = selectedIndex
            selectedPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val barWidth = (maxWidth * 0.76f).coerceIn(280.dp, 360.dp)
        val containerShape = RoundedCornerShape(32.dp)
        val indicatorShape = RoundedCornerShape(26.dp)
        val surfaceColor = MiuixTheme.colorScheme.surfaceContainer
        val containerColor = surfaceColor.copy(alpha = 0.4f)
        val accentColor = MiuixTheme.colorScheme.primary
        val inactiveColor = MiuixTheme.colorScheme.onSurfaceVariantActions
        val indicatorOverlayColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        val indicatorPressedOverlayColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.03f)

        BoxWithConstraints(
            modifier = Modifier
                .width(barWidth)
                .height(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val horizontalPadding = 4.dp
            val tabWidth = (maxWidth - horizontalPadding * 2) / destinations.size
            val tabWidthPx = with(density) { tabWidth.toPx() }
            val maxIndex = destinations.lastIndex.toFloat()
            val elasticPosition = liquidGlassElasticPosition(selectedPosition.value, maxIndex)
            val dragFraction = if (constraints.maxWidth > 0) {
                (abs(dragDistancePx) / constraints.maxWidth).coerceIn(0f, 1f)
            } else {
                0f
            }
            val targetPanelOffsetPx = with(density) { 4.dp.toPx() } *
                    dragDistancePx.sign * EaseOut.transform(dragFraction)
            val panelOffsetPx by animateFloatAsState(
                targetValue = if (isDragging) targetPanelOffsetPx else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 300f,
                    visibilityThreshold = 0.5f
                ),
                label = "liquidNavigationPanelOffset"
            )
            val pressProgress by animateFloatAsState(
                targetValue = if (isDragging || isNavigationItemPressed) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 1000f,
                    visibilityThreshold = 0.001f
                ),
                label = "liquidNavigationPressProgress"
            )
            val indicatorScaleX by animateFloatAsState(
                targetValue = 1f + pressProgress * (0.16f + dragFraction * 0.06f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 700f,
                    visibilityThreshold = 0.001f
                ),
                label = "liquidNavigationIndicatorScaleX"
            )
            val indicatorScaleY by animateFloatAsState(
                targetValue = 1f + pressProgress * 0.12f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 700f,
                    visibilityThreshold = 0.001f
                ),
                label = "liquidNavigationIndicatorScaleY"
            )
            val hiddenTabScale = 1f + pressProgress * 0.2f
            val indicatorVelocity = if (isDragging) selectedPosition.velocity / 10f else 0f

            fun navigateDuringDrag(targetIndex: Int) {
                if (targetIndex != dragNavigationIndex) {
                    dragNavigationIndex = targetIndex
                    latestOnNavigate.value(destinations[targetIndex])
                }
            }

            fun finishDrag(navigate: Boolean) {
                val targetIndex = if (navigate) {
                    dragTargetPosition.roundToInt().coerceIn(0, destinations.lastIndex)
                } else {
                    dragStartIndex
                }
                navigateDuringDrag(targetIndex)
                animationScope.launch {
                    selectedPosition.stop()
                    dragTargetPosition = targetIndex.toFloat()
                    isDragging = false
                    dragDistancePx = 0f
                    selectedPosition.animateTo(
                        targetValue = targetIndex.toFloat(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .pointerInput(tabWidthPx, isLtr) {
                        detectDragGestures(
                            onDragStart = {
                                dragStartIndex = latestSelectedIndex.value
                                dragNavigationIndex = latestSelectedIndex.value
                                dragTargetPosition = selectedPosition.value
                                dragDistancePx = 0f
                                isDragging = true
                                animationScope.launch { selectedPosition.stop() }
                            },
                            onDragEnd = { finishDrag(navigate = true) },
                            onDragCancel = { finishDrag(navigate = false) }
                        ) { change, dragAmount ->
                            change.consume()
                            dragDistancePx += dragAmount.x
                            val direction = if (isLtr) 1f else -1f
                            val targetPosition = (
                                    dragTargetPosition + direction * dragAmount.x / tabWidthPx
                                    ).coerceIn(-0.35f, maxIndex + 0.35f)
                            dragTargetPosition = targetPosition
                            animationScope.launch {
                                selectedPosition.animateTo(
                                    targetValue = targetPosition,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = 1000f,
                                        visibilityThreshold = 0.001f
                                    )
                                )
                            }
                            navigateDuringDrag(
                                targetPosition.roundToInt().coerceIn(0, destinations.lastIndex)
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .liquidLayerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffsetPx }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { containerShape },
                            effects = {
                                vibrancy()
                                blur(2.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            highlight = {
                                LiquidHighlight.Default.copy(alpha = pressProgress)
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .fillMaxWidth()
                        // Keep the captured layer larger than the centered indicator so
                        // refraction never samples past its bottom edge.
                        .height(64.dp)
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { destination ->
                        val selected = currentDestination == destination
                        LiquidGlassNavigationItemContent(
                            destination = destination,
                            selected = selected,
                            label = stringResource(destination.labelRes),
                            color = accentColor,
                            scale = (if (selected) 1.06f else 1f) * hiddenTabScale,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .graphicsLayer { translationX = panelOffsetPx }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { containerShape },
                            effects = {
                                vibrancy()
                                blur(2.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            highlight = { LiquidHighlight.Default.copy(alpha = 0.75f) },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val scale = 1f + 16.dp.toPx() / width * pressProgress
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = horizontalPadding)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = currentDestination == destination
                        val label = stringResource(destination.labelRes)
                        val itemColor by animateColorAsState(
                            targetValue = if (selected) accentColor else inactiveColor,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "liquidNavigationItemColor"
                        )
                        val itemScale by animateFloatAsState(
                            targetValue = if (selected) 1.06f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "liquidNavigationItemScale"
                        )
                        LiquidGlassNavigationItemContent(
                            destination = destination,
                            selected = selected,
                            label = label,
                            color = itemColor,
                            scale = itemScale,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(indicatorShape)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    interactionSource = navigationInteractionSource,
                                    indication = null,
                                    onClick = {
                                        dragTargetPosition = index.toFloat()
                                        dragNavigationIndex = index
                                        animationScope.launch {
                                            selectedPosition.animateTo(
                                                targetValue = index.toFloat(),
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                        onNavigate(destination)
                                    }
                                )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .padding(start = horizontalPadding, top = 4.dp, bottom = 4.dp)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = panelOffsetPx +
                                    elasticPosition * tabWidthPx * if (isLtr) 1f else -1f
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { indicatorShape },
                            effects = {
                                lens(
                                    refractionHeight = 10.dp.toPx() * pressProgress,
                                    refractionAmount = 14.dp.toPx() * pressProgress,
                                    depthEffect = true,
                                    chromaticAberration = pressProgress > 0.35f
                                )
                            },
                            highlight = {
                                LiquidHighlight.Default.copy(alpha = pressProgress)
                            },
                            shadow = {
                                LiquidShadow(alpha = pressProgress)
                            },
                            innerShadow = {
                                LiquidInnerShadow(
                                    radius = 8.dp * pressProgress,
                                    alpha = pressProgress
                                )
                            },
                            layerBlock = {
                                val velocityScaleX = (indicatorVelocity * 0.75f).coerceIn(-0.08f, 0.08f)
                                val velocityScaleY = (indicatorVelocity * 0.25f).coerceIn(-0.04f, 0.04f)
                                scaleX = indicatorScaleX / (1f - velocityScaleX)
                                scaleY = indicatorScaleY * (1f - velocityScaleY)
                            },
                            onDrawSurface = {
                                drawRect(
                                    color = indicatorOverlayColor,
                                    alpha = 1f - pressProgress
                                )
                                drawRect(
                                    indicatorPressedOverlayColor.copy(
                                        alpha = indicatorPressedOverlayColor.alpha * pressProgress
                                    )
                                )
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun RowScope.LiquidGlassNavigationItemContent(
    destination: TopLevelDestination,
    selected: Boolean,
    label: String,
    color: Color,
    scale: Float,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = destination.miuixIcon(selected),
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun liquidGlassElasticPosition(position: Float, maxIndex: Float): Float {
    return when {
        position < 0f -> -(-position / (1f + -position * 2f))
        position > maxIndex -> {
            val overflow = position - maxIndex
            maxIndex + overflow / (1f + overflow * 2f)
        }
        else -> position
    }
}

@Composable
fun MiuixTopLevelNavigationBar(
    currentDestination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixBlurNavigationBar(
        modifier = modifier.fillMaxWidth(),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination == destination
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = destination.miuixIcon(selected),
                label = stringResource(destination.labelRes)
            )
        }
    }
}
