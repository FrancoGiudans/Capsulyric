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

package com.example.islandlyrics.feature.parserrule.material

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.rules.FieldOrder
import com.example.islandlyrics.rules.ParserRule
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorState
import com.example.islandlyrics.feature.parserrule.ParserRuleSourceConfigActivity
import com.example.islandlyrics.feature.parserrule.ParserRuleSourceConfigType
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.feature.parserrule.withSourceSettingsFrom
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.ui.navigation.PageStackHost
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleEditorScreen(
    initialRule: ParserRule,
    isNewRule: Boolean,
    isTemplate: Boolean = false,
    onBack: () -> Unit,
    onDelete: (ParserRule) -> Unit,
    onSaved: (ParserRule) -> Unit,
    onOpenFaq: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val enterPkgMessage = stringResource(R.string.dialog_enter_pkg)
    var state by remember(initialRule) { mutableStateOf(initialRule.toEditorState()) }
    var showOnlineSuggestionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val templateConfigType = remember { mutableStateOf<ParserRuleSourceConfigType?>(null) }
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
        if (isTemplate) {
            templateConfigType.value = type
            return
        }
        if (state.packageName.isBlank()) {
            Toast.makeText(context, enterPkgMessage, Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(
            Intent(context, ParserRuleSourceConfigActivity::class.java).apply {
                putExtra(ParserRuleSourceConfigActivity.EXTRA_PACKAGE_NAME, state.packageName)
                putExtra(ParserRuleSourceConfigActivity.EXTRA_CONFIG_TYPE, type.name)
            }
        )
    }

    val editorContent: @Composable () -> Unit = {
    MaterialBlurScaffold(
        topBar = {
            MaterialBlurTopAppBar(
                title = {
                    Text(
                        when {
                            isTemplate -> stringResource(R.string.parser_template_title)
                            isNewRule -> stringResource(R.string.parser_add_rule)
                            else -> stringResource(R.string.parser_edit)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!isTemplate && state.packageName.isBlank()) {
                            Toast.makeText(context, enterPkgMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            onSaved(state.toRule(initialRule))
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.parser_save_rule))
                    }
                    IconButton(onClick = {
                        onOpenFaq?.invoke()
                            ?: context.startActivity(Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                    }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.faq_title))
                    }
                    if (isTemplate || !isNewRule) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(
                                    if (isTemplate) R.string.parser_template_clear else R.string.parser_delete
                                )
                            )
                        }
                    }
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(padding.calculateTopPadding()))
            Spacer(modifier = Modifier.height(8.dp))
            if (!isTemplate) {
                SettingsSectionHeader(stringResource(R.string.parser_app_info))
                OutlinedTextField(
                    value = state.customName,
                    onValueChange = { state = state.copy(customName = it) },
                    label = { Text(stringResource(R.string.parser_app_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.packageName,
                    onValueChange = { state = state.copy(packageName = it) },
                    label = { Text(stringResource(R.string.parser_package_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = isNewRule
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            SettingsSectionHeader(stringResource(R.string.parser_logic_desc))
            SourceCard {
                MaterialSourceRows(
                    state = state,
                    onStateChange = ::updateSourceState,
                    onNavigate = ::openSourceConfig,
                    onShowOnlineSuggestion = { showOnlineSuggestionDialog = true }
                )
            }
            if (!isTemplate) {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(stringResource(R.string.parser_lastfm_header))
                SourceCard {
                    SwitchRow(
                        title = stringResource(R.string.parser_lastfm_scrobble),
                        subtitle = stringResource(R.string.parser_lastfm_scrobble_desc),
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isTemplate) R.string.parser_template_save else R.string.parser_save_rule))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(padding.calculateBottomPadding()))
        }
    }

    if (showOnlineSuggestionDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showOnlineSuggestionDialog = false },
            title = { Text(stringResource(R.string.parser_online_conflict_title)) },
            text = { Text(stringResource(R.string.parser_online_conflict_message)) },
            confirmButton = {
                TextButton(onClick = {
                    updateSourceState(state.copy(usesCarProtocol = false))
                    showOnlineSuggestionDialog = false
                }) {
                    Text(stringResource(R.string.parser_online_conflict_disable_notify))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOnlineSuggestionDialog = false }) {
                    Text(stringResource(R.string.parser_online_conflict_keep))
                }
            }
        )
    }

    if (showDeleteDialog) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(if (isTemplate) R.string.parser_template_clear else R.string.parser_delete)) },
            text = {
                Text(
                    if (isTemplate) {
                        stringResource(R.string.parser_template_clear_confirm)
                    } else {
                        stringResource(R.string.dialog_delete_confirm, state.customName.ifBlank { state.packageName })
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(initialRule)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    }

    if (isTemplate) {
        val editingTemplateConfigType = templateConfigType.value
        PageStackHost(
            stack = listOfNotNull(editingTemplateConfigType),
            onPop = { templateConfigType.value = null },
            backdropColor = materialPageContainerColor(),
            modifier = Modifier.fillMaxSize(),
            backgroundContent = { editorContent() },
            pageContent = { configType ->
                ParserRuleSourceConfigScreen(
                    configType = configType,
                    initialRule = state.toRule(initialRule),
                    onBack = { templateConfigType.value = null },
                    onStateChange = { state = it }
                )
            }
        )
    } else {
        editorContent()
    }
}

@Composable
private fun MaterialSourceRows(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    onNavigate: (ParserRuleSourceConfigType) -> Unit,
    onShowOnlineSuggestion: () -> Unit
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    SwitchArrowRow(
        title = stringResource(R.string.parser_car_protocol),
        subtitle = stringResource(R.string.parser_notify_lyric_desc),
        checked = state.usesCarProtocol,
        onCheckedChange = { onStateChange(state.copy(usesCarProtocol = it)) },
        onArrowClick = { onNavigate(ParserRuleSourceConfigType.NOTIFICATION) }
    )
    SwitchRow(
        title = stringResource(R.string.parser_local_lyric),
        subtitle = stringResource(R.string.parser_local_lyric_desc_short),
        checked = state.useLocalLyrics,
        onCheckedChange = { onStateChange(state.copy(useLocalLyrics = it)) }
    )
    SwitchArrowRow(
        title = stringResource(R.string.settings_use_online_lyrics),
        subtitle = stringResource(R.string.parser_online_lyric_desc_short),
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
    SwitchRow(
        title = stringResource(R.string.parser_super_lyric),
        subtitle = stringResource(R.string.parser_super_lyric_desc_short),
        checked = state.useSuperLyricApi,
        onCheckedChange = { onStateChange(state.copy(useSuperLyricApi = it)) }
    )
    SwitchRow(
        title = stringResource(R.string.parser_lgetter_lyric),
        subtitle = stringResource(R.string.parser_lgetter_lyric_desc_short),
        checked = state.useLyricGetterApi,
        onCheckedChange = { onStateChange(state.copy(useLyricGetterApi = it)) }
    )
    SwitchArrowRow(
        title = stringResource(R.string.parser_lyricon_lyric),
        subtitle = stringResource(R.string.parser_lyricon_lyric_desc_short),
        checked = state.useLyriconApi,
        onCheckedChange = { onStateChange(state.copy(useLyriconApi = it)) },
        onArrowClick = { onNavigate(ParserRuleSourceConfigType.LYRICON) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleSourceConfigScreen(
    configType: ParserRuleSourceConfigType,
    initialRule: ParserRule,
    onBack: () -> Unit,
    onStateChange: (ParserRuleEditorState) -> Unit
) {
    var state by remember(initialRule) { mutableStateOf(initialRule.toEditorState()) }
    fun updateState(next: ParserRuleEditorState) {
        state = next
        onStateChange(next)
    }

    MaterialBlurScaffold(
        topBar = {
            MaterialBlurTopAppBar(
                title = {
                    Text(
                        when (configType) {
                            ParserRuleSourceConfigType.NOTIFICATION -> stringResource(R.string.parser_car_protocol)
                            ParserRuleSourceConfigType.ONLINE -> stringResource(R.string.settings_use_online_lyrics)
                            ParserRuleSourceConfigType.LYRICON -> stringResource(R.string.parser_lyricon_lyric)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
        when (configType) {
            ParserRuleSourceConfigType.NOTIFICATION -> MaterialNotificationSourceConfigPage(state, ::updateState, modifier)
            ParserRuleSourceConfigType.ONLINE -> MaterialOnlineSourceConfigPage(state, ::updateState, modifier)
            ParserRuleSourceConfigType.LYRICON -> MaterialLyriconSourceConfigPage(state, ::updateState, modifier)
        }
    }
}

@Composable
fun MaterialNotificationSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        SourceCard {
            Text(stringResource(R.string.parser_separator_label), color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("-", " - ", " | ").forEach { separator ->
                    FilterChip(
                        selected = state.separator == separator,
                        onClick = { onStateChange(state.copy(separator = separator)) },
                        label = {
                            Text(
                                when (separator) {
                                    "-" -> stringResource(R.string.parser_separator_tight)
                                    " - " -> stringResource(R.string.parser_separator_spaced)
                                    else -> stringResource(R.string.parser_separator_pipe)
                                }
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.parser_field_order_label), color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST).forEach { order ->
                    FilterChip(
                        selected = state.fieldOrder == order,
                        onClick = { onStateChange(state.copy(fieldOrder = order)) },
                        label = {
                            Text(
                                if (order == FieldOrder.ARTIST_TITLE) {
                                    stringResource(R.string.parser_order_artist_title)
                                } else {
                                    stringResource(R.string.parser_order_title_artist)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MaterialOnlineSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        SourceCard {
            SwitchRow(
                title = stringResource(R.string.parser_smart_online_fetch),
                subtitle = stringResource(R.string.parser_smart_online_fetch_desc),
                checked = state.useSmartOnlineLyricSelection,
                onCheckedChange = { onStateChange(state.copy(useSmartOnlineLyricSelection = it)) }
            )
            SwitchRow(
                title = stringResource(R.string.parser_use_raw_metadata_for_online_match),
                subtitle = stringResource(R.string.parser_use_raw_metadata_for_online_match_desc),
                checked = state.useRawMetadataForOnlineMatching,
                onCheckedChange = { onStateChange(state.copy(useRawMetadataForOnlineMatching = it)) }
            )
            SwitchRow(
                title = stringResource(R.string.parser_receive_translation),
                subtitle = stringResource(R.string.parser_online_translation_desc),
                checked = state.receiveOnlineTranslation,
                onCheckedChange = { onStateChange(state.copy(receiveOnlineTranslation = it)) }
            )
            SwitchRow(
                title = stringResource(R.string.parser_receive_romanization),
                subtitle = stringResource(R.string.parser_online_romanization_desc),
                checked = state.receiveOnlineRomanization,
                onCheckedChange = { onStateChange(state.copy(receiveOnlineRomanization = it)) }
            )
            OnlineProviderOrderEditor(state, onStateChange)
            AppleMusicOverrideEditor(state, onStateChange)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MaterialLyriconSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        SourceCard {
            SwitchRow(
                title = stringResource(R.string.parser_receive_translation),
                subtitle = stringResource(R.string.parser_lyricon_translation_desc),
                checked = state.receiveLyriconTranslation,
                onCheckedChange = { onStateChange(state.copy(receiveLyriconTranslation = it)) }
            )
            SwitchRow(
                title = stringResource(R.string.parser_receive_romanization),
                subtitle = stringResource(R.string.parser_lyricon_romanization_desc),
                checked = state.receiveLyriconRomanization,
                onCheckedChange = { onStateChange(state.copy(receiveLyriconRomanization = it)) }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SourceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchArrowRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    onArrowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                if (checked) onArrowClick() else onCheckedChange(true)
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onArrowClick, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun OnlineProviderOrderEditor(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit
) {
    val context = LocalContext.current
    if (state.useSmartOnlineLyricSelection) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.parser_online_priority), color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
    var order by remember { mutableStateOf(state.onlineLyricProviderOrder) }
    var disabledProviders by remember { mutableStateOf(state.onlineLyricDisabledProviders) }
    var draggingProvider by remember { mutableStateOf<OnlineLyricProvider?>(null) }
    LaunchedEffect(state.onlineLyricProviderOrder, state.onlineLyricDisabledProviders) {
        if (draggingProvider == null && order != state.onlineLyricProviderOrder) {
            order = state.onlineLyricProviderOrder
        }
        disabledProviders = state.onlineLyricDisabledProviders
    }
    val rowHeight = 52.dp
    Box(modifier = Modifier.fillMaxWidth().height(rowHeight * order.size)) {
        order.forEachIndexed { index, provider ->
            key(provider.id) {
                DraggableProviderRow(
                    label = provider.displayName(context),
                    checked = provider !in disabledProviders,
                    onCheckedChange = { checked ->
                        val nextDisabled = if (checked) {
                            disabledProviders - provider
                        } else {
                            disabledProviders + provider
                        }
                        disabledProviders = nextDisabled
                        onStateChange(state.copy(onlineLyricDisabledProviders = nextDisabled))
                    },
                    index = index,
                    rowHeight = rowHeight,
                    itemCount = order.size,
                    isDragging = draggingProvider == provider,
                    onDragStart = {
                        draggingProvider = provider
                    },
                    onDragMove = { from, to -> order = order.moveItem(from, to) },
                    onDragCancel = {
                        order = state.onlineLyricProviderOrder
                        draggingProvider = null
                    },
                    onDragEnd = {
                        val nextOrder = order
                        draggingProvider = null
                        if (nextOrder != state.onlineLyricProviderOrder) {
                            onStateChange(state.copy(onlineLyricProviderOrder = nextOrder))
                        }
                    }
                )
            }
        }
    }
    TextButton(onClick = {
        onStateChange(
            state.copy(
                onlineLyricProviderOrder = OnlineLyricProvider.defaultOrderForPackage(state.packageName),
                onlineLyricDisabledProviders = emptySet()
            )
        )
    }) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.parser_reset_online_priority))
    }
}

@Composable
private fun DraggableProviderRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    rowHeight: Dp,
    itemCount: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragMove: (Int, Int) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentIndex by rememberUpdatedState(index)
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val animatedY by animateDpAsState(
        targetValue = rowHeight * index,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.85f),
        label = "providerReorderY"
    )
    val baseY = if (isDragging) rowHeight * index else animatedY
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .offset {
                IntOffset(
                    x = 0,
                    y = baseY.roundToPx() + if (isDragging) dragOffset.roundToInt() else 0
                )
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                alpha = if (isDragging) 0.92f else 1f
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
            }
            .then(
                if (isDragging) {
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(label, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.action_drag_sort),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
                .pointerInput(itemCount) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffset = 0f
                            onDragStart()
                        },
                        onDragEnd = {
                            dragOffset = 0f
                            onDragEnd()
                        },
                        onDragCancel = {
                            dragOffset = 0f
                            onDragCancel()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y
                                    val from = currentIndex
                                    val target = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, itemCount - 1)
                                    if (target != from) {
                                        dragOffset -= (target - from) * rowHeightPx
                                        onDragMove(from, target)
                                    }
                        }
                    )
                }
        )
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
private fun AppleMusicOverrideEditor(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val overrideActive =
        state.appleMusicStorefrontOverride != null || state.appleMusicLanguageOverride != null

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.parser_apple_music_override),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (overrideActive) {
                    stringResource(R.string.apple_music_custom_override)
                } else {
                    stringResource(R.string.apple_music_follow_global)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (expanded) {
        Text(
            text = stringResource(R.string.parser_apple_music_override_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow(
            title = stringResource(R.string.apple_music_follow_global),
            subtitle = null,
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
            AppleOverrideDropdown(
                title = stringResource(R.string.apple_music_storefront),
                options = appleStorefrontOptions,
                value = state.appleMusicStorefrontOverride.orEmpty(),
                onSelect = { onStateChange(state.copy(appleMusicStorefrontOverride = it)) }
            )
            AppleOverrideDropdown(
                title = stringResource(R.string.apple_music_language),
                options = appleLanguageOptions,
                value = state.appleMusicLanguageOverride.orEmpty(),
                onSelect = { onStateChange(state.copy(appleMusicLanguageOverride = it)) }
            )
        }
    }
}

@Composable
private fun AppleOverrideDropdown(
    title: String,
    options: List<Pair<String, Int>>,
    value: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = options.firstOrNull { it.first == value }?.let { stringResource(it.second) } ?: value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionValue, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(optionValue)
                    }
                )
            }
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

