package com.example.islandlyrics.feature.parserrule.material

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorState
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

private enum class MaterialSourceConfigPage {
    NOTIFICATION,
    ONLINE,
    LYRICON
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleEditorScreen(
    initialRule: ParserRule,
    isNewRule: Boolean,
    onBack: () -> Unit,
    onDelete: (ParserRule) -> Unit,
    onSaved: (ParserRule) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var state by remember(initialRule) { mutableStateOf(initialRule.toEditorState()) }
    var sourceConfigPage by remember { mutableStateOf<MaterialSourceConfigPage?>(null) }
    var showOnlineSuggestionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        when (sourceConfigPage) {
                            MaterialSourceConfigPage.NOTIFICATION -> stringResource(R.string.parser_car_protocol)
                            MaterialSourceConfigPage.ONLINE -> stringResource(R.string.settings_use_online_lyrics)
                            MaterialSourceConfigPage.LYRICON -> stringResource(R.string.parser_lyricon_lyric)
                            null -> if (isNewRule) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        sourceConfigPage = when (sourceConfigPage) {
                            null -> {
                                onBack()
                                null
                            }
                            else -> null
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (state.packageName.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.dialog_enter_pkg), Toast.LENGTH_SHORT).show()
                        } else {
                            onSaved(state.toRule(initialRule, isNewRule))
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.parser_save_rule))
                    }
                    IconButton(onClick = {
                        context.startActivity(android.content.Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                    }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.faq_title))
                    }
                    if (!isNewRule) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.parser_delete))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        }
    ) { padding ->
        if (sourceConfigPage != null) {
            val sourceModifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
            when (sourceConfigPage) {
                MaterialSourceConfigPage.NOTIFICATION -> MaterialNotificationSourceConfigPage(
                    state = state,
                    onStateChange = { state = it },
                    modifier = sourceModifier
                )
                MaterialSourceConfigPage.ONLINE -> MaterialOnlineSourceConfigPage(
                    state = state,
                    onStateChange = { state = it },
                    onShowOnlineSuggestion = { showOnlineSuggestionDialog = true },
                    modifier = sourceModifier
                )
                MaterialSourceConfigPage.LYRICON -> MaterialLyriconSourceConfigPage(
                    state = state,
                    onStateChange = { state = it },
                    modifier = sourceModifier
                )
                null -> Unit
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
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
            SettingsSectionHeader(stringResource(R.string.parser_logic_desc))
            SourceCard {
                MaterialSourceRows(
                    state = state,
                    onStateChange = { state = it },
                    onNavigate = { sourceConfigPage = it },
                    onShowOnlineSuggestion = { showOnlineSuggestionDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (state.packageName.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.dialog_enter_pkg), Toast.LENGTH_SHORT).show()
                    } else {
                        onSaved(state.toRule(initialRule, isNewRule))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.parser_save_rule))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showOnlineSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { showOnlineSuggestionDialog = false },
            title = { Text(stringResource(R.string.parser_online_conflict_title)) },
            text = { Text(stringResource(R.string.parser_online_conflict_message)) },
            confirmButton = {
                TextButton(onClick = {
                    state = state.copy(usesCarProtocol = false)
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.parser_delete)) },
            text = { Text(stringResource(R.string.dialog_delete_confirm, state.customName.ifBlank { state.packageName })) },
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

@Composable
private fun MaterialSourceRows(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    onNavigate: (MaterialSourceConfigPage) -> Unit,
    onShowOnlineSuggestion: () -> Unit
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    SwitchArrowRow(
        title = stringResource(R.string.parser_car_protocol),
        subtitle = stringResource(R.string.parser_notify_lyric_desc),
        checked = state.usesCarProtocol,
        onCheckedChange = { onStateChange(state.copy(usesCarProtocol = it)) },
        onArrowClick = { onNavigate(MaterialSourceConfigPage.NOTIFICATION) }
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
        onArrowClick = { onNavigate(MaterialSourceConfigPage.ONLINE) }
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
        onArrowClick = { onNavigate(MaterialSourceConfigPage.LYRICON) }
    )
}

@Composable
private fun MaterialNotificationSourceConfigPage(
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
private fun MaterialOnlineSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    onShowOnlineSuggestion: () -> Unit,
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
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MaterialLyriconSourceConfigPage(
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        IconButton(onClick = onArrowClick, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
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
    state.onlineLyricProviderOrder.forEachIndexed { index, provider ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}. ${provider.displayName(context)}", modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    onStateChange(
                        state.copy(
                            onlineLyricProviderOrder = state.onlineLyricProviderOrder.toMutableList().apply {
                                removeAt(index)
                                add(index - 1, provider)
                            }
                        )
                    )
                },
                enabled = index > 0
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
            }
            IconButton(
                onClick = {
                    onStateChange(
                        state.copy(
                            onlineLyricProviderOrder = state.onlineLyricProviderOrder.toMutableList().apply {
                                removeAt(index)
                                add(index + 1, provider)
                            }
                        )
                    )
                },
                enabled = index < state.onlineLyricProviderOrder.lastIndex
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
            }
        }
    }
    TextButton(onClick = { onStateChange(state.copy(onlineLyricProviderOrder = OnlineLyricProvider.defaultOrder())) }) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.parser_reset_online_priority))
    }
}
