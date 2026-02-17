package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaControlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.media_control_scanning)) }

    // Load controllers & Listen for changes
    DisposableEffect(Unit) {
        val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = android.content.ComponentName(context, MediaMonitorService::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers ?: emptyList()
            statusMessage = context.getString(R.string.media_control_found_sessions, activeControllers.size)
        }

        try {
            // Initial load
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            statusMessage = context.getString(R.string.media_control_found_sessions, activeControllers.size)
            // Register listener
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (e: SecurityException) {
            statusMessage = context.getString(R.string.media_control_perm_required)
        } catch (e: Exception) {
            statusMessage = context.getString(R.string.media_control_error_prefix, e.message)
        }

        onDispose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Filter by Whitelist
    val whitelistedControllers = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers.filter { enabledPackages.contains(it.packageName) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(R.string.media_control_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Status
                Text(
                    text = "$statusMessage (Whitelisted: ${whitelistedControllers.size})",
                    style = MaterialTheme.typography.bodySmall
                )

                if (whitelistedControllers.isNotEmpty()) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { whitelistedControllers.size })
                    
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        pageSpacing = 8.dp
                    ) { page ->
                        // Use a key to ensure we don't reuse state incorrectly when the list changes
                        key(whitelistedControllers[page].packageName) {
                            MediaSessionCard(controller = whitelistedControllers[page], context = context)
                        }
                    }
                    
                    // Simple Pager Indicator
                    if (whitelistedControllers.size > 1) {
                         Row(
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(whitelistedControllers.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(8.dp)
                                        .background(color, CircleShape)
                                )
                            }
                        }
                    }

                } else {
                    Text(androidx.compose.ui.res.stringResource(R.string.media_control_no_sessions), color = MaterialTheme.colorScheme.error)
                }

                Divider()

                // Mi Play (Always available)
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent()
                            intent.component = android.content.ComponentName(
                                "miui.systemui.plugin",
                                "miui.systemui.miplay.MiPlayDetailActivity"
                            )
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.media_control_miplay_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.media_control_launch_miplay))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(R.string.media_control_close))
            }
        }
    )
}

@Composable
fun MediaSessionCard(controller: MediaController, context: Context) {
    // Observable state for the controller
    var playbackState by remember(controller) { mutableStateOf(controller.playbackState) }
    var metadata by remember(controller) { mutableStateOf(controller.metadata) }

    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
            }
            override fun onMetadataChanged(meta: MediaMetadata?) {
                metadata = meta
            }
        }
        controller.registerCallback(callback)
        onDispose {
            controller.unregisterCallback(callback)
        }
    }

    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: androidx.compose.ui.res.stringResource(R.string.media_control_unknown_title)
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: androidx.compose.ui.res.stringResource(R.string.media_control_unknown_artist)
    val pkg = controller.packageName
    val appName = remember(pkg) { ParserRuleHelper.getAppNameForPackage(context, pkg) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "$appName ($pkg)", style = MaterialTheme.typography.labelSmall)
            Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(text = artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            
            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                    Icon(painterResource(R.drawable.ic_skip_previous), "Prev")
                }
                IconButton(onClick = {
                    val state = playbackState?.state
                    if (state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }) {
                     val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
                     Icon(painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow), "Toggle")
                }
                IconButton(onClick = { controller.transportControls.skipToNext() }) {
                    Icon(painterResource(R.drawable.ic_skip_next), "Next")
                }
            }
            
            // Actions
            Button(
                onClick = {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            android.widget.Toast.makeText(context, context.getString(R.string.media_control_cannot_open, pkg), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, context.getString(R.string.media_control_error_prefix, e.message), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.media_control_open_app))
            }
        }
    }
}
