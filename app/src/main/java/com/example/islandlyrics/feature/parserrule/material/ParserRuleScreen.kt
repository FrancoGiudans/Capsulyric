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

import com.example.islandlyrics.ui.material.blur.MaterialBlurAlertDialog

import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.rules.ParserRule
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.ui.adaptive.AdaptiveTwoPane
import com.example.islandlyrics.ui.adaptive.AdaptiveWidthItem
import com.example.islandlyrics.ui.adaptive.LocalAdaptiveHasStartRail
import com.example.islandlyrics.ui.adaptive.LocalAppWindowLayout
import com.example.islandlyrics.ui.adaptive.adaptiveContentPadding
import com.example.islandlyrics.ui.navigation.OverlaySheetHost
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import kotlinx.coroutines.delay

private const val TEMPLATE_DETAIL_KEY = "__TEMPLATE__"
private const val NEW_RULE_DETAIL_PREFIX = "__NEW__|"

private data class InlineParserRuleEditorState(
    val initialRule: ParserRule,
    val isNewRule: Boolean,
    val isTemplate: Boolean = false,
)

private fun encodeNewRuleDetailKey(packageName: String?, suggestedName: String?): String =
    "$NEW_RULE_DETAIL_PREFIX${packageName.orEmpty()}|${suggestedName.orEmpty()}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleScreen(
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    onOpenFaq: (() -> Unit)? = null,
    onOpenRuleEditor: ((packageName: String?, suggestedName: String?) -> Unit)? = null,
    onOpenTemplateEditor: (() -> Unit)? = null,
    selectedDetailKey: String? = null,
    onSelectDetailKey: ((String?) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val layoutInfo = LocalAppWindowLayout.current
    val layoutDirection = LocalLayoutDirection.current
    val useMasterDetail = layoutInfo.useMasterDetail && onSelectDetailKey != null
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    var rules by remember { mutableStateOf<List<ParserRule>>(ParserRuleHelper.loadRules(context)) }
    var hasTemplate by remember { mutableStateOf(ParserRuleHelper.hasDefaultTemplate(context)) }
    val showDeleteDialog = remember { mutableStateOf<ParserRule?>(null) }
    var inlineEditorState by remember { mutableStateOf<InlineParserRuleEditorState?>(null) }
    var inlineEditorVisible by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    fun refreshRules() {
        rules = ParserRuleHelper.loadRules(context)
        hasTemplate = ParserRuleHelper.hasDefaultTemplate(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val prefs = AppPreferences.of(context)
        val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferences.Keys.PARSER_RULES_JSON || key == AppPreferences.Keys.PARSER_RULE_TEMPLATE_JSON) {
                refreshRules()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshRules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refresh recommendations on enter
    LaunchedEffect(Unit) {
        com.example.islandlyrics.runtime.service.MediaMonitorService.triggerRecheck()
    }

    fun openRuleEditor(packageName: String? = null, suggestedName: String? = null) {
        if (useMasterDetail && onSelectDetailKey != null) {
            val existingRule = if (!packageName.isNullOrBlank()) {
                rules.firstOrNull { it.packageName == packageName }
            } else {
                null
            }
            if (existingRule != null) {
                onSelectDetailKey(existingRule.packageName)
            } else {
                onSelectDetailKey(encodeNewRuleDetailKey(packageName, suggestedName))
            }
            return
        }
        if (onOpenRuleEditor != null) {
            onOpenRuleEditor(packageName, suggestedName)
            return
        }
        val existingRule = if (!packageName.isNullOrBlank()) {
            ParserRuleHelper.loadRules(context).firstOrNull { it.packageName == packageName }
        } else {
            null
        }
        val initialRule = when {
            !packageName.isNullOrBlank() -> {
                existingRule
                    ?: ParserRuleHelper.createDefaultRule(context, packageName).copy(customName = suggestedName)
            }
            else -> ParserRuleHelper.createDefaultRule(context, "").copy(customName = suggestedName)
        }
        inlineEditorState = InlineParserRuleEditorState(
            initialRule = initialRule,
            isNewRule = packageName.isNullOrBlank() || existingRule == null
        )
        inlineEditorVisible = true
    }

    fun openTemplateEditor() {
        if (useMasterDetail && onSelectDetailKey != null) {
            onSelectDetailKey(TEMPLATE_DETAIL_KEY)
            return
        }
        if (onOpenTemplateEditor != null) {
            onOpenTemplateEditor()
            return
        }
        inlineEditorState = InlineParserRuleEditorState(
            initialRule = ParserRuleHelper.loadDefaultTemplate(context)
                ?: ParserRuleHelper.createDefaultRule(context, ""),
            isNewRule = false,
            isTemplate = true
        )
        inlineEditorVisible = true
    }

    fun closeRuleEditor() {
        inlineEditorVisible = false
    }


    LaunchedEffect(inlineEditorVisible) {
        if (inlineEditorVisible) {
            onBottomBarVisibilityChange(false)
        } else {
            delay(430)
            inlineEditorState = null
            onBottomBarVisibilityChange(true)
        }
    }

    val listScaffoldContent: @Composable () -> Unit = {
        MaterialBlurScaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                MaterialBlurTopAppBar(
                    title = { Text(stringResource(R.string.parser_rule_title)) },
                    navigationIcon = if (showBackButton) {
                        {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    } else {
                        {}
                    },
                    actions = {
                        IconButton(onClick = {
                            onOpenFaq?.invoke()
                                ?: context.startActivity(android.content.Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                        }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.faq_title)
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                // Recommendation Logic
                val recommendEnabled = remember {
                    AppPreferences.of(context)
                        .getBoolean(AppPreferences.Keys.RECOMMEND_MEDIA_APP, true)
                }

                val metadata by LyricRepository.getInstance().liveSuggestionMetadata.observeAsState()
                val currentPkg = metadata?.packageName

                // Check if current app is already in rules
                val isKnownApp = remember(rules, currentPkg) {
                    if (currentPkg == null) true // Don't recommend if no music
                    else rules.any { it.packageName == currentPkg }
                }

                val showRecommendation = recommendEnabled && !isKnownApp && currentPkg != null

                ExtendedFloatingActionButton(
                    onClick = {
                        if (showRecommendation) {
                            // Try to get label
                            val label = try {
                                val pm = context.packageManager
                                val info = pm.getApplicationInfo(currentPkg, 0)
                                pm.getApplicationLabel(info).toString()
                            } catch (_: Exception) {
                                ""
                            }
                            openRuleEditor(packageName = currentPkg, suggestedName = label)
                        } else {
                            openRuleEditor()
                        }
                    },
                    expanded = showRecommendation,
                    icon = { Icon(painterResource(android.R.drawable.ic_input_add), contentDescription = null) },
                    modifier = Modifier.padding(bottom = extraBottomPadding),
                    text = {
                        if (showRecommendation) {
                            // Extract app name for display
                            val appLabel = remember(currentPkg) {
                                try {
                                    val pm = context.packageManager
                                    val info = pm.getApplicationInfo(currentPkg, 0)
                                    pm.getApplicationLabel(info).toString()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            Text(text = stringResource(R.string.parser_add_app_fmt, appLabel ?: stringResource(R.string.parser_current_app)))
                        } else {
                            Text(text = stringResource(R.string.parser_add_rule))
                        }
                    }
                )
            },
            containerColor = materialPageContainerColor()
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = if (useMasterDetail) {
                    PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        end = 12.dp,
                        bottom = padding.calculateBottomPadding() + 80.dp + extraBottomPadding,
                    )
                } else {
                    padding.adaptiveContentPadding(
                        layoutDirection = layoutDirection,
                        extraStart = 16.dp,
                        extraTop = 8.dp,
                        extraEnd = 16.dp,
                        extraBottom = 80.dp + extraBottomPadding,
                    )
                },
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    val templateCard = @Composable {
                        ParserRuleTemplateCard(
                            configured = hasTemplate,
                            selected = useMasterDetail && selectedDetailKey == TEMPLATE_DETAIL_KEY,
                            onClick = ::openTemplateEditor
                        )
                    }
                    if (useMasterDetail) templateCard() else AdaptiveWidthItem { templateCard() }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (rules.isEmpty()) {
                    item {
                        val emptyCard = @Composable {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = { Text(stringResource(R.string.parser_no_rules), style = MaterialTheme.typography.titleMedium) },
                                    supportingContent = { Text(stringResource(R.string.parser_no_rules_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                )
                            }
                        }
                        if (useMasterDetail) emptyCard() else AdaptiveWidthItem { emptyCard() }
                    }
                } else {
                    item {
                        val rulesCard = @Composable {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                rules.forEachIndexed { index, rule ->
                                    ParserRuleItem(
                                        rule = rule,
                                        offlineModeEnabled = offlineModeEnabled,
                                        selected = useMasterDetail && selectedDetailKey == rule.packageName,
                                        onToggleEnabled = { enabled ->
                                            val idx = rules.indexOf(rule)
                                            if (idx != -1) {
                                                val newRule = rule.copy(enabled = enabled)
                                                rules = rules.toMutableList().apply { set(idx, newRule) }
                                                ParserRuleHelper.saveRules(context, rules)
                                            }
                                        },
                                        onEdit = {
                                            openRuleEditor(packageName = rule.packageName)
                                        },
                                        onDelete = { showDeleteDialog.value = rule }
                                    )
                                    if (index < rules.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                        if (useMasterDetail) rulesCard() else AdaptiveWidthItem { rulesCard() }
                    }
                }
            }
        }
    }

    if (useMasterDetail) {
        AdaptiveTwoPane(
            startWeight = 0.38f,
            dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(),
            startPane = { listScaffoldContent() },
            endPane = {
                CompositionLocalProvider(LocalAdaptiveHasStartRail provides true) {
                    val detailKey = selectedDetailKey
                    val detailEditorState = remember(detailKey, rules, hasTemplate) {
                        when {
                            detailKey == null -> null
                            detailKey == TEMPLATE_DETAIL_KEY -> InlineParserRuleEditorState(
                                initialRule = ParserRuleHelper.loadDefaultTemplate(context)
                                    ?: ParserRuleHelper.createDefaultRule(context, ""),
                                isNewRule = false,
                                isTemplate = true
                            )
                            detailKey.startsWith(NEW_RULE_DETAIL_PREFIX) -> {
                                val parts = detailKey.removePrefix(NEW_RULE_DETAIL_PREFIX).split('|', limit = 2)
                                val pkg = parts.getOrNull(0).orEmpty()
                                val name = parts.getOrNull(1).orEmpty().ifBlank { null }
                                val existing = if (pkg.isNotBlank()) rules.firstOrNull { it.packageName == pkg } else null
                                val initial = if (pkg.isNotBlank()) {
                                    existing ?: ParserRuleHelper.createDefaultRule(context, pkg).copy(customName = name)
                                } else {
                                    ParserRuleHelper.createDefaultRule(context, "").copy(customName = name)
                                }
                                InlineParserRuleEditorState(
                                    initialRule = initial,
                                    isNewRule = pkg.isBlank() || existing == null,
                                    isTemplate = false
                                )
                            }
                            else -> rules.firstOrNull { it.packageName == detailKey }?.let { rule ->
                                InlineParserRuleEditorState(
                                    initialRule = rule,
                                    isNewRule = false,
                                    isTemplate = false
                                )
                            }
                        }
                    }
                    if (detailEditorState != null) {
                        key(detailKey) {
                            ParserRuleEditorScreen(
                                initialRule = detailEditorState.initialRule,
                                isNewRule = detailEditorState.isNewRule,
                                isTemplate = detailEditorState.isTemplate,
                                showBackButton = false,
                                onBack = { onSelectDetailKey?.invoke(null) },
                                onDelete = { rule ->
                                    if (detailEditorState.isTemplate) {
                                        ParserRuleHelper.clearDefaultTemplate(context)
                                    } else {
                                        val updatedRules = ParserRuleHelper.loadRules(context).toMutableList()
                                        updatedRules.removeAll { it.packageName == rule.packageName }
                                        ParserRuleHelper.saveRules(context, updatedRules)
                                    }
                                    refreshRules()
                                    onSelectDetailKey?.invoke(null)
                                },
                                onSaved = { rule ->
                                    if (detailEditorState.isTemplate) {
                                        ParserRuleHelper.saveDefaultTemplate(context, rule)
                                        refreshRules()
                                        onSelectDetailKey?.invoke(TEMPLATE_DETAIL_KEY)
                                    } else {
                                        val updatedRules = ParserRuleHelper.loadRules(context).toMutableList()
                                        val index = updatedRules.indexOfFirst { it.packageName == rule.packageName }
                                        if (index >= 0) {
                                            updatedRules[index] = rule
                                        } else {
                                            updatedRules.add(rule)
                                        }
                                        updatedRules.sort()
                                        ParserRuleHelper.saveRules(context, updatedRules)
                                        refreshRules()
                                        onSelectDetailKey?.invoke(rule.packageName)
                                    }
                                },
                                onOpenFaq = {
                                    onOpenFaq?.invoke()
                                        ?: context.startActivity(android.content.Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.parser_rule_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.parser_rule_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    } else {
        OverlaySheetHost(
            visible = inlineEditorVisible && inlineEditorState != null,
            onDismissRequest = ::closeRuleEditor,
            content = { listScaffoldContent() },
            sheetContent = {
                val editorState = inlineEditorState
                if (editorState != null) {
                    ParserRuleEditorScreen(
                        initialRule = editorState.initialRule,
                        isNewRule = editorState.isNewRule,
                        isTemplate = editorState.isTemplate,
                        onBack = ::closeRuleEditor,
                        onDelete = { rule ->
                            if (editorState.isTemplate) {
                                ParserRuleHelper.clearDefaultTemplate(context)
                                refreshRules()
                            } else {
                                val updatedRules = ParserRuleHelper.loadRules(context).toMutableList()
                                updatedRules.removeAll { it.packageName == rule.packageName }
                                ParserRuleHelper.saveRules(context, updatedRules)
                                refreshRules()
                            }
                            closeRuleEditor()
                        },
                        onSaved = { rule ->
                            if (editorState.isTemplate) {
                                ParserRuleHelper.saveDefaultTemplate(context, rule)
                                refreshRules()
                            } else {
                                val updatedRules = ParserRuleHelper.loadRules(context).toMutableList()
                                val index = updatedRules.indexOfFirst { it.packageName == rule.packageName }
                                if (index >= 0) {
                                    updatedRules[index] = rule
                                } else {
                                    updatedRules.add(rule)
                                }
                                updatedRules.sort()
                                ParserRuleHelper.saveRules(context, updatedRules)
                                refreshRules()
                            }
                            closeRuleEditor()
                        },
                        onOpenFaq = {
                            closeRuleEditor()
                            onOpenFaq?.invoke()
                                ?: context.startActivity(android.content.Intent(context, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                        }
                    )
                }
            }
        )
    }

    if (showDeleteDialog.value != null) {
        MaterialBlurAlertDialog(
            onDismissRequest = { showDeleteDialog.value = null },
            title = { Text(stringResource(R.string.parser_delete)) },
            text = {
                val deletingRule = showDeleteDialog.value
                Text(stringResource(R.string.dialog_delete_confirm, deletingRule?.customName ?: deletingRule?.packageName ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = showDeleteDialog.value
                    val newRules = rules.toMutableList()
                    newRules.remove(deleted)
                    ParserRuleHelper.saveRules(context, newRules)
                    refreshRules()
                    if (deleted != null && selectedDetailKey == deleted.packageName) {
                        onSelectDetailKey?.invoke(null)
                    }
                    showDeleteDialog.value = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog.value = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ParserRuleTemplateCard(
    configured: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = stringResource(R.string.parser_template_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(
                        if (configured) R.string.parser_template_status_configured
                        else R.string.parser_template_status_not_configured
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ParserRuleItem(
    rule: ParserRule,
    offlineModeEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    selected: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val onlineOrderSummary = if (!offlineModeEnabled && rule.useOnlineLyrics && !rule.useSmartOnlineLyricSelection) {
        OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder)
            .filterNot { it.id in rule.onlineLyricDisabledProviders }
            .joinToString(" > ") { it.displayName(context) }
    } else {
        null
    }
    val onlineOrderSummaryText = onlineOrderSummary?.let {
        stringResource(R.string.parser_online_priority_summary, it)
    }
    val packageSummary = if (!rule.customName.isNullOrEmpty()) rule.packageName else null

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onEdit, onLongClick = onDelete),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = rule.customName ?: rule.packageName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        supportingContent = {
            Column {
                packageSummary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                onlineOrderSummaryText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RuleSourceBadge(active = rule.useLocalLyrics, label = "Local")
                    RuleSourceBadge(active = rule.usesCarProtocol, label = "Notify")
                    if (!offlineModeEnabled) {
                        RuleSourceBadge(active = rule.useOnlineLyrics, label = "Online")
                    }
                    RuleSourceBadge(active = rule.useSuperLyricApi, label = "Super")
                    RuleSourceBadge(active = rule.useLyricGetterApi, label = "LGetter")
                    RuleSourceBadge(active = rule.useLyriconApi, label = "Lyricon")
                }
            }
        },
        trailingContent = {
            Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
        }
    )
}

@Composable
private fun RuleSourceBadge(active: Boolean, label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

