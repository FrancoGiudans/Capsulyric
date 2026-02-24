package com.example.islandlyrics

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixLogViewerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var filterLevel by remember { mutableStateOf("ALL") } // ALL, E, W, D
    var originalLogs by remember { mutableStateOf<List<LogManager.LogEntry>>(emptyList()) }
    var showExportDialog = remember { mutableStateOf(false) }
    var showClearDialog = remember { mutableStateOf(false) }

    // Load logs
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = LogManager.getInstance().getLogEntries(context)
            withContext(Dispatchers.Main) {
                originalLogs = loaded
                if (loaded.isNotEmpty()) {
                    listState.scrollToItem(loaded.size - 1)
                }
            }
        }
    }

    // Filtered logs
    val logs = remember(searchQuery, filterLevel, originalLogs) {
        originalLogs.filter { entry ->
            val levelMatch = filterLevel == "ALL" || entry.level == filterLevel
            val textMatch = searchQuery.isEmpty() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true)
            levelMatch && textMatch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Log Console",
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
                    // Export
                    IconButton(
                        onClick = { showExportDialog.value = true },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "ExportDialog",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    // Save
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val content = logs.joinToString("\n") { "${it.timestamp} ${it.level}/${it.tag}: ${it.message}" }
                                val filename = "Log_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())}.txt"
                                val uri = LogManager.getInstance().saveLogToDownloads(context, content, filename)
                                withContext(Dispatchers.Main) {
                                    if (uri != null) {
                                        Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Save",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    // Clear
                    IconButton(
                        onClick = { showClearDialog.value = true },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (logs.isNotEmpty()) {
                            listState.animateScrollToItem(logs.size - 1)
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown, 
                    contentDescription = "Bottom",
                    tint = MiuixTheme.colorScheme.onPrimary
                )
            }
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search & Filter
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Search Logs",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val levels = listOf("ALL", "E", "W", "D")
                        levels.forEach { level ->
                            MiuixFilterPill(
                                label = level,
                                selected = filterLevel == level,
                                onClick = { filterLevel = level },
                                color = when (level) {
                                    "E" -> Color(0xFFE57373)
                                    "W" -> Color(0xFFFFB74D)
                                    "D" -> MiuixTheme.colorScheme.primary
                                    else -> null
                                }
                            )
                        }
                    }
                }
            }

            // List
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            BasicComponent(
                                title = "No logs match",
                                summary = "Try changing filters"
                            )
                        }
                    }
                } else {
                    items(logs.size, key = { "log_$it" }) { index ->
                        MiuixLogItem(entry = logs[index])
                    }
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog.value) {
        var selectedIndex by remember { mutableStateOf(1) } // Default 24h
        val options = listOf("Last 1 Hour", "Last 24 Hours", "All Time")
        
        SuperDialog(
            title = "Export Logs",
            show = showExportDialog,
            onDismissRequest = { showExportDialog.value = false }
        ) {
            Column {
                SuperDropdown(
                    title = "Time Range",
                    items = options,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "Cancel",
                        onClick = { showExportDialog.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "Export",
                        onClick = {
                            val timeRange = when (selectedIndex) {
                                0 -> 60 * 60 * 1000L
                                1 -> 24 * 60 * 60 * 1000L
                                else -> -1L
                            }
                            LogManager.getInstance().exportLog(context, timeRange)
                            showExportDialog.value = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }

    // Clear Confirmation
    if (showClearDialog.value) {
        SuperDialog(
            title = "Clear Logs",
            summary = "This will permanently delete all logs.",
            show = showClearDialog,
            onDismissRequest = { showClearDialog.value = false }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "Cancel",
                    onClick = { showClearDialog.value = false },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "Clear",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            LogManager.getInstance().clearLog(context)
                            withContext(Dispatchers.Main) {
                                originalLogs = emptyList()
                                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                                showClearDialog.value = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun MiuixFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) (color ?: MiuixTheme.colorScheme.onBackground).copy(alpha = 0.15f)
                else Color.Transparent
            )
            .then(
                if (!selected) Modifier.background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.05f))
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) (color ?: MiuixTheme.colorScheme.primary) 
                    else MiuixTheme.colorScheme.onSurfaceVariantActions
        )
    }
}

@Composable
private fun MiuixLogItem(entry: LogManager.LogEntry) {
    val levelColor = when (entry.level) {
        "E" -> Color(0xFFE57373)
        "W" -> Color(0xFFFFB74D)
        "D" -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = entry.level,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = levelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.timestamp.substringAfter(" "),
                fontSize = 10.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Text(
            text = entry.message,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

