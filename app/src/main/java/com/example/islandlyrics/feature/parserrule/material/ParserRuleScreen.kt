package com.example.islandlyrics.feature.parserrule.material

import androidx.compose.foundation.clickable
import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.R
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleScreen(
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.parser_rule_title)) },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = ParserLocalIcons.ArrowBack,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = bottomBar,

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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(rules) { rule ->
                ParserRuleItem(
                    rule = rule,
                    onToggleEnabled = { enabled ->
                        val index = rules.indexOf(rule)
                        if (index != -1) {
                            val newRule = rule.copy(enabled = enabled)
                            rules = rules.toMutableList().apply { set(index, newRule) }
                            ParserRuleHelper.saveRules(context, rules)
                        }
                    },
                    onEdit = {
                        context.startActivity(
                            android.content.Intent(context, ParserRuleEditorActivity::class.java)
                                .putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, rule.packageName)
                        )
                    },
                    onDelete = {
                        showDeleteDialog = rule
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onLongClick = onDelete
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.customName ?: rule.packageName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!rule.customName.isNullOrEmpty()) {
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            if (rule.useOnlineLyrics && !rule.useSmartOnlineLyricSelection) {
                val onlineOrderSummary = OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder)
                    .joinToString(" > ") { it.displayName(context) }
                Text(
                    text = stringResource(R.string.parser_online_priority_summary, onlineOrderSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Status Badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(active = rule.usesCarProtocol, label = "Notify Lyric")
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(active = rule.useOnlineLyrics, label = "Online")
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(active = rule.useSuperLyricApi, label = "SuperLyric")
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(active = rule.useLyricGetterApi, label = "LyricGetter")
            }
        }
        
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggleEnabled
        )
    }
}

@Composable
fun StatusBadge(active: Boolean, label: String) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier,
        fontSize = 10.sp
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
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private object ParserLocalIcons {
    val ArrowBack: ImageVector
        get() {
            if (_arrowBack != null) return _arrowBack!!
            _arrowBack = ImageVector.Builder(
                name = "ArrowBack",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(20f, 11f)
                    horizontalLineTo(7.83f)
                    lineTo(13.42f, 5.41f)
                    lineTo(12f, 4f)
                    lineTo(4f, 12f)
                    lineTo(12f, 20f)
                    lineTo(13.41f, 18.59f)
                    lineTo(7.83f, 13f)
                    horizontalLineTo(20f)
                    close()
                }
            }.build()
            return _arrowBack!!
        }
    private var _arrowBack: ImageVector? = null
}
