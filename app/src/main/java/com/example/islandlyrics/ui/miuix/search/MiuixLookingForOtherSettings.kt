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

package com.example.islandlyrics.ui.miuix.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.search.SettingsSearchAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class OtherSettingLink(
    @StringRes val titleRes: Int,
    val action: SettingsSearchAction.Navigate? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * 渲染各子设置页末尾的“在寻找其他设置？”关联卡片。
 * 遵循现代规范：灰色分类小标题 + 纯净圆角 Card + 粗体主色纯文本链接，无箭头无分割线。
 */
@Composable
fun MiuixLookingForOtherSettings(
    links: List<OtherSettingLink>,
    onNavigate: ((SettingsSearchAction.Navigate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (links.isEmpty()) return

    Column(modifier = modifier) {
        SmallTitle(text = stringResource(R.string.settings_looking_for_other))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                links.forEach { link ->
                    Text(
                        text = stringResource(link.titleRes),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (link.onClick != null) {
                                    link.onClick.invoke()
                                } else if (link.action != null) {
                                    onNavigate?.invoke(link.action)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}
