package com.example.islandlyrics.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.lyric.LocalLyricDirectoryManager
import com.example.islandlyrics.feature.locallyrics.LocalLyricDirectoryActivity
import com.example.islandlyrics.feature.settings.material.SettingsCard
import com.example.islandlyrics.feature.settings.material.SettingsCardDivider
import com.example.islandlyrics.feature.settings.material.SettingsSectionHeader

@Composable
fun LocalLyricDirectoriesSection(
    onOpenDirectory: ((Uri, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val dirManager = remember { LocalLyricDirectoryManager.getInstance(context) }
    var directories by remember { mutableStateOf(dirManager.getDirectories()) }
    var showRemoveDialog by remember { mutableStateOf<Uri?>(null) }
    val removeTargetName = remember(showRemoveDialog, directories) {
        directories.firstOrNull { it.uri == showRemoveDialog }?.displayName
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            dirManager.addDirectory(uri)
            directories = dirManager.getDirectories()
        }
    }

    SettingsSectionHeader(text = stringResource(R.string.settings_local_lyrics_title))
    SettingsCard {
        if (directories.isEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_local_lyrics_empty)) },
                leadingContent = {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { dirPickerLauncher.launch(null) }
            )
        } else {
            directories.forEachIndexed { index, entry ->
                ListItem(
                    headlineContent = { Text(entry.displayName) },
                    supportingContent = { Text(stringResource(R.string.settings_local_lyrics_directories)) },
                    leadingContent = {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        IconButton(onClick = { showRemoveDialog = entry.uri }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onOpenDirectory?.invoke(entry.uri, entry.displayName)
                            ?: context.startActivity(Intent(context, LocalLyricDirectoryActivity::class.java).apply {
                                putExtra(LocalLyricDirectoryActivity.EXTRA_DIRECTORY_URI, entry.uri.toString())
                                putExtra(LocalLyricDirectoryActivity.EXTRA_DIRECTORY_NAME, entry.displayName)
                            })
                    }
                )
                if (index < directories.size - 1) SettingsCardDivider()
            }
            SettingsCardDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_local_lyrics_add)) },
                leadingContent = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { dirPickerLauncher.launch(null) }
            )
        }
    }

    if (showRemoveDialog != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = {
                Text(
                    removeTargetName?.let {
                        stringResource(R.string.settings_local_lyrics_remove_confirm_named, it)
                    } ?: stringResource(R.string.settings_local_lyrics_remove_confirm)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dirManager.removeDirectory(showRemoveDialog!!)
                    directories = dirManager.getDirectories()
                    showRemoveDialog = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
