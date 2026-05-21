package com.example.islandlyrics.feature.locallyrics.material

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.lyric.LyricExporter
import com.example.islandlyrics.data.lyric.LocalLyricDirectoryManager
import com.example.islandlyrics.data.lyric.LocalLyricDirectoryManager.LrcFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLyricDirectoryScreen(
    directoryUri: Uri,
    directoryName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }
    val exportScope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<LrcFileInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var editTarget by remember { mutableStateOf<LrcFileInfo?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }

    LaunchedEffect(directoryUri) {
        files = withContext(Dispatchers.IO) {
            dirManager.listFilesInDirectory(directoryUri)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(directoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        exportScope.launch {
                            val result = LyricExporter.exportCurrentLyrics(context)
                            val msg = when {
                                result.success -> context.getString(R.string.export_lyric_success, result.fileName)
                                result.error == "no_directory" -> context.getString(R.string.export_lyric_no_directory)
                                result.error == "no_lyrics" || result.error == "no_metadata" -> context.getString(R.string.export_lyric_no_lyrics)
                                else -> context.getString(R.string.export_lyric_failed)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.SaveAlt, contentDescription = stringResource(R.string.export_lyric_button))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            if (loading) {
                item { Text("Loading...", modifier = Modifier.padding(16.dp)) }
            } else if (files.isEmpty()) {
                item { Text(stringResource(R.string.local_lyric_dir_empty), modifier = Modifier.padding(16.dp)) }
            } else {
                item {
                    Text(
                        stringResource(R.string.local_lyric_dir_file_count, files.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(files, key = { it.uri.toString() }) { file ->
                    Card(
                        onClick = {
                            editTarget = file
                            editTitle = file.customMatch?.title
                                ?: file.metadata?.title
                                ?: file.fileName.substringBeforeLast(".")
                            editArtist = file.customMatch?.artist
                                ?: file.metadata?.artist ?: ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(file.fileName, style = MaterialTheme.typography.titleSmall)
                            val meta = file.metadata
                            if (meta?.title != null || meta?.artist != null) {
                                Text(
                                    buildString {
                                        meta.artist?.let { append(it) }
                                        if (meta.title != null && meta.artist != null) append(" - ")
                                        meta.title?.let { append(it) }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    stringResource(R.string.local_lyric_no_metadata),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (file.customMatch != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.local_lyric_custom_match_label,
                                        file.customMatch.artist, file.customMatch.title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editTarget != null) {
            AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { Text(editTarget?.fileName ?: "") },
                text = {
                    Column {
                        val meta = editTarget?.metadata
                        if (meta?.title != null || meta?.artist != null) {
                            Text(
                                stringResource(R.string.local_lyric_metadata_hint,
                                    meta?.artist.orEmpty(), meta?.title.orEmpty()),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text(stringResource(R.string.local_lyric_match_title)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editArtist,
                            onValueChange = { editArtist = it },
                            label = { Text(stringResource(R.string.local_lyric_match_artist)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (editTitle.isNotBlank()) {
                            editTarget?.let { file ->
                                dirManager.setCustomMatch(file.uri, editTitle.trim(), editArtist.trim())
                                val newMatch = LocalLyricDirectoryManager.CustomMatch(
                                    file.uri.toString(), editTitle.trim(), editArtist.trim()
                                )
                                files = files.map {
                                    if (it.uri == file.uri) it.copy(customMatch = newMatch) else it
                                }
                            }
                        }
                        editTarget = null
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            editTarget?.let { dirManager.removeCustomMatch(it.uri) }
                            files = files.map {
                                if (it.uri == editTarget?.uri) it.copy(customMatch = null) else it
                            }
                            editTarget = null
                        }) { Text(stringResource(R.string.local_lyric_reset_match)) }
                        TextButton(onClick = { editTarget = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            )
        }
    }
}
