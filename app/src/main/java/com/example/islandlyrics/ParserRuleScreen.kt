package com.example.islandlyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserRuleScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var rules by remember { mutableStateOf(ParserRuleHelper.loadRules(context)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ParserRule?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ParserRule?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.parser_rule_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ParserLocalIcons.ArrowBack,
                            contentDescription = "Back"
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
                        
                        editingRule = ParserRuleHelper.createDefaultRule(pkg).copy(customName = label)
                        showEditDialog = true
                    } else {
                        // Standard Add
                        editingRule = null
                        showEditDialog = true
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
                        Text(text = "Add ${appLabel ?: "Current App"}")
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
                        editingRule = rule
                        showEditDialog = true
                    },
                    onDelete = {
                        showDeleteDialog = rule
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    if (showEditDialog) {
        EditRuleDialog(
            rule = editingRule,
            onDismiss = { showEditDialog = false },
            onSave = { newRule ->
                val newRules = rules.toMutableList()
                if (editingRule == null) {
                    // Add
                    if (newRules.any { it.packageName == newRule.packageName }) {
                        android.widget.Toast.makeText(context, "Package already exists", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        newRules.add(newRule)
                        newRules.sort()
                        rules = newRules
                        ParserRuleHelper.saveRules(context, rules)
                        showEditDialog = false
                    }
                } else {
                    // Update or Add Recommendation
                    val index = newRules.indexOf(editingRule)
                    if (index != -1) {
                        newRules[index] = newRule
                        newRules.sort() 
                        rules = newRules
                        ParserRuleHelper.saveRules(context, rules)
                        showEditDialog = false
                    } else {
                        // Recommendation case: editingRule not in list
                        if (newRules.any { it.packageName == newRule.packageName }) {
                            android.widget.Toast.makeText(context, "Package already exists", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            newRules.add(newRule)
                            newRules.sort()
                            rules = newRules
                            ParserRuleHelper.saveRules(context, rules)
                            showEditDialog = false
                        }
                    }
                }
            }
        )
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
            
            // Status Badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(active = rule.usesCarProtocol, label = "Notify Lyric")
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(active = rule.useOnlineLyrics, label = "Online")
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(active = rule.useSuperLyricApi, label = "SuperLyric")
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
fun EditRuleDialog(
    rule: ParserRule?,
    onDismiss: () -> Unit,
    onSave: (ParserRule) -> Unit
) {
    var packageName by remember { mutableStateOf(rule?.packageName ?: "") }
    var customName by remember { mutableStateOf(rule?.customName ?: "") }
    
    // Logic Switches
    var usesCarProtocol by remember { mutableStateOf(rule?.usesCarProtocol ?: true) }
    var useOnlineLyrics by remember { mutableStateOf(rule?.useOnlineLyrics ?: false) }
    var useSuperLyricApi by remember { mutableStateOf(rule?.useSuperLyricApi ?: true) }
    
    // Parser Config
    var separator by remember { mutableStateOf(rule?.separatorPattern ?: "-") }
    var fieldOrder by remember { mutableStateOf(rule?.fieldOrder ?: FieldOrder.ARTIST_TITLE) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (rule == null) stringResource(R.string.parser_add_rule) else stringResource(R.string.parser_edit),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_btn_cancel))
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. App Info
                    SettingsSectionHeader("Application Info")
                    
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("App Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = rule == null // Cannot change pkg of existing rule
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. Sources & Features (Grouped)
                    SettingsSectionHeader("Lyric Sources & Features")
                    
                    // A. Media Notification (Car Protocol)
                    SwitchRow(
                        title = "Media Notification Lyrics",
                        subtitle = "Extract lyrics from notification title. Enables 'Smart Detection'.",
                        checked = usesCarProtocol,
                        onCheckedChange = { usesCarProtocol = it }
                    )

                    // B. Parser Configuration (Only if Car Protocol is ON)
                    if (usesCarProtocol) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Parsing Logic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Separator Selector
                                Text("Separator", style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = separator == "-",
                                        onClick = { separator = "-" },
                                        label = { Text("Tight (-)") }
                                    )
                                    FilterChip(
                                        selected = separator == " - ",
                                        onClick = { separator = " - " },
                                        label = { Text("Spaced ( - )") }
                                    )
                                    FilterChip(
                                        selected = separator == " | ",
                                        onClick = { separator = " | " },
                                        label = { Text("Pipe ( | )") }
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Order Selector
                                Text("Field Order", style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = fieldOrder == FieldOrder.ARTIST_TITLE,
                                        onClick = { fieldOrder = FieldOrder.ARTIST_TITLE },
                                        label = { Text("Artist - Title") }
                                    )
                                    FilterChip(
                                        selected = fieldOrder == FieldOrder.TITLE_ARTIST,
                                        onClick = { fieldOrder = FieldOrder.TITLE_ARTIST },
                                        label = { Text("Title - Artist") }
                                    )
                                }
                            }
                        }
                    }

                    // C. Online Lyrics
                    SwitchRow(
                        title = "Online Lyrics",
                        subtitle = "Fetch missing lyrics from internet APIs",
                        checked = useOnlineLyrics,
                        onCheckedChange = { useOnlineLyrics = it }
                    )

                    // D. SuperLyric API
                    SwitchRow(
                        title = "SuperLyric API",
                        subtitle = "Receive lyrics from supported apps via broadcast",
                        checked = useSuperLyricApi,
                        onCheckedChange = { useSuperLyricApi = it }
                    )
                }

                // Footer Actions
                Button(
                    onClick = {
                        if (packageName.isBlank()) {
                            // Show error
                        } else {
                            onSave(
                                ParserRule(
                                    packageName = packageName.trim(),
                                    customName = customName.trim().ifEmpty { null },
                                    enabled = rule?.enabled ?: true,
                                    usesCarProtocol = usesCarProtocol,
                                    separatorPattern = separator,
                                    fieldOrder = fieldOrder,
                                    useOnlineLyrics = useOnlineLyrics,
                                    useSuperLyricApi = useSuperLyricApi
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Rule")
                }
            }
        }
    }
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
