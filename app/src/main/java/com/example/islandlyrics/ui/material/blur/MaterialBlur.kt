/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: AndroidX Compose Material 3: Scaffold, AlertDialog, DropdownMenu (https://developer.android.com/jetpack/androidx/releases/compose-material3, Copyright The Android Open Source Project, Apache License 2.0); and miuix-blur: layerBackdrop, textureBlur, rememberLayerBackdrop (Copyright 2025, compose-miuix-ui contributors, Apache License 2.0)
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Combined Material 3 components with miuix-blur backdrop/texture blur (MaterialBlurRoot, MaterialBlurScaffold, MaterialBlurAlertDialog, MaterialBlurDropdownMenu).
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
 *
 *
 */

package com.example.islandlyrics.ui.material.blur

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.AlertDialog as Material3AlertDialog
import androidx.compose.material3.DropdownMenu as Material3DropdownMenu
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurBackdrop
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.FabPosition as MiuixFabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

val LocalMaterialBlurEnabled = compositionLocalOf { false }
val LocalMaterialBlurRadius = compositionLocalOf { 20.dp }

private const val MaterialBlurTintAlpha = 0.68f
private const val MaterialBlurDialogTintAlpha = 0.78f

@Composable
fun MaterialBlurRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences.of(context) }
    var blurEnabled by remember(prefs) {
        mutableStateOf(LabFeatureManager.isMaterialBlurEnabled(prefs))
    }
    var blurRadius by remember(prefs) {
        mutableStateOf(AppPreferences.materialBlurRadiusDp(prefs).dp)
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                LabFeatureManager.KEY_MATERIAL_BLUR_ENABLED ->
                    blurEnabled = LabFeatureManager.isMaterialBlurEnabled(prefs)
                AppPreferences.Keys.MATERIAL_BLUR_RADIUS_DP ->
                    blurRadius = AppPreferences.materialBlurRadiusDp(prefs).dp
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    CompositionLocalProvider(
        LocalMaterialBlurEnabled provides blurEnabled,
        LocalMaterialBlurRadius provides blurRadius,
        content = content
    )
}

@Composable
fun Modifier.materialBlurPanel(
    shape: Shape = RoundedCornerShape(24.dp),
    sourceKey: Any? = null,
    enabled: Boolean = LocalMaterialBlurEnabled.current,
    radius: Dp = LocalMaterialBlurRadius.current,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MaterialBlurTintAlpha),
    backgroundColor: Color = tint,
): Modifier {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    if (!enabled || !blurEnabled || backdrop == null || !isRenderEffectSupported()) {
        return background(backgroundColor, shape)
    }
    return clip(shape).textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = radius.value,
        colors = BlurColors(
            blendColors = listOf(
                BlendColorEntry(color = tint)
            )
        ),
    )
}

@Composable
fun MaterialBlurScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val blurEnabled = LocalMaterialBlurEnabled.current
    val blurRadius = LocalMaterialBlurRadius.current
    val blurBackground = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
        alpha = MaterialBlurTintAlpha
    )
    val scaffoldContainerColor = if (blurEnabled) Color.Transparent else containerColor
    val miuixFabPosition = when (floatingActionButtonPosition) {
        FabPosition.Start -> MiuixFabPosition.Start
        FabPosition.Center -> MiuixFabPosition.Center
        FabPosition.EndOverlay -> MiuixFabPosition.End
        else -> MiuixFabPosition.End
    }

    if (blurEnabled) {
        val blurBackdrop = rememberLayerBackdrop {
            drawRect(containerColor)
            drawContent()
        }
        CompositionLocalProvider(
            LocalMiuixBlurBackdrop provides blurBackdrop,
            LocalMiuixBlurEnabled provides blurEnabled
        ) {
            MiuixScaffold(
                modifier = modifier,
                topBar = topBar,
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .materialBlurPanel(
                                shape = RectangleShape,
                                enabled = true,
                                radius = blurRadius,
                                tint = blurBackground,
                            )
                    ) {
                        bottomBar()
                    }
                },
                snackbarHost = snackbarHost,
                floatingActionButton = floatingActionButton,
                floatingActionButtonPosition = miuixFabPosition,
                containerColor = scaffoldContainerColor,
                contentWindowInsets = contentWindowInsets
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(containerColor)
                        .layerBackdrop(blurBackdrop)
                ) {
                    content(paddingValues)
                }
            }
        }
    } else {
        MiuixScaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = miuixFabPosition,
            containerColor = scaffoldContainerColor,
            contentWindowInsets = contentWindowInsets
        ) { paddingValues ->
            content(paddingValues)
        }
    }
}

@Composable
fun MaterialBlurAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconContentColor: Color = MaterialTheme.colorScheme.secondary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 6.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val blurEnabled = LocalMaterialBlurEnabled.current
    val canBlur = blurEnabled &&
        LocalMiuixBlurBackdrop.current != null &&
        isRenderEffectSupported()
    Material3AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.materialBlurPanel(
            shape = shape,
            enabled = canBlur,
            tint = containerColor.copy(alpha = MaterialBlurDialogTintAlpha),
            backgroundColor = containerColor,
        ),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (canBlur) Color.Transparent else containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = if (canBlur) 0.dp else tonalElevation,
        properties = properties,
    )
}

@Composable
fun MaterialBlurDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MaterialTheme.shapes.extraSmall,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 3.dp,
    shadowElevation: Dp = 3.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val blurEnabled = LocalMaterialBlurEnabled.current
    val canBlur = blurEnabled &&
        LocalMiuixBlurBackdrop.current != null &&
        isRenderEffectSupported()
    Material3DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.materialBlurPanel(
            shape = shape,
            enabled = canBlur,
            tint = containerColor.copy(alpha = MaterialBlurDialogTintAlpha),
            backgroundColor = containerColor,
        ),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = if (canBlur) Color.Transparent else containerColor,
        tonalElevation = if (canBlur) 0.dp else tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}
