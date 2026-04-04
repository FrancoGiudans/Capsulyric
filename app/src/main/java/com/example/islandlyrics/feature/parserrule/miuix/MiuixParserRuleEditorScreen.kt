package com.example.islandlyrics.feature.parserrule.miuix

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
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
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixParserRuleEditorScreen(
    initialRule: ParserRule,
    isNewRule: Boolean,
    onBack: () -> Unit,
    onDelete: (ParserRule) -> Unit,
    onSaved: (ParserRule) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var state by remember(initialRule) { mutableStateOf(initialRule.toEditorState()) }
    var showOnlineSuggestionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val separators = listOf("-", " - ", " | ")
    val orders = listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST)

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = if (isNewRule) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.packageName.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.dialog_enter_pkg), Toast.LENGTH_SHORT).show()
                            } else {
                                onSaved(state.toRule(initialRule, isNewRule))
                            }
                        }
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = stringResource(R.string.parser_save_rule), tint = MiuixTheme.colorScheme.onBackground)
                    }
                    IconButton(
                        onClick = {
                            context.startActivity(android.content.Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                        }
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Info, contentDescription = stringResource(R.string.faq_title), tint = MiuixTheme.colorScheme.onBackground)
                    }
                    if (!isNewRule) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.parser_delete), tint = MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
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
            SmallTitle(text = stringResource(R.string.parser_logic_header))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SuperSwitch(
                    title = stringResource(R.string.parser_car_protocol),
                    summary = stringResource(R.string.parser_notify_lyric_desc),
                    checked = state.usesCarProtocol,
                    onCheckedChange = { state = state.copy(usesCarProtocol = it) }
                )
                if (state.usesCarProtocol) {
                    SuperDropdown(
                        title = stringResource(R.string.parser_separator_label),
                        items = separators,
                        selectedIndex = separators.indexOf(state.separator).coerceAtLeast(0),
                        onSelectedIndexChange = { state = state.copy(separator = separators[it]) }
                    )
                    SuperDropdown(
                        title = stringResource(R.string.parser_field_order_label),
                        items = orders.map {
                            if (it == FieldOrder.ARTIST_TITLE) stringResource(R.string.parser_order_artist_title)
                            else stringResource(R.string.parser_order_title_artist)
                        },
                        selectedIndex = orders.indexOf(state.fieldOrder).coerceAtLeast(0),
                        onSelectedIndexChange = { state = state.copy(fieldOrder = orders[it]) }
                    )
                }
                SuperSwitch(
                    title = stringResource(R.string.settings_use_online_lyrics),
                    summary = stringResource(R.string.parser_online_lyric_desc_short),
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
                    SuperSwitch(
                        title = stringResource(R.string.parser_smart_online_fetch),
                        summary = stringResource(R.string.parser_smart_online_fetch_desc),
                        checked = state.useSmartOnlineLyricSelection,
                        onCheckedChange = { state = state.copy(useSmartOnlineLyricSelection = it) }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.parser_use_raw_metadata_for_online_match),
                        summary = stringResource(R.string.parser_use_raw_metadata_for_online_match_desc),
                        checked = state.useRawMetadataForOnlineMatching,
                        onCheckedChange = { state = state.copy(useRawMetadataForOnlineMatching = it) }
                    )
                    if (!state.useSmartOnlineLyricSelection) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.parser_online_priority))
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
                                        androidx.compose.material3.Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.action_move_up),
                                            tint = if (index > 0) MiuixTheme.colorScheme.onSurface
                                            else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
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
                                        androidx.compose.material3.Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.action_move_down),
                                            tint = if (index < state.onlineLyricProviderOrder.lastIndex) MiuixTheme.colorScheme.onSurface
                                            else MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = { state = state.copy(onlineLyricProviderOrder = OnlineLyricProvider.defaultOrder()) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.parser_reset_online_priority))
                            }
                        }
                    }
                }
                SuperSwitch(
                    title = stringResource(R.string.parser_super_lyric),
                    summary = stringResource(R.string.parser_super_lyric_desc_short),
                    checked = state.useSuperLyricApi,
                    onCheckedChange = { state = state.copy(useSuperLyricApi = it) }
                )
                SuperSwitch(
                    title = stringResource(R.string.parser_lgetter_lyric),
                    summary = stringResource(R.string.parser_lgetter_lyric_desc_short),
                    checked = state.useLyricGetterApi,
                    onCheckedChange = { state = state.copy(useLyricGetterApi = it) }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.parser_save_rule))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showOnlineSuggestionDialog) {
            MiuixBlurDialog(
                title = stringResource(R.string.parser_online_conflict_title),
                summary = stringResource(R.string.parser_online_conflict_message),
                show = true,
                onDismissRequest = { showOnlineSuggestionDialog = false }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            state = state.copy(usesCarProtocol = false)
                            showOnlineSuggestionDialog = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.parser_online_conflict_disable_notify))
                    }
                    Button(
                        onClick = { showOnlineSuggestionDialog = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.parser_online_conflict_keep))
                    }
                }
            }
        }

        if (showDeleteDialog) {
            MiuixBlurDialog(
                title = stringResource(R.string.parser_delete),
                summary = stringResource(R.string.dialog_delete_confirm, state.customName.ifBlank { state.packageName }),
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
    }
}
