package com.example.islandlyrics.feature.settings.miuix

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.lyric.LocalLyricDirectoryManager
import com.example.islandlyrics.feature.locallyrics.LocalLyricDirectoryActivity
import com.example.islandlyrics.ui.miuix.MiuixBlurDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference as SuperArrow

class MiuixLocalLyricDirectoriesState {
    var directories by mutableStateOf<List<LocalLyricDirectoryManager.DirectoryEntry>>(emptyList())
    var removeTarget by mutableStateOf<Uri?>(null)

    fun refresh(context: android.content.Context) {
        directories = LocalLyricDirectoryManager.getInstance(context).getDirectories()
    }
}

@Composable
fun rememberLocalLyricDirectoriesState(): MiuixLocalLyricDirectoriesState {
    val context = LocalContext.current
    val state = remember { MiuixLocalLyricDirectoriesState() }
    LaunchedEffect(Unit) { state.refresh(context) }
    return state
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MiuixLocalLyricDirectoriesContent(
    state: MiuixLocalLyricDirectoriesState,
    onOpenDirectory: ((Uri, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            dirManager.addDirectory(uri)
            state.refresh(context)
        }
    }

    SmallTitle(text = stringResource(R.string.settings_local_lyrics_title))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (state.directories.isEmpty()) {
            SuperArrow(
                title = stringResource(R.string.settings_local_lyrics_empty),
                onClick = { dirPickerLauncher.launch(null) }
            )
        } else {
            state.directories.forEach { entry ->
                val isExportDir = dirManager.isExportDirectory(entry.uri)
                SuperArrow(
                    title = entry.displayName,
                    summary = if (isExportDir) stringResource(R.string.local_lyric_export_dir_badge)
                              else stringResource(R.string.settings_local_lyrics_directories),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            onOpenDirectory?.invoke(entry.uri, entry.displayName)
                                ?: context.startActivity(Intent(context, LocalLyricDirectoryActivity::class.java).apply {
                                    putExtra(LocalLyricDirectoryActivity.EXTRA_DIRECTORY_URI, entry.uri.toString())
                                    putExtra(LocalLyricDirectoryActivity.EXTRA_DIRECTORY_NAME, entry.displayName)
                                })
                        },
                        onLongClick = {
                            state.removeTarget = entry.uri
                        }
                    ),
                    holdDownState = state.removeTarget == entry.uri
                )
            }
            SuperArrow(
                title = stringResource(R.string.settings_local_lyrics_add),
                onClick = { dirPickerLauncher.launch(null) }
            )
        }
    }
}

@Composable
fun MiuixLocalLyricDirectoriesDialog(state: MiuixLocalLyricDirectoriesState) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }
    val removeTargetName = remember(state.removeTarget, state.directories) {
        state.directories.firstOrNull { it.uri == state.removeTarget }?.displayName
    }

    MiuixBlurDialog(
        title = removeTargetName?.let {
            stringResource(R.string.settings_local_lyrics_remove_confirm_named, it)
        } ?: stringResource(R.string.settings_local_lyrics_remove_confirm),
        show = state.removeTarget != null,
        onDismissRequest = { state.removeTarget = null }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = { state.removeTarget = null },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                text = stringResource(android.R.string.ok),
                onClick = {
                    state.removeTarget?.let { uri ->
                        dirManager.removeDirectory(uri)
                        state.refresh(context)
                    }
                    state.removeTarget = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
