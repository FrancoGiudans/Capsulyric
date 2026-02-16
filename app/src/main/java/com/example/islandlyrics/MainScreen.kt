package com.example.islandlyrics

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Status Pill ──
        StatusPill(
            statusText = statusText,
            isActive = statusActive,
            onTap = if (!serviceConnected && listenerEnabled) onStatusCardTap else null
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        // ── Action Buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Settings Button
            ActionButton(
                text = stringResource(R.string.menu_settings),
                iconRes = R.drawable.ic_settings,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )

            // Debug Button (Always visible logic in UI, but content depends on isDebugBuild?)
            // The prompt implies "Debug" button is always there in the reference image.
            // But we should probably keep the isDebugBuild check for functional safety or just show it.
            // The user request "Bottom Action Buttons (Settings, Debug)" suggests showing it.
            // Let's assume for now we show it if isDebugBuild is true, or maybe we want to make it always visible?
            // The original code hid it. The design shows it. I'll respect the boolean for now but lay it out.
            if (isDebugBuild) {
                ActionButton(
                    text = "Debug",
                    iconRes = R.drawable.ic_bug_report,
                    onClick = onOpenDebug,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Status Pill ──
@Composable
private fun StatusPill(
    statusText: String,
    isActive: Boolean,
    onTap: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(50), // Pill shape
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Indicator Dot
            Canvas(modifier = Modifier.size(12.dp)) {
                drawCircle(color = if (isActive) StatusActive else StatusInactive)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = statusText,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Now Playing Card ──
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
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Idle state (centered)
        if (!isPlaying) {
             Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.status_idle_waiting),
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Playing state
            Column(modifier = Modifier.padding(20.dp)) {
                // Top Row: Album Art + Song Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Album Art - Large rounded square
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        // Placeholder
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceDim,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Song title + Artist
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle ?: "-",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = artist ?: "-",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Lyric label
                Text(
                    text = "Lyric:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Lyric text - Prominent Blue
                Text(
                    text = lyric ?: "-",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal, // Based on image looks normal weight but blue and distinct
                    minLines = 2,
                    color = Color(0xFF64B5F6), // Light Blue for lyrics
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Progress bar - Blue
                if (progressDuration > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (progressPosition.toFloat() / progressDuration.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF64B5F6), // Match lyrics color
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}



// ── Action Button ──
@Composable
private fun ActionButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier
            .height(64.dp)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
