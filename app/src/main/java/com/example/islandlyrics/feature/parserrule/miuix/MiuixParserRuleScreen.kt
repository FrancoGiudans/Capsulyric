package com.example.islandlyrics.feature.parserrule.miuix

import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import android.content.Context
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.preference.SwitchPreference as SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import com.example.islandlyrics.ui.miuix.*
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun MiuixParserRuleScreen(
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    onBottomBarVisibilityChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    var rules by remember { mutableStateOf(ParserRuleHelper.loadRules(context)) }
    var showDeleteDialog = remember { mutableStateOf(false) }
    var deletingRule by remember { mutableStateOf<ParserRule?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    MiuixBackHandler(enabled = showDeleteDialog.value) { showDeleteDialog.value = false }

    fun refreshRules() {
        rules = ParserRuleHelper.loadRules(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshRules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listState) {
        var previousIndex = 0
        var previousOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                val delta = when {
                    index != previousIndex -> (index - previousIndex) * 10_000 + (offset - previousOffset)
                    else -> offset - previousOffset
                }
                previousIndex = index
                previousOffset = offset
                delta
            }
            .distinctUntilChanged()
            .collect { delta ->
                when {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 -> onBottomBarVisibilityChange(true)
                    delta > 6 -> onBottomBarVisibilityChange(false)
                    delta < -6 -> onBottomBarVisibilityChange(true)
                }
            }
    }

    // Refresh recommendations on enter
    LaunchedEffect(Unit) {
        com.example.islandlyrics.service.MediaMonitorService.triggerRecheck()
    }

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.parser_rule_title),
                largeTitle = stringResource(R.string.parser_rule_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                } else {
                    {}
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
        bottomBar = bottomBar,
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
                        context.startActivity(
                            android.content.Intent(context, ParserRuleEditorActivity::class.java)
                                .putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, pkg)
                                .putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, appLabel)
                        )
                    } else {
                        context.startActivity(android.content.Intent(context, ParserRuleEditorActivity::class.java))
                    }
                },
                modifier = Modifier.padding(bottom = 108.dp, end = 8.dp)
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 136.dp
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
                                offlineModeEnabled = offlineModeEnabled,
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(context, ParserRuleEditorActivity::class.java)
                                            .putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, rule.packageName)
                                    )
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
        MiuixBlurDialog(
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
    offlineModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
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
            if (!offlineModeEnabled && rule.useOnlineLyrics && !rule.useSmartOnlineLyricSelection) {
                val orderSummary = OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder)
                    .joinToString(" > ") { it.displayName(context) }
                Text(
                    text = stringResource(R.string.parser_online_priority_summary, orderSummary),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixStatusBadge(active = rule.usesCarProtocol, label = "Notify")
                if (!offlineModeEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    MiuixStatusBadge(active = rule.useOnlineLyrics, label = "Online")
                }
                Spacer(modifier = Modifier.width(8.dp))
                MiuixStatusBadge(active = rule.useSuperLyricApi, label = "Super")
                Spacer(modifier = Modifier.width(8.dp))
                MiuixStatusBadge(active = rule.useLyricGetterApi, label = "LGetter")
                Spacer(modifier = Modifier.width(8.dp))
                MiuixStatusBadge(active = rule.useLyriconApi, label = "Lyricon")
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

