package com.example.islandlyrics

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Status colors matching colors.xml
private val StatusActive = Color(0xFF4CAF50)
private val StatusInactive = Color(0xFFF44336)

@Composable
fun MainScreen(
    versionText: String,
    isDebugBuild: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenPromotedSettings: () -> Unit,
    onStatusCardTap: () -> Unit,
    apiPermissionText: String,
    apiCapabilityText: String,
    apiFlagText: String,
    apiPermissionActive: Boolean,
    apiCapabilityActive: Boolean,
    apiFlagActive: Boolean,
    showApiCard: Boolean,
) {
    val repo = remember { LyricRepository.getInstance() }
    val context = LocalContext.current

    // Observe LiveData as Compose State
    val isPlaying by repo.isPlaying.observeAsState(false)
    val metadata by repo.liveMetadata.observeAsState()
    val lyricInfo by repo.liveLyric.observeAsState()
    val albumArt by repo.liveAlbumArt.observeAsState()
    val progress by repo.liveProgress.observeAsState()

    // Derive status card state
    val listenerEnabled = remember(Unit) {
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        listeners?.contains(context.packageName) == true
    }

    val serviceConnected = MediaMonitorService.isConnected

    val statusText = when {
        !listenerEnabled -> "Permission Required"
        !serviceConnected -> "Service Disconnected\nTap to Reconnect"
        isPlaying -> {
            val rawPackage = lyricInfo?.sourceApp ?: metadata?.packageName
            val sourceName = if (rawPackage != null) {
                ParserRuleHelper.getAppNameForPackage(context, rawPackage)
            } else "Music"
            "Active: $sourceName"
        }
        else -> "Service Ready (Idle)"
    }

    val statusActive = listenerEnabled && serviceConnected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // ── Header: Icon + Title ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            // Load adaptive icon as bitmap (painterResource doesn't support adaptive icons)
            val appIconBitmap = remember {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val bitmap = android.graphics.Bitmap.createBitmap(
                    128, 128, android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, 128, 128)
                drawable.draw(canvas)
                bitmap
            }
            Image(
                bitmap = appIconBitmap.asImageBitmap(),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Status Card ──
        StatusCard(
            statusText = statusText,
            versionText = versionText,
            isActive = statusActive,
            onTap = if (!serviceConnected && listenerEnabled) onStatusCardTap else null
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Now Playing Card ──
        NowPlayingCard(
            isPlaying = isPlaying,
            songTitle = metadata?.title,
            artist = metadata?.artist,
            lyric = lyricInfo?.lyric,
            albumArt = albumArt,
            progressPosition = progress?.position ?: 0L,
            progressDuration = progress?.duration ?: 0L,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── API Status Card (Debug only) ──
        if (showApiCard) {
            ApiStatusCard(
                permissionText = apiPermissionText,
                capabilityText = apiCapabilityText,
                flagText = apiFlagText,
                permissionActive = apiPermissionActive,
                capabilityActive = apiCapabilityActive,
                flagActive = apiFlagActive,
                onOpenSettings = onOpenPromotedSettings,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Settings Button ──
        NavigationButton(
            text = stringResource(R.string.menu_settings),
            iconRes = R.drawable.ic_settings,
            onClick = onOpenSettings
        )

        // ── Debug Button (Debug builds only) ──
        if (isDebugBuild) {
            Spacer(modifier = Modifier.height(8.dp))
            NavigationButton(
                text = "Debug Center",
                iconRes = R.drawable.ic_sync,
                onClick = onOpenDebug
            )
        }
    }
}

// ── Status Card ──
@Composable
private fun StatusCard(
    statusText: String,
    versionText: String,
    isActive: Boolean,
    onTap: (() -> Unit)?,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) StatusActive else StatusInactive
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(24.dp)
        ) {
            // Icon Container
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceDim,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (isActive) R.drawable.ic_check_circle else R.drawable.ic_cancel
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = statusText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = versionText,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ── Now Playing Card ──
@Composable
private fun NowPlayingCard(
    isPlaying: Boolean,
    songTitle: String?,
    artist: String?,
    lyric: String?,
    albumArt: Bitmap?,
    progressPosition: Long,
    progressDuration: Long,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Idle state (centered)
            AnimatedVisibility(
                visible = !isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 120.dp)
                ) {
                    Text(
                        text = stringResource(R.string.status_idle_waiting),
                        fontSize = 18.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Playing state
            AnimatedVisibility(
                visible = isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    // Album Art + Metadata row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Album Art
                        if (albumArt != null) {
                            Image(
                                bitmap = albumArt.asImageBitmap(),
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            // Placeholder
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceDim,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Song title + Artist (vertically stacked)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = songTitle ?: "-",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = artist ?: "-",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Lyric label
                    Text(
                        text = stringResource(R.string.label_lyric_prefix),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Lyric text
                    Text(
                        text = lyric ?: "-",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar
                    if (progressDuration > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (progressPosition.toFloat() / progressDuration.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceDim,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceDim,
                        )
                    }
                }
            }
        }
    }
}

// ── API Status Card (Debug Only) ──
@Composable
private fun ApiStatusCard(
    permissionText: String,
    capabilityText: String,
    flagText: String,
    permissionActive: Boolean,
    capabilityActive: Boolean,
    flagActive: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.main_system_api_status),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = permissionText,
                color = if (permissionActive) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = capabilityText,
                color = if (capabilityActive) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = flagText,
                color = if (flagActive) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text(text = stringResource(R.string.main_open_settings))
            }
        }
    }
}

// ── Navigation Button ──
@Composable
private fun NavigationButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = text,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
