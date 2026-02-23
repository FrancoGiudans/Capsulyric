package com.example.islandlyrics

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixParserRuleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var rules by remember { mutableStateOf(ParserRuleHelper.loadRules(context)) }
    var showEditDialog = remember { mutableStateOf(false) }
    var showDeleteDialog = remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ParserRule?>(null) }
    var deletingRule by remember { mutableStateOf<ParserRule?>(null) }

    fun refreshRules() {
        rules = ParserRuleHelper.loadRules(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_parser_whitelist_manager),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Recommendation Logic
            val recommendEnabled = remember { 
                context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                    .getBoolean("recommend_media_app", true) 
            }
            
            val metadata by LyricRepository.getInstance().liveSuggestionMetadata.observeAsState()
            val currentPkg = metadata?.packageName
            
            val isKnownApp = remember(rules, currentPkg) {
                if (currentPkg == null) true
                else rules.any { it.packageName == currentPkg }
            }
            
            val showRecommendation = recommendEnabled && !isKnownApp && currentPkg != null

            val appLabel = remember(currentPkg) {
                if (currentPkg != null) {
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(currentPkg!!, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            FloatingActionButton(
                onClick = {
                    if (showRecommendation) {
                        val pkg = currentPkg!!
                        editingRule = ParserRuleHelper.createDefaultRule(pkg).copy(customName = appLabel)
                        showEditDialog.value = true
                    } else {
                        editingRule = null
                        showEditDialog.value = true
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                    if (showRecommendation) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add ${appLabel ?: "Current App"}",
                            color = MiuixTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.parser_add_rule),
                            color = MiuixTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            if (rules.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = "No Rules",
                            summary = "Tap FAB to add a parser rule"
                        )
                    }
                }
            } else {
                item {
                    SmallTitle(text = "Parser Rules (${rules.size})")
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        rules.forEach { rule ->
                            MiuixParserRuleItem(
                                rule = rule,
                                onClick = {
                                    editingRule = rule
                                    showEditDialog.value = true
                                },
                                onLongClick = {
                                    deletingRule = rule
                                    showDeleteDialog.value = true
                                },
                                onToggle = { enabled ->
                                    val updatedRules = rules.toMutableList()
                                    val index = updatedRules.indexOfFirst { it.packageName == rule.packageName }
                                    if (index >= 0) {
                                        updatedRules[index] = rule.copy(enabled = enabled)
                                        ParserRuleHelper.saveRules(context, updatedRules)
                                        refreshRules()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Miuix Edit Dialog
    if (showEditDialog.value) {
        MiuixEditRuleDialog(
            rule = editingRule,
            show = showEditDialog,
            onSave = { newRule ->
                val updatedRules = rules.toMutableList()
                if (editingRule != null && updatedRules.any { it.packageName == editingRule!!.packageName }) {
                    val index = updatedRules.indexOfFirst { it.packageName == editingRule!!.packageName }
                    if (index >= 0) updatedRules[index] = newRule
                } else {
                    if (updatedRules.any { it.packageName == newRule.packageName }) {
                        Toast.makeText(context, "Package already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        updatedRules.add(newRule)
                    }
                }
                updatedRules.sort()
                ParserRuleHelper.saveRules(context, updatedRules)
                refreshRules()
                showEditDialog.value = false
            }
        )
    }

    // Miuix Delete Dialog
    if (showDeleteDialog.value && deletingRule != null) {
        SuperDialog(
            title = stringResource(R.string.parser_delete),
            summary = stringResource(R.string.dialog_delete_confirm, deletingRule?.customName ?: deletingRule?.packageName ?: ""),
            show = showDeleteDialog,
            onDismissRequest = { showDeleteDialog.value = false }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.dialog_btn_cancel),
                    onClick = { showDeleteDialog.value = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val updatedRules = rules.toMutableList()
                        updatedRules.remove(deletingRule)
                        ParserRuleHelper.saveRules(context, updatedRules)
                        refreshRules()
                        showDeleteDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiuixParserRuleItem(
    rule: ParserRule,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.customName ?: rule.packageName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (rule.enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            if (!rule.customName.isNullOrEmpty()) {
                Text(
                    text = rule.packageName,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixStatusBadge(active = rule.usesCarProtocol, label = "Notify")
                Spacer(modifier = Modifier.width(8.dp))
                MiuixStatusBadge(active = rule.useOnlineLyrics, label = "Online")
                Spacer(modifier = Modifier.width(8.dp))
                MiuixStatusBadge(active = rule.useSuperLyricApi, label = "Super")
            }
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun MiuixStatusBadge(active: Boolean, label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f)
    )
}

@Composable
fun MiuixEditRuleDialog(
    rule: ParserRule?,
    show: MutableState<Boolean>,
    onSave: (ParserRule) -> Unit
) {
    var packageName by remember { mutableStateOf(rule?.packageName ?: "") }
    var customName by remember { mutableStateOf(rule?.customName ?: "") }
    var usesCarProtocol by remember { mutableStateOf(rule?.usesCarProtocol ?: true) }
    var useOnlineLyrics by remember { mutableStateOf(rule?.useOnlineLyrics ?: false) }
    var useSuperLyricApi by remember { mutableStateOf(rule?.useSuperLyricApi ?: true) }
    
    val separators = listOf("-", " - ", " | ")
    var separatorIndex by remember { 
        val idx = separators.indexOf(rule?.separatorPattern ?: "-")
        mutableStateOf(if (idx >= 0) idx else 0)
    }
    
    val orders = listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST)
    var orderIndex by remember {
        val idx = orders.indexOf(rule?.fieldOrder ?: FieldOrder.ARTIST_TITLE)
        mutableStateOf(if (idx >= 0) idx else 0)
    }

    SuperDialog(
        title = if (rule == null) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit),
        show = show,
        onDismissRequest = { show.value = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SmallTitle(text = "Application Info")
            Card(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = "App Name (Optional)",
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = "Package Name",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = rule == null
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SmallTitle(text = "Sources & Logic")
            Card(modifier = Modifier.fillMaxWidth()) {
                SuperSwitch(
                    title = "Media Notification Lyrics",
                    summary = "Extract from notification title",
                    checked = usesCarProtocol,
                    onCheckedChange = { usesCarProtocol = it }
                )
                if (usesCarProtocol) {
                    SuperDropdown(
                        title = "Separator",
                        items = separators,
                        selectedIndex = separatorIndex,
                        onSelectedIndexChange = { separatorIndex = it }
                    )
                    SuperDropdown(
                        title = "Field Order",
                        items = orders.map { if (it == FieldOrder.ARTIST_TITLE) "Artist - Title" else "Title - Artist" },
                        selectedIndex = orderIndex,
                        onSelectedIndexChange = { orderIndex = it }
                    )
                }
                SuperSwitch(
                    title = "Online Lyrics",
                    summary = "Fetch from internet APIs",
                    checked = useOnlineLyrics,
                    onCheckedChange = { useOnlineLyrics = it }
                )
                SuperSwitch(
                    title = "SuperLyric API",
                    summary = "Receive via broadcast",
                    checked = useSuperLyricApi,
                    onCheckedChange = { useSuperLyricApi = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.dialog_btn_cancel),
                    onClick = { show.value = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "Save",
                    onClick = {
                        if (packageName.isNotBlank()) {
                            onSave(ParserRule(
                                packageName = packageName.trim(),
                                customName = customName.trim().ifEmpty { null },
                                enabled = rule?.enabled ?: true,
                                usesCarProtocol = usesCarProtocol,
                                separatorPattern = separators[separatorIndex],
                                fieldOrder = orders[orderIndex],
                                useOnlineLyrics = useOnlineLyrics,
                                useSuperLyricApi = useSuperLyricApi
                            ))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

