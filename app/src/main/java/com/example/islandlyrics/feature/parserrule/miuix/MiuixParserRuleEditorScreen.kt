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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorState
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurSmallTopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

private enum class MiuixSourceConfigPage {
    NOTIFICATION,
    ONLINE,
    LYRICON
}

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
    var sourceConfigPage by remember { mutableStateOf<MiuixSourceConfigPage?>(null) }
    var showOnlineSuggestionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurSmallTopAppBar(
                title = when (sourceConfigPage) {
                    MiuixSourceConfigPage.NOTIFICATION -> stringResource(R.string.parser_car_protocol)
                    MiuixSourceConfigPage.ONLINE -> stringResource(R.string.settings_use_online_lyrics)
                    MiuixSourceConfigPage.LYRICON -> stringResource(R.string.parser_lyricon_lyric)
                    null -> if (isNewRule) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            sourceConfigPage = when (sourceConfigPage) {
                                null -> {
                                    onBack()
                                    null
                                }
                                else -> null
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
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
        if (sourceConfigPage != null) {
            val pageModifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
            when (sourceConfigPage) {
                MiuixSourceConfigPage.NOTIFICATION -> MiuixNotificationSourceConfigPage(
                    state = state,
                    onStateChange = { state = it },
                    modifier = pageModifier
                )
                MiuixSourceConfigPage.ONLINE -> MiuixOnlineSourceConfigPage(
                    state = state,
                    onStateChange = { state = it },
                    modifier = pageModifier
                )
                MiuixSourceConfigPage.LYRICON -> MiuixLyriconSourceConfigPage(
                    state = state,
                    onStateChange = { state = it },
                    modifier = pageModifier
                )
                null -> Unit
            }
        } else {
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
                MiuixSourceRows(
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.parser_save_rule))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
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

@Composable
private fun MiuixSourceRows(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    onNavigate: (MiuixSourceConfigPage) -> Unit,
    onShowOnlineSuggestion: () -> Unit
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)

    MiuixSwitchArrowPreference(
        title = stringResource(R.string.parser_car_protocol),
        summary = stringResource(R.string.parser_notify_lyric_desc),
        checked = state.usesCarProtocol,
        onCheckedChange = { onStateChange(state.copy(usesCarProtocol = it)) },
        onArrowClick = { onNavigate(MiuixSourceConfigPage.NOTIFICATION) }
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
        onArrowClick = { onNavigate(MiuixSourceConfigPage.ONLINE) }
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
        onArrowClick = { onNavigate(MiuixSourceConfigPage.LYRICON) }
    )
}

@Composable
private fun MiuixNotificationSourceConfigPage(
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
private fun MiuixOnlineSourceConfigPage(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
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
            MiuixOnlineProviderOrderEditor(state, onStateChange)
        }
    }
}

@Composable
private fun MiuixLyriconSourceConfigPage(
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
            IconButton(onClick = onArrowClick, enabled = enabled) {
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
                enabled = enabled
            )
        }
    )
}

@Composable
private fun MiuixOnlineProviderOrderEditor(
    state: ParserRuleEditorState,
    onStateChange: (ParserRuleEditorState) -> Unit
) {
    val context = LocalContext.current
    if (state.useSmartOnlineLyricSelection) return

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.parser_online_priority))
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
                    androidx.compose.material3.Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_move_up),
                        tint = if (index > 0) MiuixTheme.colorScheme.onSurface
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
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
            onClick = { onStateChange(state.copy(onlineLyricProviderOrder = OnlineLyricProvider.defaultOrder())) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.parser_reset_online_priority))
        }
    }
}
