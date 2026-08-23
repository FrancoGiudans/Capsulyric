/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: OverlayIconDropdownMenu (miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/menu/OverlayIconDropdownMenu.kt), OverlayDropdownPopup (miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/popup/OverlayDropdownPopup.kt) and miuix-blur
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Added blur backdrop and per-entry dropdown colors customization.
 */

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
 *  *
 *
 */

package com.example.islandlyrics.ui.miuix.blur

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A blur-enabled [top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu] wrapper supporting per-entry and per-item dropdown colors.
 */
@Composable
fun BlurOverlayIconDropdownMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxHeight: Dp? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    entryColors: Map<Int, DropdownColors> = emptyMap(),
    itemColors: Map<Pair<Int, Int>, DropdownColors> = emptyMap(),
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isExpanded by remember { mutableStateOf(false) }

    fun setExpanded(expanded: Boolean) {
        if (isExpanded != expanded) {
            isExpanded = expanded
            onExpandedChange?.invoke(expanded)
        }
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                setExpanded(!isExpanded)
            },
            enabled = enabled && entries.any { it.items.isNotEmpty() },
            holdDownState = isExpanded,
            backgroundColor = backgroundColor,
            cornerRadius = cornerRadius,
            minHeight = minHeight,
            minWidth = minWidth,
            content = content
        )

        BlurOverlayIconDropdownMenuPopup(
            show = isExpanded,
            entries = entries,
            dropdownColors = dropdownColors,
            entryColors = entryColors,
            itemColors = itemColors,
            onDismiss = { setExpanded(false) },
            onDismissFinished = { setExpanded(false) },
            maxHeight = maxHeight,
            renderInRootScaffold = renderInRootScaffold,
            collapseOnSelection = collapseOnSelection,
        )
    }
}

/**
 * Single [DropdownEntry] overload for [BlurOverlayIconDropdownMenu].
 */
@Composable
fun BlurOverlayIconDropdownMenu(
    entry: DropdownEntry,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxHeight: Dp? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    itemColors: Map<Int, DropdownColors> = emptyMap(),
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    BlurOverlayIconDropdownMenu(
        entries = listOf(entry),
        modifier = modifier,
        enabled = enabled,
        maxHeight = maxHeight,
        dropdownColors = dropdownColors,
        itemColors = itemColors.mapKeys { 0 to it.key },
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius,
        minHeight = minHeight,
        minWidth = minWidth,
        content = content
    )
}

@Composable
private fun BlurOverlayIconDropdownMenuPopup(
    show: Boolean,
    entries: List<DropdownEntry>,
    dropdownColors: DropdownColors,
    entryColors: Map<Int, DropdownColors>,
    itemColors: Map<Pair<Int, Int>, DropdownColors>,
    onDismiss: () -> Unit,
    onDismissFinished: (() -> Unit)?,
    maxHeight: Dp?,
    renderInRootScaffold: Boolean,
    collapseOnSelection: Boolean,
) {
    val currentEntries by rememberUpdatedState(entries)
    val onItemClicked: (Int, Int) -> Unit = remember(collapseOnSelection) {
        { entryIndex, itemIndex ->
            currentEntries.getOrNull(entryIndex)
                ?.items
                ?.getOrNull(itemIndex)
                ?.onClick
                ?.invoke()
            if (collapseOnSelection) {
                onDismiss()
            }
        }
    }

    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current && backdrop != null
    val panelShape = RoundedCornerShape(16.dp)
    val panelColor = MiuixTheme.colorScheme.surface
    val isLight = panelColor.luminance() > 0.5f
    val panelDropdownColors = if (blurEnabled) {
        val selectedContainer = if (dropdownColors.selectedContainerColor != dropdownColors.containerColor) {
            dropdownColors.selectedContainerColor
        } else {
            MiuixTheme.colorScheme.primary.copy(alpha = if (isLight) 0.12f else 0.22f)
        }
        dropdownColors.copy(
            containerColor = Color.Transparent,
            selectedContainerColor = selectedContainer,
        )
    } else {
        dropdownColors
    }

    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        maxHeight = maxHeight,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(panelShape)
                .miuixSurfaceBlur(
                    enabled = blurEnabled,
                    backdrop = backdrop,
                    shape = panelShape,
                    fallbackColor = panelColor,
                    blurRadius = MiuixBlurStyleDefaults.BlurRadius,
                    colors = miuixBlurColors(panelColor),
                )
                .border(
                    width = 1.dp,
                    color = if (isLight) {
                        Color.White.copy(alpha = 0.35f)
                    } else {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    },
                    shape = panelShape,
                ),
        ) {
            ListPopupColumn {
                val lastEntryIndex = entries.lastIndex
                entries.forEachIndexed { entryIndex, entry ->
                    val lastItemIndex = entry.items.lastIndex
                    entry.items.forEachIndexed { itemIndex, item ->
                        val customColors = itemColors[entryIndex to itemIndex] ?: entryColors[entryIndex]
                        val itemResolvedColors = if (customColors != null) {
                            panelDropdownColors.copy(
                                contentColor = customColors.contentColor,
                                summaryColor = customColors.summaryColor
                            )
                        } else {
                            panelDropdownColors
                        }
                        DropdownImpl(
                            item = item,
                            optionSize = entry.items.size,
                            isSelected = item.selected,
                            index = itemIndex,
                            dropdownColors = itemResolvedColors,
                            enabled = entry.enabled && item.enabled,
                            isFirst = entryIndex == 0 && itemIndex == 0,
                            isLast = entryIndex == lastEntryIndex && itemIndex == lastItemIndex,
                            onSelectedIndexChange = {
                                onItemClicked(entryIndex, it)
                            },
                        )
                    }
                    if (entryIndex != lastEntryIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            thickness = 1.5.dp,
                        )
                    }
                }
            }
        }
    }
}
