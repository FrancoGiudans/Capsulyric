package com.example.islandlyrics.feature.parserrule.material

import androidx.compose.foundation.clickable
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorActivity
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.theme.material.neutralMaterialTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleScreen(
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    extraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val offlineModeEnabled = OfflineModeManager.isEnabled(context)
    var rules by remember { mutableStateOf(ParserRuleHelper.loadRules(context)) }
    var showDeleteDialog by remember { mutableStateOf<ParserRule?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                rules = ParserRuleHelper.loadRules(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Refresh recommendations on enter
    LaunchedEffect(Unit) {
        com.example.islandlyrics.service.MediaMonitorService.triggerRecheck()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumTopAppBar(
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
                        val actContext = context
                        actContext.startActivity(android.content.Intent(actContext, com.example.islandlyrics.feature.faq.FAQActivity::class.java))
                    }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = stringResource(R.string.faq_title)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = neutralMaterialTopBarColors()
            )
        },
        floatingActionButton = {
            // Recommendation Logic
            val recommendEnabled = remember { 
                context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE)
                    .getBoolean("recommend_media_app", true) 
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
                        // Pre-fill dialog with current app info
                        val pkg = currentPkg
                        // Try to get label
                        val label = try {
                            val pm = context.packageManager
                            val info = pm.getApplicationInfo(pkg, 0)
                            pm.getApplicationLabel(info).toString()
                        } catch (e: Exception) {
                            ""
                        }
                        context.startActivity(
                            android.content.Intent(context, ParserRuleEditorActivity::class.java)
                                .putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, pkg)
                                .putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, label)
                        )
                    } else {
                        context.startActivity(android.content.Intent(context, ParserRuleEditorActivity::class.java))
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
                            } catch (e: Exception) {
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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 80.dp + extraBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (rules.isEmpty()) {
                item {
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
            } else {
                item {
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
                                onToggleEnabled = { enabled ->
                                    val idx = rules.indexOf(rule)
                                    if (idx != -1) {
                                        val newRule = rule.copy(enabled = enabled)
                                        rules = rules.toMutableList().apply { set(idx, newRule) }
                                        ParserRuleHelper.saveRules(context, rules)
                                    }
                                },
                                onEdit = {
                                    context.startActivity(
                                        android.content.Intent(context, ParserRuleEditorActivity::class.java)
                                            .putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, rule.packageName)
                                    )
                                },
                                onDelete = { showDeleteDialog = rule }
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
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.parser_delete)) },
            text = { Text(stringResource(R.string.dialog_delete_confirm, showDeleteDialog?.customName ?: showDeleteDialog?.packageName ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    val newRules = rules.toMutableList()
                    newRules.remove(showDeleteDialog)
                    rules = newRules
                    ParserRuleHelper.saveRules(context, rules)
                    showDeleteDialog = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
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
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val onlineOrderSummary = if (!offlineModeEnabled && rule.useOnlineLyrics && !rule.useSmartOnlineLyricSelection) {
        OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder)
            .joinToString(" > ") { it.displayName(context) }
    } else {
        null
    }
    val packageSummary = if (!rule.customName.isNullOrEmpty()) rule.packageName else null

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
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
                onlineOrderSummary?.let {
                    Text(
                        text = context.getString(R.string.parser_online_priority_summary, it),
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
