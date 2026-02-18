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
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.islandlyrics.ParserRuleHelper

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.vector.ImageVector

// Status colors matching colors.xml
private val StatusActive = Color(0xFF4CAF50)
private val StatusInactive = Color(0xFFF44336)

@Composable
fun MainScreen(
    versionText: String,
    isDebugBuild: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenWhitelist: () -> Unit,
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
    // Observe LiveData as Compose State
    val repoPlaying by repo.isPlaying.observeAsState(false)
    val repoMetadata by repo.liveMetadata.observeAsState()
    val repoLyric by repo.liveLyric.observeAsState()
    val repoAlbumArt by repo.liveAlbumArt.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()

    // ── Session Management ──
    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }
    
    // Listen for active sessions
    DisposableEffect(Unit) {
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = android.content.ComponentName(context, MediaMonitorService::class.java)

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers ?: emptyList()
        }

        try {
            // Initial load
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            // Register listener
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (e: Exception) {
            // Permission might not be granted yet, handled by UI state
        }

        onDispose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (e: Exception) {}
        }
    }

    // Filter and Sort: Ensure Playing sessions are prioritized (at the start)
    val whitelistedSessions = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers
            .filter { enabledPackages.contains(it.packageName) }
            .sortedByDescending { 
                val state = it.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            }
    }
    
    // Determine overall status based on actual sessions
    val hasActiveSession = whitelistedSessions.isNotEmpty()

    // Derive status card state
    val listenerEnabled = remember(Unit) {
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        listeners?.contains(context.packageName) == true
    }

    val serviceConnected = MediaMonitorService.isConnected

    val statusText = when {
        !listenerEnabled -> "Permission Required"
        hasActiveSession -> {
            // Show status of the PRIMARY (playing) session if possible, else the first one
            // We can trust Repo for the "Active" one
            // Only consider it "Active" if Repo says so OR if we have valid metadata
            if (repoPlaying || repoMetadata != null) {
                 val rawPackage = repoLyric?.sourceApp ?: repoMetadata?.packageName ?: whitelistedSessions.firstOrNull()?.packageName
                 val sourceName = if (rawPackage != null) {
                    ParserRuleHelper.getAppNameForPackage(context, rawPackage)
                } else "Music"
                "Active: $sourceName"
            } else {
                // If nothing playing but sessions exist
                "Ready: ${whitelistedSessions.size} Session(s)"
            }
        }
        !serviceConnected -> "Service Disconnected\nTap to Reconnect"
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

        // ── Now Playing / Session Pager ──
        if (whitelistedSessions.isEmpty()) {
            // Idle State
            IdleCard()
        } else {
             val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { whitelistedSessions.size })
             
             Column {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 16.dp,
                    contentPadding = PaddingValues(horizontal = 0.dp) // Card handles padding
                ) { page ->
                    val controller = whitelistedSessions[page]
                    
                    // Check if this is the PRIMARY session (matching Repo)
                    val isPrimary = controller.packageName == repoMetadata?.packageName
                    
                    // Use Repo data if primary, else extract from controller
                    MediaSessionCard(
                        controller = controller,
                        isPrimary = isPrimary,
                        // If primary, inject repo data. If not, pass null to let card extract from controller.
                        primaryMetadata = if (isPrimary) repoMetadata else null,
                        primaryLyric = if (isPrimary) repoLyric?.lyric else null,
                        primaryAlbumArt = if (isPrimary) repoAlbumArt else null,
                        primaryProgress = if (isPrimary) repoProgress else null
                    )
                }
                
                // Pager Indicator
                if (whitelistedSessions.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(whitelistedSessions.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                }
             }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Action Buttons ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Personalization & Whitelist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ActionButton(
                    text = stringResource(R.string.page_title_personalization),
                    icon = Icons.Filled.Palette,
                    onClick = onOpenPersonalization,
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    text = stringResource(R.string.title_parser_whitelist_manager),
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    onClick = onOpenWhitelist,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: App Settings & Debug
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                 ActionButton(
                    text = stringResource(R.string.title_app_settings),
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )

                if (isDebugBuild) {
                    ActionButton(
                        text = "Debug",
                        icon = Icons.Filled.BugReport,
                        onClick = onOpenDebug,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
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
// ── Idle Card ──
@Composable
private fun IdleCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
         Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 160.dp) // Match height roughly
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                )
                Text(
                    text = stringResource(R.string.status_idle_waiting),
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Media Session Card (Replaces NowPlayingCard) ──
@Composable
private fun MediaSessionCard(
    controller: MediaController,
    isPrimary: Boolean,
    primaryMetadata: LyricRepository.MediaInfo?,
    primaryLyric: String?,
    primaryAlbumArt: Bitmap?,
    primaryProgress: LyricRepository.PlaybackProgress?
) {
    // If not primary, we need to observe the controller directly
    var localMetadata by remember(controller) { mutableStateOf(controller.metadata) }
    var localPlaybackState by remember(controller) { mutableStateOf(controller.playbackState) }

    if (!isPrimary) {
        DisposableEffect(controller) {
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) { 
                    localPlaybackState = state 
                }
                override fun onMetadataChanged(meta: MediaMetadata?) { localMetadata = meta }
            }
            controller.registerCallback(callback)
            onDispose { controller.unregisterCallback(callback) }
        }
    }
    
    // Explicitly observe primary state if isPrimary to ensure UI updates
    // Compose state `repoPlaying` is already observed in parent, but `isPlaying` here calculates from `localPlaybackState`
    // We need to ensure `localPlaybackState` is correct for primary too if we use it, 
    // OR just rely entirely on `repoPlaying` for primary.
    // The previous logic `val isPlaying = if (isPrimary) localPlaybackState?.state ...` 
    // used `localPlaybackState` which wasn't updated for Primary because we didn't register the callback!
    // FIX: For primary, use the `repoPlaying` passed down (we need to pass it) OR register callback for primary too.
    // Better: Register callback for ALL controllers to get immediate transport state updates.
    
    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { 
                localPlaybackState = state 
            }
             override fun onMetadataChanged(meta: MediaMetadata?) { 
                 localMetadata = meta 
             }
        }
        controller.registerCallback(callback)
        onDispose { controller.unregisterCallback(callback) }
    }

    // Resolve Data
    val title = if (isPrimary) primaryMetadata?.title else localMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = if (isPrimary) primaryMetadata?.artist else localMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    // Album art for non-primary is tricky because loading Bitmap from Metadata object in Composables can be slow/main-thread heavy?
    // But `getBitmap` returns what's in memory.
    val albumArt = if (isPrimary) primaryAlbumArt else (localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ART))
    
    val lyric = if (isPrimary) primaryLyric else null // Non-primary has no lyrics
    
    // Progress
    // Only primary has live progress from repo (which might be polled or event based).
    // For non-primary, we might not have real-time progress unless we poll.
    // Let's just use Repo progress for primary, and 0 for others to keep it simple, or static.
    val duration = if (isPrimary) primaryMetadata?.duration ?: 0L else localMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val position = if (isPrimary) primaryProgress?.position ?: 0L else localPlaybackState?.position ?: 0L

    val isPlaying = localPlaybackState?.state == PlaybackState.STATE_PLAYING || localPlaybackState?.state == PlaybackState.STATE_BUFFERING

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Top Row: Album Art + Song Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Album Art
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
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceDim,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Song title + Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title ?: "Unknown Title",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist ?: "Unknown Artist",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                     Spacer(modifier = Modifier.height(4.dp))
                     // App Name (Context)
                     val context = LocalContext.current
                     val appName = remember(controller.packageName) { ParserRuleHelper.getAppNameForPackage(context, controller.packageName) }
                     Text(
                        text = appName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary, // Highlight app name
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lyric Section (Fixed Height for consistency)
            // Even if no lyric, we reserve space for 3 lines (Label + 2 lines text)
            // Label: 14sp + 8dp padding
            // Text: 2 lines * 24sp line height = 48sp
            // Total approx 80-90dp
            
            Column(modifier = Modifier.height(90.dp)) {
                if (lyric != null) {
                    Text(
                        text = "Lyric:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Lyric text
                    Text(
                        text = lyric,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF64B5F6),
                        lineHeight = 24.sp
                    )
                } else if (isPrimary) {
                     // Primary but no lyrics yet
                     Text(
                        text = "Lyric:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                     Spacer(modifier = Modifier.height(8.dp))
                     Text(
                        text = "Waiting for lyrics...",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                } else {
                    // Non-primary
                     Text(
                        text = "Lyric:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                     Spacer(modifier = Modifier.height(8.dp))
                     Text(
                        text = "Lyrics unavailable",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress bar (Interactive Slider)
            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableFloatStateOf(0f) }

            val currentProgress = if (isDragging) {
                dragProgress
            } else {
                if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            }

            Slider(
                value = currentProgress,
                onValueChange = { 
                    isDragging = true
                    dragProgress = it
                },
                onValueChangeFinished = {
                    if (duration > 0) {
                         controller.transportControls.seekTo((dragProgress * duration).toLong())
                    }
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF64B5F6),
                    activeTrackColor = Color(0xFF64B5F6),
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp) // Slightly reduce touch target height to fit better if needed, but standard is fine
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // ── Media Controls ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                IconButton(onClick = { controller.transportControls.skipToPrevious() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause
                IconButton(
                    onClick = {
                        if (isPlaying) controller.transportControls.pause() else controller.transportControls.play()
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next
                IconButton(onClick = { controller.transportControls.skipToNext() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
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
    icon: ImageVector,
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
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
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
