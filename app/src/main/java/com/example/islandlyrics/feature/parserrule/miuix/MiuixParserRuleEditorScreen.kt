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

package com.example.islandlyrics.feature.parserrule.miuix

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.rules.FieldOrder
import com.example.islandlyrics.rules.ParserRule
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorState
import com.example.islandlyrics.feature.parserrule.ParserRuleSourceConfigType
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.feature.parserrule.withSourceSettingsFrom
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurBottomSheet
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurSmallTopAppBar
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.effects.miuixPageScroll
import com.example.islandlyrics.ui.miuix.reorderable.MiuixBlurReorderablePanel
import com.example.islandlyrics.ui.miuix.reorderable.MiuixReorderableListItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import com.example.islandlyrics.ui.miuix.preference.BlurOverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import com.example.islandlyrics.ui.miuix.blur.BlurOverlayIconDropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixParserRuleEditorScreen(
    initialRule: ParserRule,
    isNewRule: Boolean,
    isTemplate: Boolean = false,
    onBack: () -> Unit,
    onDelete: (ParserRule) -> Unit,
    onSaved: (ParserRule) -> Unit,
    onOpenFaq: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val enterPkgMessage = stringResource(R.string.dialog_enter_pkg)
    var state by remember(initialRule) { mutableStateOf(initialRule.toEditorState()) }
    var showOnlineSuggestionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sourceConfigSheet by remember { mutableStateOf<ParserRuleSourceConfigType?>(null) }
    var showOnlineProviderOrderSheet by remember { mutableStateOf(false) }
    val canPersistSourceSettings = !isTemplate && !isNewRule && state.packageName.isNotBlank()

    DisposableEffect(canPersistSourceSettings, state.packageName) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && canPersistSourceSettings) {
                val latest = ParserRuleHelper.getRuleForPackage(context, state.packageName)
                    ?: ParserRuleHelper.loadRules(context).firstOrNull { it.packageName == state.packageName }
                if (latest != null) {
                    state = state.withSourceSettingsFrom(latest.toEditorState())
                }
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    fun updateSourceState(next: ParserRuleEditorState) {
        state = next
        if (canPersistSourceSettings) {
            ParserRuleHelper.updateRule(context, state.packageName) { current ->
                current.copy(
                    usesCarProtocol = next.usesCarProtocol,
                    separatorPattern = next.separator,
                    fieldOrder = next.fieldOrder,
                    useLocalLyrics = next.useLocalLyrics,
                    useOnlineLyrics = next.useOnlineLyrics,
                    useSmartOnlineLyricSelection = next.useSmartOnlineLyricSelection,
                    useRawMetadataForOnlineMatching = next.useRawMetadataForOnlineMatching,
                    receiveOnlineTranslation = next.receiveOnlineTranslation,
                    receiveOnlineRomanization = next.receiveOnlineRomanization,
                    onlineLyricProviderOrder = next.onlineLyricProviderOrder.map { it.id },
                    onlineLyricDisabledProviders = next.onlineLyricDisabledProviders.map { it.id }.toSet(),
                    appleMusicStorefrontOverride = next.appleMusicStorefrontOverride,
                    appleMusicLanguageOverride = next.appleMusicLanguageOverride,
                    useSuperLyricApi = next.useSuperLyricApi,
                    useLyricGetterApi = next.useLyricGetterApi,
                    useLyriconApi = next.useLyriconApi,
                    receiveLyriconTranslation = next.receiveLyriconTranslation,
                    receiveLyriconRomanization = next.receiveLyriconRomanization,
                    useLastFmScrobble = next.useLastFmScrobble
                )
            }
        }
    }

    fun openSourceConfig(type: ParserRuleSourceConfigType) {
        sourceConfigSheet = type
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurSmallTopAppBar(
                title = when {
                    isTemplate -> stringResource(R.string.parser_template_title)
                    isNewRule -> stringResource(R.string.parser_add_rule)
                    else -> stringResource(R.string.parser_edit)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onBack()
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    val menuItems = buildList {
                        add(
                            DropdownItem(
                                text = stringResource(if (isTemplate) R.string.parser_template_save else R.string.parser_save_rule),
                                onClick = {
                                    if (!isTemplate && state.packageName.isBlank()) {
                                        Toast.makeText(context, enterPkgMessage, Toast.LENGTH_SHORT).show()
                                    } else {
                                        onSaved(state.toRule(initialRule))
                                    }
                                }
                            )
                        )
                        add(
                            DropdownItem(
                                text = stringResource(R.string.faq_title),
                                onClick = {
                                    onOpenFaq?.invoke()
                                        ?: context.startActivity(Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                                }
                            )
                        )
                        if (isTemplate || !isNewRule) {
                            add(
                                DropdownItem(
                                    text = stringResource(if (isTemplate) R.string.parser_template_clear else R.string.parser_delete),
                                    onClick = {
                                        showDeleteDialog = true
                                    }
                                )
                            )
                        }
                    }

                    BlurOverlayIconDropdownMenu(
                        entry = DropdownEntry(items = menuItems),
                        itemColors = if (isTemplate || !isNewRule) {
                            mapOf(2 to DropdownDefaults.dropdownColors(contentColor = MiuixTheme.colorScheme.error))
                        } else {
                            emptyMap()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .miuixPageScroll(scrollBehavior)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            if (!isTemplate) {
                SmallTitle(text = stringResource(R.string.parser_app_info))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextField(
                        value = state.customName,
                        onValueChange = { state = state.copy(customName = it) },
                        label = stringResource(R.string.parser_app_name),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = state.packageName,
                        onValueChange = { state = state.copy(packageName = it) },
                        label = stringResource(R.string.parser_package_name),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isNewRule
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            SmallTitle(text = stringResource(R.string.parser_logic_header))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                MiuixSourceRows(
                    state = state,
                    onStateChange = ::updateSourceState,
                    onNavigate = ::openSourceConfig,
                    onShowOnlineSuggestion = { showOnlineSuggestionDialog = true }
                )
            }

            if (!isTemplate) {
                Spacer(modifier = Modifier.height(16.dp))
                SmallTitle(text = stringResource(R.string.parser_lastfm_header))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SuperSwitch(
                        title = stringResource(R.string.parser_lastfm_scrobble),
                        summary = stringResource(R.string.parser_lastfm_scrobble_desc),
                        checked = state.useLastFmScrobble,
                        onCheckedChange = { updateSourceState(state.copy(useLastFmScrobble = it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!isTemplate && state.packageName.isBlank()) {
                        Toast.makeText(context, enterPkgMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        onSaved(state.toRule(initialRule))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = 24.dp
            ) {
                Text(stringResource(if (isTemplate) R.string.parser_template_save else R.string.parser_save_rule))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showOnlineSuggestionDialog) {
            MiuixBlurDialog(
                title = stringResource(R.string.parser_online_conflict_title),
                summary = stringResource(R.string.parser_online_conflict_message),
                show = true,
                onDismissRequest = {
                    updateSourceState(state.copy(useOnlineLyrics = false))
                    showOnlineSuggestionDialog = false
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            updateSourceState(state.copy(usesCarProtocol = false, useOnlineLyrics = true))
                            showOnlineSuggestionDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.parser_online_conflict_disable_notify))
                    }
                    Button(
                        onClick = {
                            updateSourceState(state.copy(useOnlineLyrics = false))
                            showOnlineSuggestionDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.parser_online_conflict_keep))
                    }
                }
            }
        }

        if (showDeleteDialog) {
            MiuixBlurDialog(
                title = stringResource(if (isTemplate) R.string.parser_template_clear else R.string.parser_delete),
                summary = if (isTemplate) {
                    stringResource(R.string.parser_template_clear_confirm)
                } else {
                    stringResource(R.string.dialog_delete_confirm, state.customName.ifBlank { state.packageName })
                },
                show = true,
                onDismissRequest = { showDeleteDialog = false }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onDelete(initialRule)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                    Button(
                        onClick = { showDeleteDialog = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }

        val sourceConfigSheetType = sourceConfigSheet
        if (sourceConfigSheetType != null) {
            MiuixBlurBottomSheet(
                show = true,
                title = when (sourceConfigSheetType) {
                    ParserRuleSourceConfigType.NOTIFICATION -> stringResource(R.string.parser_car_protocol)
                    ParserRuleSourceConfigType.ONLINE -> stringResource(R.string.settings_use_online_lyrics)
                    ParserRuleSourceConfigType.LYRICON -> stringResource(R.string.parser_lyricon_lyric)
                },
                onDismissRequest = {
                    sourceConfigSheet = null
                    showOnlineProviderOrderSheet = false
                },
                startAction = {
                    IconButton(onClick = {
                        sourceConfigSheet = null
                        showOnlineProviderOrderSheet = false
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.backup_dialog_cancel),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            ) {
                when (sourceConfigSheetType) {
                    ParserRuleSourceConfigType.NOTIFICATION ->
                        MiuixNotificationSourceConfigPage(state, ::updateSourceState)
                    ParserRuleSourceConfigType.ONLINE ->
                        MiuixOnlineSourceConfigPage(
                            state = state,
                            onStateChange = ::updateSourceState,
                            onOpenProviderOrder = { showOnlineProviderOrderSheet = true }
                        )
                    ParserRuleSourceConfigType.LYRICON ->
                        MiuixLyriconSourceConfigPage(state, ::updateSourceState)
                }
            }
        }

        MiuixOnlineProviderOrderSheet(
            state = state,
            onStateChange = ::updateSourceState,
            show = showOnlineProviderOrderSheet,
            onDismiss = { showOnlineProviderOrderSheet = false }
        )
    }
}

@Composable
private fun MiuixSourceRows(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    onNavigate: (ParserRuleSourceConfigType) -> Unit,
    onShowOnlineSuggestion: () -> Unit
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)

    MiuixSwitchArrowPreference(
        title = stringResource(R.string.parser_car_protocol),
        summary = stringResource(R.string.parser_notify_lyric_desc),
        checked = state.usesCarProtocol,
        onCheckedChange = {
            onStateChange(
                state.copy(
                    usesCarProtocol = it,
                    useOnlineLyrics = if (it) false else state.useOnlineLyrics
                )
            )
        },
        onArrowClick = { onNavigate(ParserRuleSourceConfigType.NOTIFICATION) }
    )
    SuperSwitch(
        title = stringResource(R.string.parser_local_lyric),
        summary = stringResource(R.string.parser_local_lyric_desc_short),
        checked = state.useLocalLyrics,
        onCheckedChange = { onStateChange(state.copy(useLocalLyrics = it)) }
    )
    MiuixSwitchArrowPreference(
        title = stringResource(R.string.settings_use_online_lyrics),
        summary = stringResource(R.string.parser_online_lyric_desc_short),
        checked = state.useOnlineLyrics,
        enabled = !offlineModeEnabled,
        onCheckedChange = {
            onStateChange(
                state.copy(
                    useOnlineLyrics = it,
                    useSmartOnlineLyricSelection = if (it) true else state.useSmartOnlineLyricSelection
                )
            )
            if (it && state.usesCarProtocol) onShowOnlineSuggestion()
        },
        onArrowClick = { onNavigate(ParserRuleSourceConfigType.ONLINE) }
    )
    SuperSwitch(
        title = stringResource(R.string.parser_super_lyric),
        summary = stringResource(R.string.parser_super_lyric_desc_short),
        checked = state.useSuperLyricApi,
        onCheckedChange = { onStateChange(state.copy(useSuperLyricApi = it)) }
    )
    SuperSwitch(
        title = stringResource(R.string.parser_lgetter_lyric),
        summary = stringResource(R.string.parser_lgetter_lyric_desc_short),
        checked = state.useLyricGetterApi,
        onCheckedChange = { onStateChange(state.copy(useLyricGetterApi = it)) }
    )
    MiuixSwitchArrowPreference(
        title = stringResource(R.string.parser_lyricon_lyric),
        summary = stringResource(R.string.parser_lyricon_lyric_desc_short),
        checked = state.useLyriconApi,
        onCheckedChange = { onStateChange(state.copy(useLyriconApi = it)) },
        onArrowClick = { onNavigate(ParserRuleSourceConfigType.LYRICON) }
    )
}

@Composable
fun MiuixParserRuleSourceConfigScreen(
    configType: ParserRuleSourceConfigType,
    initialRule: ParserRule,
    onBack: () -> Unit,
    onStateChange: (ParserRuleEditorState) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var state by remember(initialRule) { mutableStateOf(initialRule.toEditorState()) }
    fun updateState(next: ParserRuleEditorState) {
        state = next
        onStateChange(next)
    }
    var showProviderOrderSheet by remember { mutableStateOf(false) }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = when (configType) {
                    ParserRuleSourceConfigType.NOTIFICATION -> stringResource(R.string.parser_car_protocol)
                    ParserRuleSourceConfigType.ONLINE -> stringResource(R.string.settings_use_online_lyrics)
                    ParserRuleSourceConfigType.LYRICON -> stringResource(R.string.parser_lyricon_lyric)
                },
                largeTitle = when (configType) {
                    ParserRuleSourceConfigType.NOTIFICATION -> stringResource(R.string.parser_car_protocol)
                    ParserRuleSourceConfigType.ONLINE -> stringResource(R.string.settings_use_online_lyrics)
                    ParserRuleSourceConfigType.LYRICON -> stringResource(R.string.parser_lyricon_lyric)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                }
            )
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .miuixPageScroll(scrollBehavior)
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                when (configType) {
                    ParserRuleSourceConfigType.NOTIFICATION -> MiuixNotificationSourceConfigPage(state, ::updateState)
                    ParserRuleSourceConfigType.ONLINE -> MiuixOnlineSourceConfigPage(
                        state = state,
                        onStateChange = ::updateState,
                        onOpenProviderOrder = { showProviderOrderSheet = true }
                    )
                    ParserRuleSourceConfigType.LYRICON -> MiuixLyriconSourceConfigPage(state, ::updateState)
                }
            }
        }

        MiuixOnlineProviderOrderSheet(
            state = state,
            onStateChange = ::updateState,
            show = showProviderOrderSheet,
            onDismiss = { showProviderOrderSheet = false }
        )
    }
}

@Composable
fun MiuixNotificationSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    val separators = listOf("-", " - ", " | ")
    val orders = listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST)
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SuperDropdown(
                title = stringResource(R.string.parser_separator_label),
                items = separators,
                selectedIndex = separators.indexOf(state.separator).coerceAtLeast(0),
                onSelectedIndexChange = { onStateChange(state.copy(separator = separators[it])) }
            )
            SuperDropdown(
                title = stringResource(R.string.parser_field_order_label),
                items = orders.map {
                    if (it == FieldOrder.ARTIST_TITLE) stringResource(R.string.parser_order_artist_title)
                    else stringResource(R.string.parser_order_title_artist)
                },
                selectedIndex = orders.indexOf(state.fieldOrder).coerceAtLeast(0),
                onSelectedIndexChange = { onStateChange(state.copy(fieldOrder = orders[it])) }
            )
        }
    }
}

@Composable
fun MiuixOnlineSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    onOpenProviderOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SuperSwitch(
                title = stringResource(R.string.parser_smart_online_fetch),
                summary = stringResource(R.string.parser_smart_online_fetch_desc),
                checked = state.useSmartOnlineLyricSelection,
                onCheckedChange = { onStateChange(state.copy(useSmartOnlineLyricSelection = it)) }
            )
            SuperSwitch(
                title = stringResource(R.string.parser_use_raw_metadata_for_online_match),
                summary = stringResource(R.string.parser_use_raw_metadata_for_online_match_desc),
                checked = state.useRawMetadataForOnlineMatching,
                onCheckedChange = { onStateChange(state.copy(useRawMetadataForOnlineMatching = it)) }
            )
            SuperSwitch(
                title = stringResource(R.string.parser_receive_translation),
                summary = stringResource(R.string.parser_online_translation_desc),
                checked = state.receiveOnlineTranslation,
                onCheckedChange = { onStateChange(state.copy(receiveOnlineTranslation = it)) }
            )
            SuperSwitch(
                title = stringResource(R.string.parser_receive_romanization),
                summary = stringResource(R.string.parser_online_romanization_desc),
                checked = state.receiveOnlineRomanization,
                onCheckedChange = { onStateChange(state.copy(receiveOnlineRomanization = it)) }
            )
            if (!state.useSmartOnlineLyricSelection) {
                val orderSummary = state.onlineLyricProviderOrder
                    .filterNot { it in state.onlineLyricDisabledProviders }
                    .joinToString(" > ") { it.displayName(context) }
                SuperArrow(
                    title = stringResource(R.string.parser_online_priority),
                    summary = stringResource(R.string.parser_online_priority_summary, orderSummary),
                    onClick = onOpenProviderOrder
                )
            }
            MiuixAppleMusicOverrideSection(state, onStateChange)
        }
    }
}

private val appleStorefrontOptions = listOf(
    "us" to R.string.apple_music_storefront_us,
    "cn" to R.string.apple_music_storefront_cn,
    "jp" to R.string.apple_music_storefront_jp,
    "hk" to R.string.apple_music_storefront_hk,
    "gb" to R.string.apple_music_storefront_gb
)

private val appleLanguageOptions = listOf(
    "en-US" to R.string.apple_music_language_en_us,
    "zh-Hans" to R.string.apple_music_language_zh_hans,
    "zh-Hant" to R.string.apple_music_language_zh_hant,
    "ja" to R.string.apple_music_language_ja
)

@Composable
private fun MiuixAppleMusicOverrideSection(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val overrideActive =
        state.appleMusicStorefrontOverride != null || state.appleMusicLanguageOverride != null

    SuperArrow(
        title = stringResource(R.string.parser_apple_music_override),
        summary = if (overrideActive) {
            stringResource(R.string.apple_music_custom_override)
        } else {
            stringResource(R.string.apple_music_follow_global)
        },
        onClick = { expanded = !expanded }
    )
    if (expanded) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SuperSwitch(
                title = stringResource(R.string.apple_music_follow_global),
                summary = stringResource(R.string.parser_apple_music_override_desc),
                checked = !overrideActive,
                onCheckedChange = { follow ->
                    if (follow) {
                        onStateChange(
                            state.copy(
                                appleMusicStorefrontOverride = null,
                                appleMusicLanguageOverride = null
                            )
                        )
                    } else {
                        val prefs = AppPreferences.of(context)
                        onStateChange(
                            state.copy(
                                appleMusicStorefrontOverride = AppPreferences.appleMusicStorefront(prefs),
                                appleMusicLanguageOverride = AppPreferences.appleMusicLanguage(prefs)
                            )
                        )
                    }
                }
            )
            if (overrideActive) {
                SuperDropdown(
                    title = stringResource(R.string.apple_music_storefront),
                    items = appleStorefrontOptions.map { stringResource(it.second) },
                    selectedIndex = appleStorefrontOptions
                        .indexOfFirst { it.first == state.appleMusicStorefrontOverride }
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        onStateChange(
                            state.copy(appleMusicStorefrontOverride = appleStorefrontOptions[index].first)
                        )
                    }
                )
                SuperDropdown(
                    title = stringResource(R.string.apple_music_language),
                    items = appleLanguageOptions.map { stringResource(it.second) },
                    selectedIndex = appleLanguageOptions
                        .indexOfFirst { it.first == state.appleMusicLanguageOverride }
                        .coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        onStateChange(
                            state.copy(appleMusicLanguageOverride = appleLanguageOptions[index].first)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun MiuixLyriconSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SuperSwitch(
                title = stringResource(R.string.parser_receive_translation),
                summary = stringResource(R.string.parser_lyricon_translation_desc),
                checked = state.receiveLyriconTranslation,
                onCheckedChange = { onStateChange(state.copy(receiveLyriconTranslation = it)) }
            )
            SuperSwitch(
                title = stringResource(R.string.parser_receive_romanization),
                summary = stringResource(R.string.parser_lyricon_romanization_desc),
                checked = state.receiveLyriconRomanization,
                onCheckedChange = { onStateChange(state.copy(receiveLyriconRomanization = it)) }
            )
        }
    }
}

@Composable
private fun MiuixSwitchArrowPreference(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onArrowClick: () -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = {
            if (checked) {
                onArrowClick()
            } else {
                onCheckedChange(true)
            }
        },
        endActions = {
            IconButton(
                onClick = onArrowClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    )
}

@Composable
private fun MiuixOnlineProviderOrderSheet(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    MiuixBlurBottomSheet(
        show = show,
        title = stringResource(R.string.parser_online_priority),
        onDismissRequest = onDismiss,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.backup_dialog_cancel),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        },
        endAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.backup_dialog_confirm),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiuixBlurReorderablePanel(
                items = state.onlineLyricProviderOrder.map { provider ->
                    MiuixReorderableListItem(
                        id = provider.id,
                        title = provider.displayName(context),
                        checked = provider !in state.onlineLyricDisabledProviders
                    )
                },
                onMove = { from, to ->
                    onStateChange(
                        state.copy(
                            onlineLyricProviderOrder = state.onlineLyricProviderOrder.moveItem(from, to)
                        )
                    )
                },
                onCheckedChange = { id, checked ->
                    val provider = OnlineLyricProvider.fromId(id) ?: return@MiuixBlurReorderablePanel
                    val nextDisabled = if (checked) {
                        state.onlineLyricDisabledProviders - provider
                    } else {
                        state.onlineLyricDisabledProviders + provider
                    }
                    onStateChange(state.copy(onlineLyricDisabledProviders = nextDisabled))
                },
                onReset = {
                    onStateChange(
                        state.copy(
                            onlineLyricProviderOrder = OnlineLyricProvider.defaultOrderForPackage(state.packageName),
                            onlineLyricDisabledProviders = emptySet()
                        )
                    )
                },
                resetLabel = stringResource(R.string.parser_reset_online_priority),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply {
        val item = removeAt(from)
        add(to, item)
    }
}
