package com.example.islandlyrics.feature.lab.miuix

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
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
    val showAdvancedStyleDialog = remember { mutableStateOf(false) }

    MiuixBackHandler(enabled = showAdvancedStyleDialog.value) {
        showAdvancedStyleDialog.value = false
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.title_lab),
                largeTitle = stringResource(R.string.title_lab),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.online_lyric_debug_back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
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
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item { SmallTitle(text = stringResource(R.string.diag_header_laboratory)) }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_full_offline_mode),
                        summary = stringResource(R.string.settings_full_offline_mode_desc),
                        checked = offlineModeEnabled,
                        onCheckedChange = {
                            offlineModeEnabled = it
                            OfflineModeManager.setEnabled(context, it)
                        }
                    )

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
                    }
                }
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
    }
}
