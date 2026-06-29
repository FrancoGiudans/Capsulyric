package com.example.islandlyrics.feature.lab.miuix

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackHandler
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
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
    var liveUpdateTextLimitsEnabled by remember {
        mutableStateOf(LabFeatureManager.isLiveUpdateTextLimitsEnabled(context))
    }
    var floatingLyricsLabEnabled by remember {
        mutableStateOf(LabFeatureManager.isFloatingLyricsEnabled(context))
    }
    var experimentUpdatesEnabled by remember {
        mutableStateOf(LabFeatureManager.isExperimentUpdatesEnabled(context))
    }
    var scrollEndHapticEnabled by remember {
        mutableStateOf(LabFeatureManager.isScrollEndHapticEnabled(context))
    }
    val showOfflineModeDialog = remember { mutableStateOf(false) }
    val showAdvancedStyleDialog = remember { mutableStateOf(false) }
    val showLiveUpdateTextLimitsDialog = remember { mutableStateOf(false) }

    MiuixBackHandler(
        enabled = showOfflineModeDialog.value || showAdvancedStyleDialog.value || showLiveUpdateTextLimitsDialog.value
    ) {
        showOfflineModeDialog.value = false
        showAdvancedStyleDialog.value = false
        showLiveUpdateTextLimitsDialog.value = false
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.title_lab),
                largeTitle = stringResource(R.string.title_lab),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        MiuixBackIcon(contentDescription = stringResource(R.string.online_lyric_debug_back))
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .miuixPageScroll(scrollBehavior),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item {
                Text(
                    text = stringResource(R.string.diag_lab_page_desc),
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
            }

            item { SmallTitle(text = stringResource(R.string.diag_lab_category_general)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_full_offline_mode),
                        summary = stringResource(R.string.settings_full_offline_mode_desc),
                        checked = offlineModeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showOfflineModeDialog.value = true
                            } else {
                                offlineModeEnabled = false
                                OfflineModeManager.setEnabled(context, false)
                            }
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.diag_lab_scroll_end_haptic_title),
                        summary = stringResource(R.string.diag_lab_scroll_end_haptic_desc),
                        checked = scrollEndHapticEnabled,
                        onCheckedChange = {
                            scrollEndHapticEnabled = it
                            LabFeatureManager.setScrollEndHapticEnabled(context, it)
                        }
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.diag_lab_category_interface)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    if (RomUtils.isXiaomi()) {
                        SuperSwitch(
                            title = stringResource(R.string.diag_lab_super_island_advanced_style_title),
                            summary = stringResource(R.string.diag_lab_super_island_advanced_style_desc),
                            checked = superIslandAdvancedStyleEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showAdvancedStyleDialog.value = true
                                } else {
                                    LabFeatureManager.setSuperIslandAdvancedStyleEnabled(context, false)
                                    superIslandAdvancedStyleEnabled = false
                                }
                            }
                        )

                        SuperSwitch(
                            title = stringResource(R.string.diag_lab_super_island_text_limits_title),
                            summary = stringResource(R.string.diag_lab_super_island_text_limits_desc),
                            checked = superIslandTextLimitsEnabled,
                            onCheckedChange = {
                                superIslandTextLimitsEnabled = it
                                LabFeatureManager.setSuperIslandTextLimitsEnabled(context, it)
                            }
                        )

                        SuperSwitch(
                            title = stringResource(R.string.diag_lab_super_island_relaxed_text_limits_title),
                            summary = stringResource(R.string.diag_lab_super_island_relaxed_text_limits_desc),
                            checked = superIslandRelaxedTextLimitsEnabled,
                            onCheckedChange = {
                                superIslandRelaxedTextLimitsEnabled = it
                                LabFeatureManager.setSuperIslandRelaxedTextLimitsEnabled(context, it)
                            }
                        )

                    }

                    SuperSwitch(
                        title = stringResource(R.string.diag_lab_live_update_text_limits_title),
                        summary = stringResource(R.string.diag_lab_live_update_text_limits_desc),
                        checked = liveUpdateTextLimitsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showLiveUpdateTextLimitsDialog.value = true
                            } else {
                                LabFeatureManager.setLiveUpdateTextLimitsEnabled(context, false)
                                liveUpdateTextLimitsEnabled = false
                            }
                        }
                    )

                    SuperSwitch(
                        title = stringResource(R.string.diag_lab_floating_lyrics_title),
                        summary = stringResource(R.string.diag_lab_floating_lyrics_desc),
                        checked = floatingLyricsLabEnabled,
                        onCheckedChange = {
                            floatingLyricsLabEnabled = it
                            LabFeatureManager.setFloatingLyricsEnabled(context, it)
                        }
                    )
                }
            }

            item { SmallTitle(text = stringResource(R.string.diag_lab_category_updates)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.diag_lab_experiment_updates_title),
                        summary = stringResource(R.string.diag_lab_experiment_updates_desc),
                        checked = experimentUpdatesEnabled,
                        onCheckedChange = {
                            experimentUpdatesEnabled = it
                            LabFeatureManager.setExperimentUpdatesEnabled(context, it)
                        }
                    )

                }
            }
        }

        MiuixBlurDialog(
            title = stringResource(R.string.settings_full_offline_mode_dialog_title),
            summary = stringResource(R.string.settings_full_offline_mode_dialog_message),
            show = showOfflineModeDialog.value,
            onDismissRequest = { showOfflineModeDialog.value = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showOfflineModeDialog.value = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.settings_full_offline_mode_dialog_confirm),
                    onClick = {
                        OfflineModeManager.setEnabled(context, true)
                        offlineModeEnabled = true
                        showOfflineModeDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }

        MiuixBlurDialog(
            title = stringResource(R.string.diag_lab_super_island_dialog_title),
            summary = stringResource(R.string.diag_lab_super_island_dialog_message),
            show = showAdvancedStyleDialog.value,
            onDismissRequest = { showAdvancedStyleDialog.value = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showAdvancedStyleDialog.value = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.diag_lab_super_island_dialog_confirm),
                    onClick = {
                        LabFeatureManager.setSuperIslandAdvancedStyleEnabled(context, true)
                        superIslandAdvancedStyleEnabled = true
                        showAdvancedStyleDialog.value = false
                        context.startActivity(Intent(context, CustomSettingsActivity::class.java))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }

        MiuixBlurDialog(
            title = stringResource(R.string.diag_lab_live_update_text_limits_dialog_title),
            summary = stringResource(R.string.diag_lab_live_update_text_limits_dialog_message),
            show = showLiveUpdateTextLimitsDialog.value,
            onDismissRequest = { showLiveUpdateTextLimitsDialog.value = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showLiveUpdateTextLimitsDialog.value = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.diag_lab_live_update_text_limits_dialog_confirm),
                    onClick = {
                        LabFeatureManager.setLiveUpdateTextLimitsEnabled(context, true)
                        liveUpdateTextLimitsEnabled = true
                        showLiveUpdateTextLimitsDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}


