/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of Capsulyric.
 */

package com.example.islandlyrics.feature.settings.material

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.core.settings.search.SettingsSearchAction
import com.example.islandlyrics.R

data class MaterialOtherSettingLink(
    @StringRes val titleRes: Int,
    val action: SettingsSearchAction.Navigate
)

@Composable
fun MaterialLookingForOtherSettings(
    links: List<MaterialOtherSettingLink>,
    onNavigate: (SettingsSearchAction.Navigate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (links.isEmpty()) return

    Column(modifier = modifier) {
        SettingsSectionHeader(text = stringResource(R.string.settings_looking_for_other))
        SettingsCard {
            links.forEachIndexed { index, link ->
                if (index > 0) SettingsCardDivider()
                SettingsActionItem(
                    title = stringResource(link.titleRes),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = { onNavigate(link.action) }
                )
            }
        }
    }
}
