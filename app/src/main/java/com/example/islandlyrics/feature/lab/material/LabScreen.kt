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

package com.example.islandlyrics.feature.lab.material

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandColorSource
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.feature.settings.material.SettingsSwitchItem
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    onBack: () -> Unit,
    onOpenCapsuleNotification: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var offlineModeEnabled by remember { mutableStateOf(OfflineModeManager.isEnabled(context)) }
    var superIslandAdvancedStyleEnabled by remember {
        mutableStateOf(LabFeatureManager.isSuperIslandAdvancedStyleEnabled(context))
    }
    var superIslandTextLimitsEnabled by remember {
        mutableStateOf(LabFeatureManager.isSuperIslandTextLimitsEnabled(context))
    }
    var superIslandRelaxedTextLimitsEnabled by remember {
        mutableStateOf(LabFeatureManager.isSuperIslandRelaxedTextLimitsEnabled(context))
    }
    var superIslandSmartMinContrast by remember {
        mutableStateOf(SuperIslandColorSource.readSmartMinContrast(AppPreferences.of(context)))
    }
    var superIslandSmartWhiteRatio by remember {
        mutableStateOf(SuperIslandColorSource.readSmartWhiteRatio(AppPreferences.of(context)))
    }
    var liveUpdateTextLimitsEnabled by remember {
        mutableStateOf(LabFeatureManager.isLiveUpdateTextLimitsEnabled(context))
    }
    var floatingLyricsLabEnabled by remember {
        mutableStateOf(LabFeatureManager.isFloatingLyricsEnabled(context))
    }
    var floatingWordHighFpsEnabled by remember {
        mutableStateOf(LabFeatureManager.isFloatingWordHighFpsEnabled(context))
    }
    var materialBlurEnabled by remember {
        mutableStateOf(LabFeatureManager.isMaterialBlurEnabled(context))
    }
    var experimentUpdatesEnabled by remember {
        mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context))
    }
    var showOfflineModeDialog by remember { mutableStateOf(false) }
    var showAdvancedStyleDialog by remember { mutableStateOf(false) }
    var showLiveUpdateTextLimitsDialog by remember { mutableStateOf(false) }

    MaterialBlurScaffold(
        topBar = {
            MaterialBlurTopAppBar(
                title = { Text(stringResource(R.string.title_lab)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.online_lyric_debug_back))
                    }
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
        ) {
            item {
                Text(
                    text = stringResource(R.string.diag_lab_page_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            item { SettingsSectionHeader(text = stringResource(R.string.diag_lab_category_general)) }
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_full_offline_mode),
                        subtitle = stringResource(R.string.settings_full_offline_mode_desc),
                        checked = offlineModeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showOfflineModeDialog = true
                            } else {
                                offlineModeEnabled = false
                                OfflineModeManager.setEnabled(context, false)
                            }
                        }
                    )
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.diag_lab_category_interface)) }
            item {
                SettingsCard {
                    if (RomUtils.isXiaomi()) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.diag_lab_super_island_advanced_style_title),
                            subtitle = stringResource(R.string.diag_lab_super_island_advanced_style_desc),
                            checked = superIslandAdvancedStyleEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) showAdvancedStyleDialog = true
                                else {
                                    LabFeatureManager.setSuperIslandAdvancedStyleEnabled(context, false)
                                    superIslandAdvancedStyleEnabled = false
                                }
                            }
                        )
                        SettingsCardDivider()
                        SettingsSwitchItem(
                            title = stringResource(R.string.diag_lab_super_island_text_limits_title),
                            subtitle = stringResource(R.string.diag_lab_super_island_text_limits_desc),
                            checked = superIslandTextLimitsEnabled,
                            onCheckedChange = {
                                superIslandTextLimitsEnabled = it
                                LabFeatureManager.setSuperIslandTextLimitsEnabled(context, it)
                            }
                        )
                        SettingsCardDivider()
                        SettingsSwitchItem(
                            title = stringResource(R.string.diag_lab_super_island_relaxed_text_limits_title),
                            subtitle = stringResource(R.string.diag_lab_super_island_relaxed_text_limits_desc),
                            checked = superIslandRelaxedTextLimitsEnabled,
                            onCheckedChange = {
                                superIslandRelaxedTextLimitsEnabled = it
                                LabFeatureManager.setSuperIslandRelaxedTextLimitsEnabled(context, it)
                            }
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.diag_lab_super_island_smart_contrast_title) +
                                    ": " + String.format(java.util.Locale.US, "%.1f", superIslandSmartMinContrast),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.diag_lab_super_island_smart_contrast_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = superIslandSmartMinContrast,
                                onValueChange = { raw ->
                                    val stepped = ((raw * 10f).roundToInt() / 10f)
                                        .coerceIn(
                                            SuperIslandColorSource.SMART_CONTRAST_MIN,
                                            SuperIslandColorSource.SMART_CONTRAST_MAX
                                        )
                                    superIslandSmartMinContrast = stepped
                                    SuperIslandColorSource.writeSmartMinContrast(
                                        AppPreferences.of(context),
                                        stepped
                                    )
                                },
                                valueRange = SuperIslandColorSource.SMART_CONTRAST_MIN..SuperIslandColorSource.SMART_CONTRAST_MAX,
                                steps = ((SuperIslandColorSource.SMART_CONTRAST_MAX - SuperIslandColorSource.SMART_CONTRAST_MIN) / 0.1f).toInt() - 1
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.diag_lab_super_island_smart_white_title) +
                                    ": ${(superIslandSmartWhiteRatio * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.diag_lab_super_island_smart_white_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = superIslandSmartWhiteRatio,
                                onValueChange = { raw ->
                                    val stepped = ((raw * 20f).roundToInt() / 20f)
                                        .coerceIn(
                                            SuperIslandColorSource.SMART_WHITE_RATIO_MIN,
                                            SuperIslandColorSource.SMART_WHITE_RATIO_MAX
                                        )
                                    superIslandSmartWhiteRatio = stepped
                                    SuperIslandColorSource.writeSmartWhiteRatio(
                                        AppPreferences.of(context),
                                        stepped
                                    )
                                },
                                valueRange = SuperIslandColorSource.SMART_WHITE_RATIO_MIN..SuperIslandColorSource.SMART_WHITE_RATIO_MAX,
                                steps = ((SuperIslandColorSource.SMART_WHITE_RATIO_MAX - SuperIslandColorSource.SMART_WHITE_RATIO_MIN) / 0.05f).toInt() - 1
                            )
                        }
                        SettingsCardDivider()
                    }
                    SettingsSwitchItem(
                        title = stringResource(R.string.diag_lab_live_update_text_limits_title),
                        subtitle = stringResource(R.string.diag_lab_live_update_text_limits_desc),
                        checked = liveUpdateTextLimitsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showLiveUpdateTextLimitsDialog = true
                            } else {
                                LabFeatureManager.setLiveUpdateTextLimitsEnabled(context, false)
                                liveUpdateTextLimitsEnabled = false
                            }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.diag_lab_material_blur_title),
                        subtitle = stringResource(R.string.diag_lab_material_blur_desc),
                        checked = materialBlurEnabled,
                        onCheckedChange = {
                            materialBlurEnabled = it
                            LabFeatureManager.setMaterialBlurEnabled(context, it)
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchItem(
                        title = stringResource(R.string.diag_lab_floating_lyrics_title),
                        subtitle = stringResource(R.string.diag_lab_floating_lyrics_desc),
                        checked = floatingLyricsLabEnabled,
                        onCheckedChange = {
                            floatingLyricsLabEnabled = it
                            LabFeatureManager.setFloatingLyricsEnabled(context, it)
                        }
                    )
                    if (floatingLyricsLabEnabled) {
                        SettingsCardDivider()
                        SettingsSwitchItem(
                            title = stringResource(R.string.diag_lab_floating_word_high_fps_title),
                            subtitle = stringResource(R.string.diag_lab_floating_word_high_fps_desc),
                            checked = floatingWordHighFpsEnabled,
                            onCheckedChange = {
                                floatingWordHighFpsEnabled = it
                                LabFeatureManager.setFloatingWordHighFpsEnabled(context, it)
                            }
                        )
                    }
                }
            }

            item { SettingsSectionHeader(text = stringResource(R.string.diag_lab_category_updates)) }
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        title = stringResource(R.string.diag_lab_experiment_updates_title),
                        subtitle = stringResource(R.string.diag_lab_experiment_updates_desc),
                        checked = experimentUpdatesEnabled,
                        onCheckedChange = {
                            experimentUpdatesEnabled = it
                            LabFeatureManager.setExperimentUpdatesEnabled(context, it)
                        }
                    )
                }
            }
        }
    }

    if (showOfflineModeDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showOfflineModeDialog = false },
            title = { Text(stringResource(R.string.settings_full_offline_mode_dialog_title)) },
            text = { Text(stringResource(R.string.settings_full_offline_mode_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        OfflineModeManager.setEnabled(context, true)
                        offlineModeEnabled = true
                        showOfflineModeDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_full_offline_mode_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineModeDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showAdvancedStyleDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showAdvancedStyleDialog = false },
            title = { Text(stringResource(R.string.diag_lab_super_island_dialog_title)) },
            text = { Text(stringResource(R.string.diag_lab_super_island_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LabFeatureManager.setSuperIslandAdvancedStyleEnabled(context, true)
                        superIslandAdvancedStyleEnabled = true
                        showAdvancedStyleDialog = false
                        onOpenCapsuleNotification?.invoke()
                            ?: context.startActivity(Intent(context, CustomSettingsActivity::class.java))
                    }
                ) {
                    Text(stringResource(R.string.diag_lab_super_island_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedStyleDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showLiveUpdateTextLimitsDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showLiveUpdateTextLimitsDialog = false },
            title = { Text(stringResource(R.string.diag_lab_live_update_text_limits_dialog_title)) },
            text = { Text(stringResource(R.string.diag_lab_live_update_text_limits_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LabFeatureManager.setLiveUpdateTextLimitsEnabled(context, true)
                        liveUpdateTextLimitsEnabled = true
                        showLiveUpdateTextLimitsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.diag_lab_live_update_text_limits_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveUpdateTextLimitsDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
