/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *  *
 *  *
 */

package com.example.islandlyrics.feature.settings.miuix

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.locallyrics.LocalLyricDirectoryActivity
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurTopAppBar
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixLocalLyricDirectoriesScreen(
    onBack: () -> Unit,
    onOpenDirectory: ((Uri, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val state = rememberLocalLyricDirectoriesState()

    MiuixBlurScaffold(
        topBar = {
            MiuixBlurTopAppBar(
                title = stringResource(R.string.settings_local_lyrics_title),
                largeTitle = stringResource(R.string.settings_local_lyrics_title),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
                        MiuixBackIcon(contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                MiuixLocalLyricDirectoriesContent(
                    state = state,
                    showHeader = false,
                    onOpenDirectory = onOpenDirectory
                        ?: { uri, name ->
                            context.startActivity(
                                Intent(context, LocalLyricDirectoryActivity::class.java).apply {
                                    putExtra(LocalLyricDirectoryActivity.EXTRA_DIRECTORY_URI, uri.toString())
                                    putExtra(LocalLyricDirectoryActivity.EXTRA_DIRECTORY_NAME, name)
                                }
                            )
                        }
                )
            }
        }
    }

    MiuixLocalLyricDirectoriesDialog(state)
}
