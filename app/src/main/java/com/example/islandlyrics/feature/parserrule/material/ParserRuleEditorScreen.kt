package com.example.islandlyrics.feature.parserrule.material

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
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
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorState
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader

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
    var showOnlineSuggestionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (isNewRule) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
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

            SwitchRow(
                title = stringResource(R.string.parser_car_protocol),
                subtitle = stringResource(R.string.parser_notify_lyric_desc),
                checked = state.usesCarProtocol,
                onCheckedChange = { state = state.copy(usesCarProtocol = it) }
            )

            if (state.usesCarProtocol) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.parser_logic_title), color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.parser_separator_label))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("-", " - ", " | ").forEach { separator ->
                                FilterChip(
                                    selected = state.separator == separator,
                                    onClick = { state = state.copy(separator = separator) },
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
                        Text(stringResource(R.string.parser_field_order_label))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST).forEach { order ->
                                FilterChip(
                                    selected = state.fieldOrder == order,
                                    onClick = { state = state.copy(fieldOrder = order) },
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

            SwitchRow(
                title = stringResource(R.string.settings_use_online_lyrics),
                subtitle = stringResource(R.string.parser_online_lyric_desc_short),
                checked = state.useOnlineLyrics,
                onCheckedChange = {
                    state = state.copy(
                        useOnlineLyrics = it,
                        useSmartOnlineLyricSelection = if (it) true else state.useSmartOnlineLyricSelection
                    )
                    if (it && state.usesCarProtocol) {
                        showOnlineSuggestionDialog = true
                    }
                }
            )

            if (state.useOnlineLyrics) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SwitchRow(
                            title = stringResource(R.string.parser_smart_online_fetch),
                            subtitle = stringResource(R.string.parser_smart_online_fetch_desc),
                            checked = state.useSmartOnlineLyricSelection,
                            onCheckedChange = { state = state.copy(useSmartOnlineLyricSelection = it) }
                        )
                        SwitchRow(
                            title = stringResource(R.string.parser_use_raw_metadata_for_online_match),
                            subtitle = stringResource(R.string.parser_use_raw_metadata_for_online_match_desc),
                            checked = state.useRawMetadataForOnlineMatching,
                            onCheckedChange = { state = state.copy(useRawMetadataForOnlineMatching = it) }
                        )
                        if (!state.useSmartOnlineLyricSelection) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.parser_online_priority), color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            state.onlineLyricProviderOrder.forEachIndexed { index, provider ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${provider.displayName(context)}", modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            state = state.copy(
                                                onlineLyricProviderOrder = state.onlineLyricProviderOrder.toMutableList().apply {
                                                    removeAt(index)
                                                    add(index - 1, provider)
                                                }
                                            )
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
                                    }
                                    IconButton(
                                        onClick = {
                                            state = state.copy(
                                                onlineLyricProviderOrder = state.onlineLyricProviderOrder.toMutableList().apply {
                                                    removeAt(index)
                                                    add(index + 1, provider)
                                                }
                                            )
                                        },
                                        enabled = index < state.onlineLyricProviderOrder.lastIndex
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
                                    }
                                }
                            }
                            TextButton(onClick = { state = state.copy(onlineLyricProviderOrder = OnlineLyricProvider.defaultOrder()) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.parser_reset_online_priority))
                            }
                        }
                    }
                }
            }

            SwitchRow(
                title = stringResource(R.string.parser_super_lyric),
                subtitle = stringResource(R.string.parser_super_lyric_desc_short),
                checked = state.useSuperLyricApi,
                onCheckedChange = { state = state.copy(useSuperLyricApi = it) }
            )
            SwitchRow(
                title = stringResource(R.string.parser_lgetter_lyric),
                subtitle = stringResource(R.string.parser_lgetter_lyric_desc_short),
                checked = state.useLyricGetterApi,
                onCheckedChange = { state = state.copy(useLyricGetterApi = it) }
            )

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
