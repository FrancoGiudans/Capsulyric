/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: miuix basic components: Card, CardDefaults, Icon, Text and miuix-blur styles
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Custom reorderable panel/list built on miuix components and miuix-blur styles.
 */

/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of IslandLyrics.
 *
 * IslandLyrics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.example.islandlyrics.ui.miuix.reorderable

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurBackdrop
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurSurfaceActive
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurStyleDefaults
import com.example.islandlyrics.ui.miuix.blur.miuixBlurColors
import com.example.islandlyrics.ui.miuix.blur.miuixSurfaceBlur
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A row model used by [MiuixBlurReorderablePanel].
 *
 * [id] must be stable and unique for the lifetime of an item. The caller owns
 * the list state and should update it from [MiuixBlurReorderablePanel]'s callbacks.
 */
data class MiuixReorderableListItem(
    val id: String,
    val title: String,
    val checked: Boolean = true,
    val enabled: Boolean = true,
)

/**
 * A Miuix-styled reorderable panel with optional MIUIX texture blur.
 *
 * Blur behavior:
 * - Standalone (e.g. inside a settings page): the panel renders its own
 *   [miuixSurfaceBlur] texture blur (with the global edge highlight setting)
 *   and a subtle border.
 * - Embedded in another blurred surface (e.g. [com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog],
 *   detected through [LocalMiuixBlurSurfaceActive]): the panel gives up its own
 *   background and border entirely so it visually merges with the host surface;
 *   only the rows remain, no edge highlight is rendered, and the content
 *   padding is tightened to sit closer to the host's content area.
 *
 * Reordering starts with a long press on the handle. The checkbox remains
 * independently clickable and the list state is intentionally owned by the
 * caller, making this component suitable for ViewModel-backed settings.
 *
 * The rows are rendered in a plain [Column] (not a lazy list) so the panel can
 * be safely embedded inside another scrollable container such as the settings
 * LazyColumn without hitting "infinite maximum height constraints" crashes.
 *
 * @param rowHeight height of each reorderable row.
 * @param itemCornerRadius corner radius of each row card (rounded rectangle).
 */
@Composable
fun MiuixBlurReorderablePanel(
    items: List<MiuixReorderableListItem>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onCheckedChange: (itemId: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
    itemCornerRadius: Dp = 16.dp,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Reset",
    itemSpacing: Dp = 12.dp,
    panelContentPadding: PaddingValues = PaddingValues(12.dp),
    panelShape: Shape = RoundedCornerShape(32.dp),
    panelColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    itemColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    blurRadius: Float = MiuixBlurStyleDefaults.BlurRadius,
    noiseCoefficient: Float = MiuixBlurStyleDefaults.NoiseCoefficient,
    panelBorderColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current && backdrop != null
    val embeddedInBlurSurface = LocalMiuixBlurSurfaceActive.current
    val useTextureBlur = !embeddedInBlurSurface && blurEnabled
    val currentItems = rememberUpdatedState(items)
    val currentOnMove = rememberUpdatedState(onMove)
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var draggedItemOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val spacingPx = with(LocalDensity.current) { itemSpacing.toPx() }
    val itemStepPx = rowHeightPx + spacingPx
    val contentPadding = if (embeddedInBlurSurface) {
        // Embedded in a dialog: stay tight to the host's content area.
        PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    } else {
        panelContentPadding
    }

    fun finishDrag() {
        draggedItemId = null
        draggedItemOffset = 0f
    }

    Box(
        modifier = modifier.then(
            when {
                useTextureBlur -> Modifier
                    .clip(panelShape)
                    .miuixSurfaceBlur(
                        enabled = true,
                        backdrop = backdrop,
                        shape = panelShape,
                        fallbackColor = panelColor,
                        blurRadius = blurRadius,
                        noiseCoefficient = noiseCoefficient,
                        colors = miuixBlurColors(panelColor),
                    )
                    .border(width = 1.dp, color = panelBorderColor, shape = panelShape)
                !embeddedInBlurSurface -> Modifier
                    .clip(panelShape)
                    .background(color = panelColor, shape = panelShape)
                    .border(width = 1.dp, color = panelBorderColor, shape = panelShape)
                // Embedded in another blurred surface: give up our own
                // background/border so we match the host control.
                else -> Modifier
            }
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            items.forEachIndexed { index, item ->
                key(item.id) {
                    val isDragged = draggedItemId == item.id

                    MiuixBlurReorderableRow(
                        item = item,
                        itemColor = itemColor,
                        rowHeight = rowHeight,
                        itemCornerRadius = itemCornerRadius,
                        index = index,
                        itemStepPx = itemStepPx,
                        isDragged = isDragged,
                        dragOffset = if (isDragged) draggedItemOffset else 0f,
                        modifier = Modifier.pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedItemId = item.id
                                    draggedItemOffset = 0f
                                },
                                onDragCancel = ::finishDrag,
                                onDragEnd = ::finishDrag,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggedItemId != item.id) return@detectDragGesturesAfterLongPress

                                    draggedItemOffset += dragAmount.y
                                    val latestItems = currentItems.value
                                    val currentIndex = latestItems.indexOfFirst { it.id == item.id }
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                                    val targetIndex = (currentIndex + (draggedItemOffset / itemStepPx).roundToInt())
                                        .coerceIn(0, latestItems.lastIndex)

                                    if (targetIndex != currentIndex) {
                                        val delta = targetIndex - currentIndex
                                        draggedItemOffset -= delta * itemStepPx
                                        currentOnMove.value(currentIndex, targetIndex)
                                    }
                                },
                            )
                        },
                        onCheckedChange = { checked ->
                            onCheckedChange(item.id, checked)
                        },
                    )
                }
            }

            if (onReset != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = itemCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = itemColor),
                    onClick = onReset,
                ) {
                    Text(
                        text = resetLabel,
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Compatibility wrapper for callers using the original non-blurred name.
 * Prefer [MiuixBlurReorderablePanel] for new code.
 */
@Deprecated(
    message = "Use MiuixBlurReorderablePanel instead",
    replaceWith = ReplaceWith("MiuixBlurReorderablePanel(items, onMove, onCheckedChange, modifier, rowHeight, itemCornerRadius, onReset, resetLabel, itemSpacing)"),
)
@Composable
fun MiuixReorderableList(
    items: List<MiuixReorderableListItem>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onCheckedChange: (itemId: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
    itemCornerRadius: Dp = 16.dp,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Reset",
    itemSpacing: Dp = 12.dp,
) {
    MiuixBlurReorderablePanel(
        items = items,
        onMove = onMove,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        rowHeight = rowHeight,
        itemCornerRadius = itemCornerRadius,
        onReset = onReset,
        resetLabel = resetLabel,
        itemSpacing = itemSpacing,
    )
}

@Composable
private fun MiuixBlurReorderableRow(
    item: MiuixReorderableListItem,
    itemColor: Color,
    rowHeight: Dp,
    itemCornerRadius: Dp,
    index: Int,
    itemStepPx: Float,
    isDragged: Boolean,
    dragOffset: Float,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Smoothly glides this row to its new slot when the order changes.
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    val previousIndex = remember(item.id) { mutableIntStateOf(index) }

    // Apply the slot displacement BEFORE the first frame with the new index is
    // drawn. Doing this in LaunchedEffect alone would render one frame at the
    // new slot first (a visible "flash") and only then start the animation.
    SideEffect {
        if (isDragged) {
            previousIndex.intValue = index
            offsetY = 0f
            return@SideEffect
        }
        if (previousIndex.intValue != index) {
            offsetY += (previousIndex.intValue - index) * itemStepPx
            previousIndex.intValue = index
        }
    }

    LaunchedEffect(index) {
        if (!isDragged) {
            animate(
                initialValue = offsetY,
                targetValue = 0f,
                animationSpec = spring(stiffness = 380f, dampingRatio = 0.8f),
            ) { value, _ ->
                offsetY = value
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.02f else 1f,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.8f),
        label = "reorderScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragged) dragOffset else offsetY
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (item.enabled) 1f else 0.55f),
        cornerRadius = itemCornerRadius,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = itemColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier
                    .size(36.dp)
                    .padding(4.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = MiuixTheme.textStyles.body1.fontWeight,
                modifier = Modifier.weight(1f),
            )
            MiuixReorderableCheckIndicator(
                checked = item.checked,
                enabled = item.enabled,
                onClick = if (item.enabled) ({ onCheckedChange(!item.checked) }) else null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/**
 * An animated checkbox styled after the Miuix [top.yukonga.miuix.kmp.basic.Checkbox]:
 * the checked state fills with the primary color and draws a trimmed checkmark,
 * while the unchecked state keeps a visible ring outline. The background color
 * and the checkmark trim are animated through the Miuix-style transition.
 */
@Composable
private fun MiuixReorderableCheckIndicator(
    checked: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val fillColor = if (enabled) colorScheme.primary else colorScheme.disabledPrimary
    val ringColor = if (enabled) colorScheme.outline else colorScheme.disabledOnSurface
    val checkColor = colorScheme.onPrimary

    val transition = updateTransition(checked, label = "ReorderableCheckboxTransition")

    val backgroundColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "BackgroundColor",
    ) { on -> if (on) fillColor else Color.Transparent }

    val checkAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 10, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 150, easing = FastOutSlowInEasing)
            }
        },
        label = "CheckAlpha",
    ) { on -> if (on) 1f else 0f }

    val checkStartTrim by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 200, easing = FastOutSlowInEasing)
            } else {
                keyframes {
                    durationMillis = 300
                    0.1f at 300
                }
            }
        },
        label = "CheckStartTrim",
    ) { on -> if (on) 0.186f else 0.1f }

    val checkEndTrim by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 200, easing = FastOutSlowInEasing)
            } else {
                keyframes {
                    durationMillis = 300
                    0.1f at 300
                }
            }
        },
        label = "CheckEndTrim",
    ) { on -> if (on) 0.803f else 0.1f }

    val checkPath = remember { Path() }

    Box(
        modifier = modifier
            .requiredSize(26.dp)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .drawWithCache {
                val viewportSize = 23f
                val strokeWidth = size.width * 0.09f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val viewportCenterX = viewportSize / 2f
                val viewportCenterY = viewportSize / 2f

                val startPoint = Offset(
                    centerX + ((5f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((9.4f - viewportCenterY) / viewportSize * size.height),
                )
                val middlePoint = Offset(
                    centerX + ((10.3f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((14.9f - viewportCenterY) / viewportSize * size.height),
                )
                val endPoint = Offset(
                    centerX + ((17.9f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((5.1f - viewportCenterY) / viewportSize * size.height),
                )
                val cache = ReorderableCheckmarkCache(
                    startPoint = startPoint,
                    middlePoint = middlePoint,
                    endPoint = endPoint,
                    centerX = centerX,
                    centerY = centerY,
                    strokeWidth = strokeWidth,
                )
                val stroke = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    miter = 10f,
                )

                onDrawBehind {
                    // Ring outline first, then the fill covers it when checked.
                    drawCircle(
                        color = ringColor,
                        radius = size.width / 2f - strokeWidth,
                        style = Stroke(width = strokeWidth),
                    )
                    drawCircle(backgroundColor)
                    drawTrimmedReorderableCheckmark(
                        color = checkColor,
                        alpha = checkAlpha,
                        trimStart = checkStartTrim,
                        trimEnd = checkEndTrim,
                        crossCenterGravitation = 0f,
                        path = checkPath,
                        cache = cache,
                        stroke = stroke,
                    )
                }
            }
    ) {}
}

private data class ReorderableCheckmarkCache(
    val startPoint: Offset,
    val middlePoint: Offset,
    val endPoint: Offset,
    val centerX: Float,
    val centerY: Float,
    val strokeWidth: Float,
)

private fun DrawScope.drawTrimmedReorderableCheckmark(
    color: Color,
    alpha: Float = 1f,
    trimStart: Float,
    trimEnd: Float,
    crossCenterGravitation: Float,
    path: Path,
    cache: ReorderableCheckmarkCache,
    stroke: Stroke,
) {
    path.rewind()

    val gravitatedStart = Offset(
        cache.startPoint.x,
        lerp(cache.startPoint.y, cache.centerY, crossCenterGravitation),
    )
    val gravitatedMiddle = Offset(
        lerp(cache.middlePoint.x, cache.centerX, crossCenterGravitation),
        lerp(cache.middlePoint.y, cache.centerY, crossCenterGravitation),
    )
    val gravitatedEnd = Offset(
        cache.endPoint.x,
        lerp(cache.endPoint.y, cache.centerY, crossCenterGravitation),
    )

    val firstSegmentLength = (gravitatedMiddle - gravitatedStart).getDistance()
    val secondSegmentLength = (gravitatedEnd - gravitatedMiddle).getDistance()
    val totalLength = firstSegmentLength + secondSegmentLength

    val startDistance = totalLength * trimStart
    val endDistance = totalLength * trimEnd

    if (startDistance < firstSegmentLength && endDistance > 0) {
        val segStartRatio = (startDistance / firstSegmentLength).coerceIn(0f, 1f)
        val segEndRatio = (endDistance / firstSegmentLength).coerceIn(0f, 1f)

        val startX = gravitatedStart.x + (gravitatedMiddle.x - gravitatedStart.x) * segStartRatio
        val startY = gravitatedStart.y + (gravitatedMiddle.y - gravitatedStart.y) * segStartRatio
        val endX = gravitatedStart.x + (gravitatedMiddle.x - gravitatedStart.x) * segEndRatio
        val endY = gravitatedStart.y + (gravitatedMiddle.y - gravitatedStart.y) * segEndRatio

        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
    }

    if (endDistance > firstSegmentLength) {
        val segStartRatio = ((startDistance - firstSegmentLength) / secondSegmentLength).coerceIn(0f, 1f)
        val segEndRatio = ((endDistance - firstSegmentLength) / secondSegmentLength).coerceIn(0f, 1f)

        val startX = gravitatedMiddle.x + (gravitatedEnd.x - gravitatedMiddle.x) * segStartRatio
        val startY = gravitatedMiddle.y + (gravitatedEnd.y - gravitatedMiddle.y) * segStartRatio
        val endX = gravitatedMiddle.x + (gravitatedEnd.x - gravitatedMiddle.x) * segEndRatio
        val endY = gravitatedMiddle.y + (gravitatedEnd.y - gravitatedMiddle.y) * segEndRatio

        if (startDistance < firstSegmentLength) {
            path.lineTo(endX, endY)
        } else {
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
        }
    }

    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = stroke,
    )
}
