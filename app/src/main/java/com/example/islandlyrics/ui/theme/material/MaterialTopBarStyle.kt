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

package com.example.islandlyrics.ui.theme.material

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import com.example.islandlyrics.ui.material.blur.LocalMaterialBlurEnabled
import com.example.islandlyrics.ui.material.blur.LocalMaterialBlurRadius
import com.example.islandlyrics.ui.material.blur.materialBlurPanel

// TopAppBar blends with the page background when the large title is expanded.
// When collapsed (scrolled), it picks up surfaceContainer for a subtle floating tint.
@Composable
fun neutralMaterialTopBarColors(): TopAppBarColors {
    val blurEnabled = LocalMaterialBlurEnabled.current
    return TopAppBarDefaults.topAppBarColors(
        containerColor = if (blurEnabled) Color.Transparent else MaterialTheme.colorScheme.background,
        scrolledContainerColor = if (blurEnabled) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    )
}

@Composable
fun materialPageContainerColor() = MaterialTheme.colorScheme.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialBlurTopAppBar(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .materialBlurPanel(
                shape = RectangleShape,
                radius = maxOf(LocalMaterialBlurRadius.current, 28.dp),
                tint = Color.Transparent,
            )
    ) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon ?: {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = actions,
            colors = neutralMaterialTopBarColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
