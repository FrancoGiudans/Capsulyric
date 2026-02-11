package com.example.islandlyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parser_rule_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingRule = null
                showEditDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
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
                    // Update
                    val index = newRules.indexOf(editingRule)
                    if (index != -1) {
                        newRules[index] = newRule
                        newRules.sort() // Re-sort in case name changed? No, sort is by package usually.
                        rules = newRules
                        ParserRuleHelper.saveRules(context, rules)
                        showEditDialog = false
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
            .clickable { onEdit() }
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
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
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
