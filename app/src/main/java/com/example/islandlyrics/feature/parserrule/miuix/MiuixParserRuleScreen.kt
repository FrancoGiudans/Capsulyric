package com.example.islandlyrics.feature.parserrule.miuix

import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import android.content.Context
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.R
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.LyricRepository
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
import androidx.compose.material.icons.filled.Info
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

    MiuixBackHandler(enabled = showEditDialog.value) { showEditDialog.value = false }
    MiuixBackHandler(enabled = showDeleteDialog.value) { showDeleteDialog.value = false }

    fun refreshRules() {
        rules = ParserRuleHelper.loadRules(context)
    }

    // Refresh recommendations on enter
    LaunchedEffect(Unit) {
        com.example.islandlyrics.service.MediaMonitorService.triggerRecheck()
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
                },
                actions = {
                    IconButton(onClick = {
                        val actContext = context
                        actContext.startActivity(android.content.Intent(actContext, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                    }, modifier = Modifier.padding(end = 12.dp)) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = stringResource(R.string.faq_title),
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
                        val info = pm.getApplicationInfo(currentPkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            FloatingActionButton(
                onClick = {
                    if (showRecommendation) {
                        val pkg = currentPkg
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
                            text = stringResource(R.string.parser_add_app_fmt, appLabel ?: stringResource(R.string.parser_current_app)),
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
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 80.dp
            )
        ) {
            if (rules.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = stringResource(R.string.parser_no_rules),
                            summary = stringResource(R.string.parser_no_rules_desc)
                        )
                    }
                }
            } else {
                item {
                    SmallTitle(text = stringResource(R.string.parser_rules_count, rules.size))
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

        // Dialogs must be inside Scaffold content for MiuixPopupHost
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
                        Toast.makeText(context, context.getString(R.string.parser_pkg_exists), Toast.LENGTH_SHORT).show()
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

        SuperDialog(
            title = stringResource(R.string.parser_delete),
            summary = stringResource(R.string.dialog_delete_confirm, deletingRule?.customName ?: deletingRule?.packageName ?: ""),
            show = showDeleteDialog.value,
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
                Spacer(modifier = Modifier.width(8.dp))
                MiuixStatusBadge(active = rule.useLyricGetterApi, label = "LGetter")
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
    var packageName by remember(rule, show.value) { mutableStateOf(rule?.packageName ?: "") }
    var customName by remember(rule, show.value) { mutableStateOf(rule?.customName ?: "") }
    var usesCarProtocol by remember(rule, show.value) { mutableStateOf(rule?.usesCarProtocol ?: true) }
    var useOnlineLyrics by remember(rule, show.value) { mutableStateOf(rule?.useOnlineLyrics ?: false) }
    var useSuperLyricApi by remember(rule, show.value) { mutableStateOf(rule?.useSuperLyricApi ?: false) }
    var useLyricGetterApi by remember(rule, show.value) { mutableStateOf(rule?.useLyricGetterApi ?: false) }
    
    val separators = listOf("-", " - ", " | ")
    var separatorIndex by remember(rule, show.value) { 
        val idx = separators.indexOf(rule?.separatorPattern ?: "-")
        mutableStateOf(if (idx >= 0) idx else 0)
    }
    
    val orders = listOf(FieldOrder.ARTIST_TITLE, FieldOrder.TITLE_ARTIST)
    var orderIndex by remember(rule, show.value) {
        val idx = orders.indexOf(rule?.fieldOrder ?: FieldOrder.TITLE_ARTIST)
        mutableStateOf(if (idx >= 0) idx else 0)
    }

    SuperDialog(
        title = if (rule == null) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit),
        show = show.value,
        onDismissRequest = { show.value = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Scrollable Content Area for form fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                SmallTitle(text = stringResource(R.string.parser_app_info))
                Card(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = stringResource(R.string.parser_app_name),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = stringResource(R.string.parser_package_name),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = rule == null
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SmallTitle(text = stringResource(R.string.parser_logic_header))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperSwitch(
                        title = stringResource(R.string.parser_car_protocol),
                        summary = stringResource(R.string.parser_notify_lyric_desc),
                        checked = usesCarProtocol,
                        onCheckedChange = { usesCarProtocol = it }
                    )
                    if (usesCarProtocol) {
                        SuperDropdown(
                            title = stringResource(R.string.parser_separator_label),
                            items = separators,
                            selectedIndex = separatorIndex,
                            onSelectedIndexChange = { separatorIndex = it }
                        )
                        SuperDropdown(
                            title = stringResource(R.string.parser_field_order_label),
                            items = orders.map { if (it == FieldOrder.ARTIST_TITLE) stringResource(R.string.parser_order_artist_title) else stringResource(R.string.parser_order_title_artist) },
                            selectedIndex = orderIndex,
                            onSelectedIndexChange = { orderIndex = it }
                        )
                    }
                    SuperSwitch(
                        title = stringResource(R.string.settings_use_online_lyrics),
                        summary = stringResource(R.string.parser_online_lyric_desc_short),
                        checked = useOnlineLyrics,
                        onCheckedChange = { useOnlineLyrics = it }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.parser_super_lyric),
                        summary = stringResource(R.string.parser_super_lyric_desc_short),
                        checked = useSuperLyricApi,
                        onCheckedChange = { useSuperLyricApi = it }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.parser_lgetter_lyric),
                        summary = stringResource(R.string.parser_lgetter_lyric_desc_short),
                        checked = useLyricGetterApi,
                        onCheckedChange = { useLyricGetterApi = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SmallTitle(text = stringResource(R.string.settings_help_about_header))
                Card(modifier = Modifier.fillMaxWidth()) {
                    val actContext = LocalContext.current
                    top.yukonga.miuix.kmp.extra.SuperArrow(
                        title = stringResource(R.string.faq_title),
                        summary = stringResource(R.string.summary_faq),
                        onClick = {
                            actContext.startActivity(android.content.Intent(actContext, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fixed Action Buttons Row (persistent at the bottom)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.dialog_btn_cancel),
                    onClick = { show.value = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = stringResource(R.string.parser_save),
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
                                useSuperLyricApi = useSuperLyricApi,
                                useLyricGetterApi = useLyricGetterApi
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

