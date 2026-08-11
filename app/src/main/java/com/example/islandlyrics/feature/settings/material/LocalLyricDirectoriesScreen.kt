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

package com.example.islandlyrics.feature.settings.material

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.locallyrics.LocalLyricDirectoryActivity
import com.example.islandlyrics.feature.settings.LocalLyricDirectoriesSection
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLyricDirectoriesScreen(
    onBack: () -> Unit,
    onOpenDirectory: ((Uri, String) -> Unit)? = null
) {
    val context = LocalContext.current

    MaterialBlurScaffold(
        topBar = {
            MaterialBlurTopAppBar(
                title = { Text(stringResource(R.string.settings_local_lyrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = materialPageContainerColor()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = paddingValues.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = paddingValues.calculateTopPadding(),
                end = paddingValues.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            )
        ) {
            item {
                LocalLyricDirectoriesSection(
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
}
