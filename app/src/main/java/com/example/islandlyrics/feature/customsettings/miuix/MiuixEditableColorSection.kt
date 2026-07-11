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

package com.example.islandlyrics.feature.customsettings.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.islandlyrics.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MiuixEditableColorSection(
    title: String,
    color: Color,
    isEditing: Boolean,
    defaultActionText: String,
    onStartEditing: () -> Unit,
    onColorChanged: (Color) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onUseDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.dp.value.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (isEditing) {
            ColorPalette(
                color = color,
                onColorChanged = onColorChanged
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = stringResource(R.string.dialog_btn_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = defaultActionText,
                    onClick = onUseDefault,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.apply),
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        } else {
            TextButton(
                text = stringResource(R.string.parser_edit),
                onClick = onStartEditing,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (isEditing) {
                    R.string.settings_theme_color_editing_hint
                } else {
                    R.string.settings_theme_color_edit_hint
                }
            ),
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            fontSize = 13.dp.value.sp
        )
    }
}
