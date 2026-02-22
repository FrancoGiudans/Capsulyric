package com.example.islandlyrics

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixLogViewerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    var logs by remember { mutableStateOf<List<LogManager.LogEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            logs = LogManager.getInstance().getLogEntries(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Log Console",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Share
                    IconButton(
                        onClick = {
                            val logText = logs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, logText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    // Clear
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                LogManager.getInstance().clearLog(context)
                                withContext(Dispatchers.Main) {
                                    logs = emptyList()
                                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        BasicComponent(
                            title = "No Logs",
                            summary = "The log console is empty"
                        )
                    }
                }
            } else {
                item {
                    SmallTitle(text = "${logs.size} Log Entries")
                }
                logs.forEachIndexed { index, entry ->
                    item(key = "log_$index") {
                        MiuixLogItem(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixLogItem(entry: LogManager.LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = entry.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.timestamp,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Text(
            text = entry.message,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
