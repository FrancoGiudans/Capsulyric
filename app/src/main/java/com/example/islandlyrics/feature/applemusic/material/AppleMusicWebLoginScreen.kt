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

package com.example.islandlyrics.feature.applemusic.material

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.applemusic.AppleMusicWebLoginHelper
import com.example.islandlyrics.integration.applemusic.AppleMusicSecureStore
import com.example.islandlyrics.lyrics.online.provider.AppleMusicStateCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleMusicWebLoginScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val secureStore = remember { AppleMusicSecureStore(context) }
    var statusRes by remember { mutableStateOf(R.string.apple_music_web_login_hint) }
    var captured by remember { mutableStateOf(false) }
    val webView = remember { AppleMusicWebLoginHelper.createWebView(context) }

    fun captureToken(): Boolean {
        val token = AppleMusicWebLoginHelper.readMediaUserToken() ?: return false
        if (token.isBlank()) return false
        secureStore.saveMediaUserToken(token)
        AppleMusicStateCache.setMediaUserToken(token)
        return true
    }

    LaunchedEffect(Unit) {
        while (!captured) {
            if (captureToken()) {
                captured = true
                statusRes = R.string.apple_music_web_login_captured
                val valid = withContext(Dispatchers.IO) {
                    AppleMusicWebLoginHelper.validateMediaUserToken(context)
                }
                statusRes = when (valid) {
                    true -> R.string.apple_music_web_login_valid
                    false -> R.string.apple_music_web_login_invalid
                    null -> R.string.apple_music_web_login_unknown
                }
            } else {
                delay(1500)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apple_music_web_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.apple_music_web_login_done))
            }
        }
    }
}
