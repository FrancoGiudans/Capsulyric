package com.example.islandlyrics.feature.locallyrics.miuix

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.lyric.LyricExporter
import com.example.islandlyrics.data.lyric.LocalLyricDirectoryManager
import com.example.islandlyrics.data.lyric.LocalLyricDirectoryManager.LrcFileInfo
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import com.example.islandlyrics.ui.miuix.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.miuixPageScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

@Composable
fun MiuixLocalLyricDirectoryScreen(
    directoryUri: Uri,
    directoryName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
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

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = directoryName,
                largeTitle = directoryName,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        dirManager.setExportDirectory(directoryUri)
                        Toast.makeText(context, context.getString(R.string.local_lyric_export_dir_set), Toast.LENGTH_SHORT).show()
                    }) {
                        androidx.compose.material3.Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = stringResource(R.string.local_lyric_set_export_dir),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
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
                        androidx.compose.material3.Icon(
                            Icons.Default.SaveAlt,
                            contentDescription = stringResource(R.string.export_lyric_button),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        popupHost = { MiuixPopupHost() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().miuixPageScroll(scrollBehavior),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            if (loading) {
                item {
                    Text(
                        text = "Loading...",
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else if (files.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.local_lyric_dir_empty),
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else {
                item {
                    SmallTitle(text = stringResource(R.string.local_lyric_dir_file_count, files.size))
                }
                items(files, key = { it.uri.toString() }) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(file.fileName, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val meta = file.metadata
                            if (meta?.title != null || meta?.artist != null) {
                                Text(
                                    text = buildString {
                                        meta.artist?.let { append(it) }
                                        if (meta.title != null && meta.artist != null) append(" - ")
                                        meta.title?.let { append(it) }
                                    },
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.local_lyric_no_metadata),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                            if (file.customMatch != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.local_lyric_custom_match_label,
                                        file.customMatch.artist, file.customMatch.title),
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                text = stringResource(R.string.local_lyric_edit_match),
                                onClick = {
                                    editTarget = file
                                    editTitle = file.customMatch?.title
                                        ?: file.metadata?.title
                                        ?: file.fileName.substringBeforeLast(".")
                                    editArtist = file.customMatch?.artist
                                        ?: file.metadata?.artist ?: ""
                                }
                            )
                        }
                    }
                }
            }
        }

        MiuixBlurDialog(
            title = editTarget?.fileName ?: "",
            summary = stringResource(R.string.local_lyric_edit_match_desc),
            show = editTarget != null,
            onDismissRequest = { editTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val meta = editTarget?.metadata
                if (meta?.title != null || meta?.artist != null) {
                    Text(
                        text = stringResource(R.string.local_lyric_metadata_hint,
                            meta?.artist.orEmpty(), meta?.title.orEmpty()),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                InputField(
                    query = editTitle,
                    onQueryChange = { editTitle = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.local_lyric_match_title)
                )
                Spacer(modifier = Modifier.height(8.dp))
                InputField(
                    query = editArtist,
                    onQueryChange = { editArtist = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.local_lyric_match_artist)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.local_lyric_reset_match),
                        onClick = {
                            editTarget?.let { dirManager.removeCustomMatch(it.uri) }
                            files = files.map {
                                if (it.uri == editTarget?.uri) it.copy(customMatch = null) else it
                            }
                            editTarget = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = {
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
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}
