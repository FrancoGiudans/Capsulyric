package com.example.islandlyrics.feature.main.material

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.islandlyrics.R
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.service.MediaMonitorService
import kotlinx.coroutines.delay
import kotlin.math.abs

private val StatusActive = Color(0xFF2E7D32)
private val StatusInactive = Color(0xFFC62828)
private val LyricAccent = Color(0xFF3D7DFF)
private val LightBackgroundTop = Color(0xFFF7F8FC)
private val LightBackgroundBottom = Color(0xFFEDEFF6)
private val DarkBackgroundTop = Color(0xFF101114)
private val DarkBackgroundBottom = Color(0xFF17191E)
private const val SeekSyncThresholdMs = 250L
private const val SeekFallbackTimeoutMs = 4000L

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
    contentPadding: PaddingValues = PaddingValues(),
) {
    val repo = remember { LyricRepository.getInstance() }
    val context = LocalContext.current

    val repoPlaying by repo.isPlaying.observeAsState(false)
    val repoMetadata by repo.liveMetadata.observeAsState()
    val repoLyric by repo.liveLyric.observeAsState()
    val repoAlbumArt by repo.liveAlbumArt.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()
    val repoParsedLyrics by repo.liveParsedLyrics.observeAsState()
    val repoCurrentLine by repo.liveCurrentLine.observeAsState()

    var activeControllers by remember { mutableStateOf<List<MediaController>>(emptyList()) }

    DisposableEffect(Unit) {
        val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = android.content.ComponentName(context, MediaMonitorService::class.java)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            activeControllers = controllers ?: emptyList()
        }
        try {
            activeControllers = mediaSessionManager.getActiveSessions(componentName)
            mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (_: Exception) {
            }
        }
    }

    val whitelistedSessions = remember(activeControllers) {
        val enabledPackages = ParserRuleHelper.getEnabledPackages(context)
        activeControllers
            .filter { enabledPackages.contains(it.packageName) }
            .sortedByDescending {
                val state = it.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            }
    }

    val listenerEnabled = remember(Unit) {
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        listeners?.contains(context.packageName) == true
    }
    val serviceConnected = MediaMonitorService.isConnected
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val statusText = when {
        !listenerEnabled -> stringResource(R.string.main_status_permission_required)
        whitelistedSessions.isNotEmpty() -> {
            if (repoPlaying || repoMetadata != null) {
                val rawPackage = repoLyric?.sourceApp ?: repoMetadata?.packageName ?: whitelistedSessions.firstOrNull()?.packageName
                val sourceName = rawPackage?.let { ParserRuleHelper.getAppNameForPackage(context, it) }
                    ?: stringResource(R.string.main_music_app_fallback)
                stringResource(R.string.main_status_connecting_fmt, sourceName)
            } else {
                stringResource(R.string.main_status_session_count_fmt, whitelistedSessions.size)
            }
        }
        !serviceConnected -> stringResource(R.string.main_status_service_disconnected)
        else -> stringResource(R.string.main_status_service_ready)
    }

        Column(
            modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isDarkTheme) DarkBackgroundTop else LightBackgroundTop,
                        if (isDarkTheme) DarkBackgroundBottom else LightBackgroundBottom
                    )
                )
            )
            .padding(contentPadding)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        CompactHeader(
            versionText = versionText,
            isDebugBuild = isDebugBuild,
            onOpenDebug = onOpenDebug
        )

        Spacer(modifier = Modifier.height(12.dp))
        StatusPill(
            statusText = statusText,
            isActive = listenerEnabled && serviceConnected,
            onTap = if (!serviceConnected && listenerEnabled) onStatusCardTap else null
        )

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel(
            title = stringResource(R.string.main_now_playing_title),
            subtitle = if (whitelistedSessions.isEmpty()) {
                stringResource(R.string.main_now_playing_subtitle_idle)
            } else {
                stringResource(R.string.main_now_playing_subtitle_active)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        if (whitelistedSessions.isEmpty()) {
            IdleCard()
        } else {
            val pagerState = rememberPagerState(pageCount = { whitelistedSessions.size })
            var maxCardHeightPx by remember(whitelistedSessions.size) { mutableIntStateOf(0) }
            val minCardHeight = with(LocalDensity.current) {
                if (maxCardHeightPx > 0) maxCardHeightPx.toDp() else 0.dp
            }
            Column {
                HorizontalPager(state = pagerState, pageSpacing = 14.dp) { page ->
                    val controller = whitelistedSessions[page]
                    val isPrimary = isPrimarySession(controller, repoMetadata)
                    MediaSessionCard(
                        controller = controller,
                        isPrimary = isPrimary,
                        primaryMetadata = if (isPrimary) repoMetadata else null,
                        primaryLyric = if (isPrimary) {
                            repoCurrentLine?.text ?: repoLyric?.lyric?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        },
                        primaryLyricSource = if (isPrimary) formatPrimaryLyricSource(repoLyric?.apiPath, repoParsedLyrics?.sourceLabel) else null,
                        primaryAlbumArt = if (isPrimary) repoAlbumArt else null,
                        primaryProgress = if (isPrimary) repoProgress else null,
                        primaryIsPlaying = if (isPrimary) repoPlaying else null,
                        minCardHeight = minCardHeight,
                        onHeightMeasured = { measuredHeight ->
                            if (measuredHeight > maxCardHeightPx) {
                                maxCardHeightPx = measuredHeight
                            }
                        }
                    )
                }
                if (whitelistedSessions.size > 1) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(whitelistedSessions.size) { index ->
                            val active = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(8.dp)
                                    .width(if (active) 28.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun CompactHeader(
    versionText: String,
    isDebugBuild: Boolean,
    onOpenDebug: () -> Unit,
) {
    val context = LocalContext.current
    val appIconBitmap = remember {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 128, 128)
        drawable.draw(canvas)
        bitmap
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = appIconBitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.main_app_icon_cd),
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (isDebugBuild) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.clickable { onOpenDebug() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bug_report),
                    contentDescription = stringResource(R.string.main_debug_center_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp).size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    statusText: String,
    isActive: Boolean,
    onTap: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = if (isActive) StatusActive else StatusInactive)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionLabel(
    title: String,
    subtitle: String,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IdleCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.status_idle_waiting), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.main_idle_card_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaSessionCard(
    controller: MediaController,
    isPrimary: Boolean,
    primaryMetadata: LyricRepository.MediaInfo?,
    primaryLyric: String?,
    primaryLyricSource: String?,
    primaryAlbumArt: Bitmap?,
    primaryProgress: LyricRepository.PlaybackProgress?,
    primaryIsPlaying: Boolean?,
    minCardHeight: androidx.compose.ui.unit.Dp,
    onHeightMeasured: (Int) -> Unit
) {
    var localMetadata by remember(controller) { mutableStateOf(controller.metadata) }
    var localPlaybackState by remember(controller) { mutableStateOf(controller.playbackState) }

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

    val title = if (isPrimary) {
        primaryMetadata?.title ?: localMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    } else {
        localMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    }
    val artist = if (isPrimary) {
        primaryMetadata?.artist ?: localMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    } else {
        localMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    }
    val albumArt = if (isPrimary) {
        primaryAlbumArt ?: localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    } else {
        localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: localMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }
    val lyric = if (isPrimary) primaryLyric?.takeIf { it.isNotBlank() } else null
    val duration = if (isPrimary) {
        primaryProgress?.duration ?: primaryMetadata?.duration
        ?: localMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    } else {
        localMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }
    val position = if (isPrimary) {
        primaryProgress?.position ?: calculatePlaybackPosition(localPlaybackState, duration)
    } else {
        calculatePlaybackPosition(localPlaybackState, duration)
    }
    val isPlaying = if (isPrimary) {
        primaryIsPlaying ?: isPlaybackActive(localPlaybackState)
    } else {
        isPlaybackActive(localPlaybackState)
    }
    val sessionStateKey = buildString {
        append(controller.packageName)
        append('|')
        append(title.orEmpty())
        append('|')
        append(artist.orEmpty())
    }
    var pendingSeekPosition by remember(sessionStateKey) { mutableLongStateOf(-1L) }
    var pendingSeekStartedAt by remember(sessionStateKey) { mutableLongStateOf(0L) }
    var seekOriginPosition by remember(sessionStateKey) { mutableLongStateOf(-1L) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minCardHeight)
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(20.dp)
        ) {
            val context = LocalContext.current
            val appName = remember(controller.packageName) {
                ParserRuleHelper.getAppNameForPackage(context, controller.packageName)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = stringResource(R.string.main_album_art_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)) {
                        Text(
                            text = appName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = title ?: stringResource(R.string.media_control_unknown_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist ?: stringResource(R.string.media_control_unknown_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPrimary && !primaryLyricSource.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.main_lyric_source_fmt, primaryLyricSource),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            LyricPanel(lyric = lyric, isPrimary = isPrimary)
            Spacer(modifier = Modifier.height(18.dp))

            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(position, pendingSeekPosition, pendingSeekStartedAt, seekOriginPosition, isPlaying) {
                if (pendingSeekPosition < 0L) return@LaunchedEffect
                val expectedPendingPosition = if (isPlaying && pendingSeekStartedAt > 0L) {
                    (pendingSeekPosition + (SystemClock.elapsedRealtime() - pendingSeekStartedAt)).coerceAtMost(duration)
                } else {
                    pendingSeekPosition
                }
                val hasActuallyMovedFromOrigin = seekOriginPosition < 0L ||
                    abs(position - seekOriginPosition) > SeekSyncThresholdMs
                val isNearSeekTarget = abs(position - expectedPendingPosition) <= SeekSyncThresholdMs

                if (hasActuallyMovedFromOrigin && isNearSeekTarget) {
                    pendingSeekPosition = -1L
                    pendingSeekStartedAt = 0L
                    seekOriginPosition = -1L
                    return@LaunchedEffect
                }
                if (pendingSeekStartedAt <= 0L) return@LaunchedEffect
                val remaining = SeekFallbackTimeoutMs - (SystemClock.elapsedRealtime() - pendingSeekStartedAt)
                if (remaining > 0L) {
                    delay(remaining)
                }
                if (pendingSeekPosition >= 0L && !isPlaying) {
                    pendingSeekPosition = -1L
                    pendingSeekStartedAt = 0L
                    seekOriginPosition = -1L
                }
            }
            val effectivePosition = if (pendingSeekPosition >= 0L) {
                if (isPlaying && pendingSeekStartedAt > 0L) {
                    (pendingSeekPosition + (SystemClock.elapsedRealtime() - pendingSeekStartedAt)).coerceAtMost(duration)
                } else {
                    pendingSeekPosition
                }
            } else {
                position
            }
            val currentProgress = if (isDragging) {
                dragProgress
            } else {
                if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            }

            Slider(
                value = currentProgress,
                onValueChange = {
                    isDragging = true
                    dragProgress = it
                },
                onValueChangeFinished = {
                    if (duration > 0) {
                        seekOriginPosition = position
                        pendingSeekPosition = (dragProgress * duration).toLong()
                        pendingSeekStartedAt = SystemClock.elapsedRealtime()
                        controller.transportControls.seekTo(pendingSeekPosition)
                    }
                    isDragging = false
                },
                enabled = duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = LyricAccent,
                    activeTrackColor = LyricAccent,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundTransportButton(R.drawable.ic_skip_previous, stringResource(R.string.action_previous)) { controller.transportControls.skipToPrevious() }
                FilledTonalButton(
                    onClick = { if (isPlaying) controller.transportControls.pause() else controller.transportControls.play() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.action_pause)
                        } else {
                            stringResource(R.string.action_play)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                RoundTransportButton(R.drawable.ic_skip_next, stringResource(R.string.action_next)) { controller.transportControls.skipToNext() }
            }
        }
    }
}

@Composable
private fun LyricPanel(
    lyric: String?,
    isPrimary: Boolean,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clickable(enabled = !lyric.isNullOrBlank()) {
                lyric?.let {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Lyric", it))
                    Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = LyricAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.main_current_lyric_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            when {
                !lyric.isNullOrBlank() -> {
                    Text(
                        text = lyric,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        color = LyricAccent,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.tap_to_copy_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                isPrimary -> {
                    Text(
                        text = stringResource(R.string.main_waiting_for_lyrics),
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.main_lyrics_unavailable),
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundTransportButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, shape = CircleShape, contentPadding = PaddingValues(14.dp)) {
        Icon(painter = painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

private fun formatPrimaryLyricSource(apiPath: String?, sourceLabel: String?): String? {
    val label = sourceLabel?.trim().orEmpty()
    if (label.isBlank()) return null
    return when (apiPath) {
        "Online API" -> "$label [在线]"
        "Online Cache" -> "$label [缓存]"
        else -> null
    }
}

private fun isPrimarySession(
    controller: MediaController,
    primaryMetadata: LyricRepository.MediaInfo?
): Boolean {
    if (primaryMetadata == null) return false
    if (controller.packageName != primaryMetadata.packageName) return false
    val controllerTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val controllerArtist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    return when {
        controllerTitle == null && controllerArtist == null -> true
        else -> controllerTitle == primaryMetadata.rawTitle && controllerArtist == primaryMetadata.rawArtist
    }
}

private fun isPlaybackActive(state: PlaybackState?): Boolean {
    val currentState = state?.state
    return currentState == PlaybackState.STATE_PLAYING || currentState == PlaybackState.STATE_BUFFERING
}

private fun calculatePlaybackPosition(
    state: PlaybackState?,
    duration: Long
): Long {
    if (state == null) return 0L
    var currentPos = state.position
    if (state.state == PlaybackState.STATE_PLAYING) {
        val timeDelta = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        currentPos += (timeDelta * state.playbackSpeed).toLong()
    }
    if (duration > 0 && currentPos > duration) currentPos = duration
    return currentPos.coerceAtLeast(0L)
}
