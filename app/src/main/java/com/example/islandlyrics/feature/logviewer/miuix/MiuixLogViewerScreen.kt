package com.example.islandlyrics.feature.logviewer.miuix

import com.example.islandlyrics.ui.miuix.MiuixBackHandler
import android.content.Context
import com.example.islandlyrics.core.logging.AppLogger
import android.content.Intent
import com.example.islandlyrics.core.logging.LogManager
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.islandlyrics.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as SuperDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar

private enum class LogAction { SHARE, SAVE }

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
    var recordLevel by remember {
        mutableStateOf(
            AppLogger.LogLevel.fromPreference(
                context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                    .getString(AppLogger.PREF_LOG_RECORD_LEVEL, null)
            )
        )
    }
    var originalLogs by remember { mutableStateOf<List<LogManager.LogEntry>>(emptyList()) }
    var currentAction by remember { mutableStateOf(LogAction.SHARE) }
    val showExportDialog = remember { mutableStateOf(false) }
    val showClearDialog = remember { mutableStateOf(false) }
    val levelError = stringResource(R.string.log_viewer_level_error)
    val levelWarnPlus = stringResource(R.string.log_viewer_level_warn_plus)
    val levelInfoPlus = stringResource(R.string.log_viewer_level_info_plus)
    val levelDebugPlus = stringResource(R.string.log_viewer_level_debug_plus)
    val recordLevelOptions = listOf(
        AppLogger.LogLevel.ERROR to levelError,
        AppLogger.LogLevel.WARN to levelWarnPlus,
        AppLogger.LogLevel.INFO to levelInfoPlus,
        AppLogger.LogLevel.DEBUG to levelDebugPlus
    )

    MiuixBackHandler(enabled = showExportDialog.value) { showExportDialog.value = false }
    MiuixBackHandler(enabled = showClearDialog.value) { showClearDialog.value = false }

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

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.log_viewer_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.log_viewer_back),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Export
                    IconButton(
                        onClick = { 
                            currentAction = LogAction.SHARE
                            showExportDialog.value = true 
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.log_viewer_export),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    // Save
                    IconButton(
                        onClick = {
                            currentAction = LogAction.SAVE
                            showExportDialog.value = true
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.log_viewer_save),
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
                            contentDescription = stringResource(R.string.log_viewer_clear),
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
                    contentDescription = stringResource(R.string.log_viewer_scroll_bottom),
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
                        label = stringResource(R.string.log_viewer_search),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val levels = listOf(
                            "ALL" to stringResource(R.string.log_viewer_level_all),
                            "E" to stringResource(R.string.log_viewer_level_error),
                            "W" to stringResource(R.string.log_viewer_level_warn),
                            "D" to stringResource(R.string.log_viewer_level_debug)
                        )
                        levels.forEach { (level, label) ->
                            MiuixFilterPill(
                                label = label,
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
                    Spacer(modifier = Modifier.height(8.dp))
                    SuperDropdown(
                        title = stringResource(R.string.log_viewer_record_level),
                        summary = stringResource(R.string.log_viewer_record_level_summary),
                        items = recordLevelOptions.map { it.second },
                        selectedIndex = recordLevelOptions.indexOfFirst { it.first == recordLevel }.coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val level = recordLevelOptions[index].first
                            recordLevel = level
                            context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString(AppLogger.PREF_LOG_RECORD_LEVEL, level.preferenceValue)
                                .apply()
                            AppLogger.getInstance().setMinimumLevel(level)
                        }
                    )
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
                                title = stringResource(R.string.log_viewer_no_match_title),
                                summary = stringResource(R.string.log_viewer_no_match_summary)
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

        // Export Dialog — must be inside Scaffold lambda for MiuixPopupHost
        var selectedIndex by remember { mutableStateOf(1) }
        val exportOptions = listOf(
            stringResource(R.string.log_viewer_time_last_1h),
            stringResource(R.string.log_viewer_time_last_24h),
            stringResource(R.string.log_viewer_time_all)
        )

        MiuixBlurDialog(
            title = stringResource(R.string.log_viewer_export_title),
            show = showExportDialog.value,
            renderInRootScaffold = false,
            onDismissRequest = { showExportDialog.value = false }
        ) {
            Column {
                SuperDropdown(
                    title = stringResource(R.string.log_viewer_time_range),
                    items = exportOptions,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = stringResource(R.string.log_viewer_cancel),
                        onClick = { showExportDialog.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = if (currentAction == LogAction.SHARE) stringResource(R.string.log_viewer_export) else stringResource(R.string.log_viewer_save),
                        onClick = {
                            val timeRange = when (selectedIndex) {
                                0 -> 60 * 60 * 1000L
                                1 -> 24 * 60 * 60 * 1000L
                                else -> -1L
                            }
                            if (currentAction == LogAction.SHARE) {
                                LogManager.getInstance().exportLog(context, timeRange)
                            } else {
                                LogManager.getInstance().exportLogToDownloads(context, timeRange)
                            }
                            showExportDialog.value = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }

        // Clear Confirmation Dialog
        MiuixBlurDialog(
            title = stringResource(R.string.log_viewer_clear_title),
            summary = stringResource(R.string.log_viewer_clear_message),
            show = showClearDialog.value,
            renderInRootScaffold = false,
            onDismissRequest = { showClearDialog.value = false }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.log_viewer_cancel),
                    onClick = { showClearDialog.value = false },
                    modifier = Modifier.weight(1f)
                )
                val logsClearedMessage = stringResource(R.string.log_viewer_cleared)
                TextButton(
                    text = stringResource(R.string.log_viewer_clear),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            LogManager.getInstance().clearLog(context)
                            withContext(Dispatchers.Main) {
                                originalLogs = emptyList()
                                Toast.makeText(context, logsClearedMessage, Toast.LENGTH_SHORT).show()
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

