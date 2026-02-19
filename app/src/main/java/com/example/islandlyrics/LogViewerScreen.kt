package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var filterLevel by remember { mutableStateOf("ALL") } // ALL, E, W, D
    var logs by remember { mutableStateOf<List<LogManager.LogEntry>>(emptyList()) }
    var originalLogs by remember { mutableStateOf<List<LogManager.LogEntry>>(emptyList()) }
    val listState = rememberLazyListState()

    // Load logs
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = LogManager.getInstance().getLogEntries(context)
            withContext(Dispatchers.Main) {
                originalLogs = loaded
                logs = loaded
                if (logs.isNotEmpty()) {
                    listState.scrollToItem(logs.size - 1)
                }
            }
        }
    }

    // Filter logic
    LaunchedEffect(searchQuery, filterLevel, originalLogs) {
        logs = originalLogs.filter { entry ->
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
                title = { Text("Log Console") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export
                    IconButton(onClick = {
                        val options = arrayOf("Last 1 Hour", "Last 24 Hours", "All Time")
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("Export Logs")
                            .setItems(options) { _, which ->
                                val timeRange = when (which) {
                                    0 -> 60 * 60 * 1000L
                                    1 -> 24 * 60 * 60 * 1000L
                                    else -> -1L
                                }
                                LogManager.getInstance().exportLog(context, timeRange)
                            }
                            .show()
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    // Save
                    IconButton(onClick = {
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
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Save")
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
                }
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll Down")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search & Filter
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Logs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterLevel == "ALL",
                        onClick = { filterLevel = "ALL" },
                        label = { Text("ALL") }
                    )
                    FilterChip(
                        selected = filterLevel == "E",
                        onClick = { filterLevel = "E" },
                        label = { Text("Error") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer)
                    )
                    FilterChip(
                        selected = filterLevel == "W",
                        onClick = { filterLevel = "W" },
                        label = { Text("Warn") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    )
                    FilterChip(
                        selected = filterLevel == "D",
                        onClick = { filterLevel = "D" },
                        label = { Text("Debug") }
                    )
                }
            }

            // List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { entry ->
                    LogItem(entry)
                }
            }
        }
    }
}

@Composable
fun LogItem(entry: LogManager.LogEntry) {
    val color = when (entry.level) {
        "E" -> MaterialTheme.colorScheme.error
        "W" -> Color(0xFFFFA000) // Orange-ish
        "D" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.level,
                    color = color,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = entry.timestamp.substringAfter(" "), // Show only time
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                fontSize = 12.sp
            )
        }
    }
}
