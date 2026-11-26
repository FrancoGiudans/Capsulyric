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
 *
 *
 */

package com.example.islandlyrics.feature.main.material

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.widget.Toast
import com.example.islandlyrics.core.network.OfflineModeManager
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.islandlyrics.R
import com.example.islandlyrics.ui.adaptive.AdaptiveTwoPane
import com.example.islandlyrics.ui.adaptive.AdaptiveWidthItem
import com.example.islandlyrics.ui.adaptive.LocalAppWindowLayout
import com.example.islandlyrics.ui.adaptive.WindowSizeTier
import com.example.islandlyrics.ui.adaptive.adaptiveContentPadding
import kotlinx.coroutines.launch
import com.example.islandlyrics.runtime.media.openMediaApp
import com.example.islandlyrics.lyrics.cache.OnlineLyricCacheStore
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.runtime.service.MediaMonitorService
import com.example.islandlyrics.ui.theme.material.materialPageContainerColor
import com.example.islandlyrics.ui.material.blur.MaterialBlurScaffold
import com.example.islandlyrics.ui.theme.material.MaterialBlurTopAppBar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.example.islandlyrics.core.theme.generatePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.feature.main.HomeLyricPreviewDisplay
import com.example.islandlyrics.feature.main.rememberHomeMediaCards

private val StatusActive = Color(0xFF2E7D32)
private val StatusInactive = Color(0xFFC62828)
private const val SeekSyncThresholdMs = 250L
private const val SeekFallbackTimeoutMs = 4000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun MainScreen(
    versionText: String,
    isDebugBuild: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenPromotedSettings: () -> Unit,
    onStatusCardTap: () -> Unit,
    onOpenOnlineLyricRematch: () -> Unit,
    extraBottomPadding: Dp = 0.dp,
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
    val repoLyricResolveState by repo.liveLyricResolveState.observeAsState(LyricRepository.LyricResolveState.PENDING)

    val prefs = remember { AppPreferences.of(context) }
    val onlineLyricCacheStore = remember(context) { OnlineLyricCacheStore(context) }
    var homeLyricPreviewDisplayModes by remember {
        mutableStateOf(HomeLyricPreviewDisplay.read(prefs))
    }
    val repoNextLine = remember(repoParsedLyrics, repoCurrentLine) {
        val lines = repoParsedLyrics?.lines
        if (lines.isNullOrEmpty() || repoCurrentLine == null) {
            null
        } else {
            val index = lines.indexOfFirst {
                it === repoCurrentLine || it.startTime == repoCurrentLine?.startTime
            }
            if (index >= 0 && index + 1 < lines.size) lines[index + 1] else null
        }
    }
    var isCurrentTrackInstrumental by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferences.Keys.HOME_LYRIC_PREVIEW_SECONDARY_MODES) {
                homeLyricPreviewDisplayModes = HomeLyricPreviewDisplay.read(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(
        repoMetadata?.packageName,
        repoMetadata?.title,
        repoMetadata?.artist,
        repoMetadata?.rawTitle,
        repoMetadata?.rawArtist,
        repoLyric?.apiPath,
        repoLyric?.lyric
    ) {
        val mediaInfo = repoMetadata
        isCurrentTrackInstrumental = if (mediaInfo == null) {
            false
        } else {
            withContext(Dispatchers.IO) {
                val rule = ParserRuleHelper.getRuleForPackage(context, mediaInfo.packageName)
                    ?: ParserRuleHelper.createDefaultRule(mediaInfo.packageName)
                onlineLyricCacheStore.getCurrentSongState(
                    mediaInfo = mediaInfo,
                    fallbackTitle = mediaInfo.title,
                    fallbackArtist = mediaInfo.artist,
                    useRawMetadata = rule.useRawMetadataForOnlineMatching
                ).isInstrumental
            }
        }
    }

    val mediaCards = rememberHomeMediaCards(repoMetadata)
    val whitelistedSessions = mediaCards.controllers

    val listenerEnabled = remember(Unit) {
        val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        listeners?.contains(context.packageName) == true
    }
    val serviceConnectionState by MediaMonitorService.connectionStateFlow.collectAsState()
    val serviceConnected = serviceConnectionState == MediaMonitorService.ConnectionState.CONNECTED
    val serviceConnecting = serviceConnectionState == MediaMonitorService.ConnectionState.CONNECTING
    val statusText = when {
        !listenerEnabled -> stringResource(R.string.main_status_permission_required)
        serviceConnecting -> stringResource(R.string.main_status_connecting)
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


    val layoutInfo = LocalAppWindowLayout.current
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val useTwoPane = layoutInfo.tier == WindowSizeTier.CompactLandscape ||
        layoutInfo.tier == WindowSizeTier.Expanded ||
        layoutInfo.tier == WindowSizeTier.ExtraLarge
    val compactLandscape = layoutInfo.tier == WindowSizeTier.CompactLandscape
    val sessionPagerState = rememberPagerState(pageCount = { whitelistedSessions.size.coerceAtLeast(1) })
    val subtitleText = if (whitelistedSessions.isEmpty()) {
        stringResource(R.string.main_now_playing_subtitle_idle)
    } else {
        stringResource(R.string.main_now_playing_subtitle_active)
    }
    val showOnlineRematch = !OfflineModeManager.isEnabled(context) &&
        (isCurrentTrackInstrumental ||
            isOnlineLyricSource(repoLyric?.apiPath) ||
            isOnlineLyricSource(repoParsedLyrics?.apiPath) ||
            (repoLyric?.lyric.isNullOrBlank() && repoParsedLyrics?.lines.isNullOrEmpty()))
    val primaryLyricText = HomeLyricPreviewDisplay.previewText(
        modes = homeLyricPreviewDisplayModes,
        currentLine = repoCurrentLine,
        lyricInfo = repoLyric,
        nextLine = repoNextLine
    )
    val primaryLyricSourceText = run {
        val activeParsed = repoParsedLyrics?.takeIf { it.lines.isNotEmpty() }
        formatPrimaryLyricSource(
            apiPath = activeParsed?.apiPath ?: repoLyric?.apiPath,
            sourceLabel = activeParsed?.sourceLabel,
            sourceApp = repoLyric?.sourceApp
        )
    }

    MaterialBlurScaffold(
        containerColor = materialPageContainerColor(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MaterialBlurTopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_name))
                },
                actions = {
                    if (isDebugBuild) {
                        IconButton(onClick = onOpenDebug) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bug_report),
                                contentDescription = stringResource(R.string.main_debug_center_cd)
                            )
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        if (useTwoPane) {
            AdaptiveTwoPane(
                startWeight = if (compactLandscape) 0.36f else 0.38f,
                spacing = 16.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 20.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 20.dp
                    ),
                startPane = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                top = innerPadding.calculateTopPadding() + 12.dp,
                                bottom = innerPadding.calculateBottomPadding() + 16.dp + extraBottomPadding
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusPill(
                            statusText = statusText,
                            isActive = listenerEnabled && serviceConnected,
                            onTap = if (!serviceConnected && listenerEnabled) onStatusCardTap else null
                        )
                        SectionLabel(
                            title = stringResource(R.string.main_now_playing_title),
                            subtitle = subtitleText
                        )
                        if (whitelistedSessions.isNotEmpty()) {
                            MaterialHomeSessionSelectorCard(
                                sessions = whitelistedSessions,
                                primaryToken = mediaCards.primaryToken,
                                selectedIndex = sessionPagerState.currentPage.coerceIn(0, whitelistedSessions.lastIndex),
                                onSelectSession = { index ->
                                    coroutineScope.launch {
                                        sessionPagerState.animateScrollToPage(index)
                                    }
                                }
                            )
                        }
                    }
                },
                endPane = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                top = innerPadding.calculateTopPadding() + 12.dp,
                                bottom = innerPadding.calculateBottomPadding() + 16.dp + extraBottomPadding
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = if (compactLandscape) 720.dp else 680.dp)
                        ) {
                            if (whitelistedSessions.isEmpty()) {
                                IdleCard()
                            } else {
                                MaterialSessionPagerSection(
                                    sessions = whitelistedSessions,
                                    pagerState = sessionPagerState,
                                    compactLandscape = compactLandscape,
                                    primaryToken = mediaCards.primaryToken,
                                    primaryMetadata = repoMetadata,
                                    primaryLyricText = primaryLyricText,
                                    primaryLyricSource = primaryLyricSourceText,
                                    primaryIsInstrumental = isCurrentTrackInstrumental,
                                    primaryHasTimeline = repoParsedLyrics?.lines?.isNotEmpty() == true,
                                    primaryLyricResolveState = repoLyricResolveState,
                                    primaryAlbumArt = repoAlbumArt,
                                    primaryProgress = repoProgress,
                                    primaryIsPlaying = repoPlaying,
                                    showOnlineRematch = showOnlineRematch,
                                    onOpenOnlineLyricRematch = onOpenOnlineLyricRematch
                                )
                            }
                        }
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding.adaptiveContentPadding(
                    layoutDirection = layoutDirection,
                    extraTop = 16.dp,
                    extraBottom = 16.dp + extraBottomPadding,
                    extraStart = 20.dp,
                    extraEnd = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AdaptiveWidthItem {
                        StatusPill(
                            statusText = statusText,
                            isActive = listenerEnabled && serviceConnected,
                            onTap = if (!serviceConnected && listenerEnabled) onStatusCardTap else null
                        )
                    }
                }

                item {
                    AdaptiveWidthItem {
                        SectionLabel(
                            title = stringResource(R.string.main_now_playing_title),
                            subtitle = subtitleText
                        )
                    }
                }

                item {
                    AdaptiveWidthItem {
                        if (whitelistedSessions.isEmpty()) {
                            IdleCard()
                        } else {
                            MaterialSessionPagerSection(
                                sessions = whitelistedSessions,
                                pagerState = sessionPagerState,
                                compactLandscape = false,
                                primaryToken = mediaCards.primaryToken,
                                primaryMetadata = repoMetadata,
                                primaryLyricText = primaryLyricText,
                                primaryLyricSource = primaryLyricSourceText,
                                primaryIsInstrumental = isCurrentTrackInstrumental,
                                primaryHasTimeline = repoParsedLyrics?.lines?.isNotEmpty() == true,
                                primaryLyricResolveState = repoLyricResolveState,
                                primaryAlbumArt = repoAlbumArt,
                                primaryProgress = repoProgress,
                                primaryIsPlaying = repoPlaying,
                                showOnlineRematch = showOnlineRematch,
                                onOpenOnlineLyricRematch = onOpenOnlineLyricRematch
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialSessionPagerSection(
    sessions: List<MediaController>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    compactLandscape: Boolean,
    primaryToken: Any?,
    primaryMetadata: LyricRepository.MediaInfo?,
    primaryLyricText: String?,
    primaryLyricSource: String?,
    primaryIsInstrumental: Boolean,
    primaryHasTimeline: Boolean,
    primaryLyricResolveState: LyricRepository.LyricResolveState,
    primaryAlbumArt: Bitmap?,
    primaryProgress: LyricRepository.PlaybackProgress?,
    primaryIsPlaying: Boolean?,
    showOnlineRematch: Boolean,
    onOpenOnlineLyricRematch: () -> Unit,
) {
    var maxCardHeightPx by remember(sessions.size, compactLandscape) { mutableIntStateOf(0) }
    val minCardHeight = with(LocalDensity.current) {
        if (maxCardHeightPx > 0) maxCardHeightPx.toDp() else 0.dp
    }
    Column {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 14.dp,
            key = { sessions[it].sessionToken },
        ) { page ->
            val controller = sessions[page]
            val isPrimary = controller.sessionToken == primaryToken
            MediaSessionCard(
                controller = controller,
                isPrimary = isPrimary,
                primaryMetadata = if (isPrimary) primaryMetadata else null,
                primaryLyric = if (isPrimary) primaryLyricText else null,
                primaryLyricSource = if (isPrimary) primaryLyricSource else null,
                primaryIsInstrumental = isPrimary && primaryIsInstrumental,
                primaryHasTimeline = isPrimary && primaryHasTimeline,
                primaryLyricResolveState = primaryLyricResolveState,
                primaryAlbumArt = if (isPrimary) primaryAlbumArt else null,
                primaryProgress = if (isPrimary) primaryProgress else null,
                primaryIsPlaying = if (isPrimary) primaryIsPlaying else null,
                showOnlineLyricRematch = isPrimary && showOnlineRematch,
                onOpenOnlineLyricRematch = onOpenOnlineLyricRematch,
                minCardHeight = minCardHeight,
                onHeightMeasured = { measuredHeight ->
                    if (measuredHeight > maxCardHeightPx) {
                        maxCardHeightPx = measuredHeight
                    }
                },
                compactLandscape = compactLandscape,
            )
        }
        if (sessions.size > 1) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(sessions.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (active) 28.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialHomeSessionSelectorCard(
    sessions: List<MediaController>,
    primaryToken: Any?,
    selectedIndex: Int,
    onSelectSession: (Int) -> Unit,
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sessions.forEachIndexed { index, controller ->
                val selected = index == selectedIndex
                var localMetadata by remember(controller) { mutableStateOf(controller.metadata) }
                DisposableEffect(controller) {
                    val callback = object : MediaController.Callback() {
                        override fun onMetadataChanged(meta: MediaMetadata?) {
                            localMetadata = meta
                        }
                    }
                    controller.registerCallback(callback)
                    onDispose { controller.unregisterCallback(callback) }
                }
                val appName = remember(controller.packageName) {
                    ParserRuleHelper.getAppNameForPackage(context, controller.packageName)
                }
                val title = localMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val isPrimary = controller.sessionToken == primaryToken
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSession(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComposeCanvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(
                                color = if (isPrimary) StatusActive else Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title?.takeIf { it.isNotBlank() } ?: appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            ComposeCanvas(modifier = Modifier.size(10.dp)) {
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 180.dp)
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
    primaryIsInstrumental: Boolean,
    primaryHasTimeline: Boolean,
    primaryLyricResolveState: LyricRepository.LyricResolveState,
    primaryAlbumArt: Bitmap?,
    primaryProgress: LyricRepository.PlaybackProgress?,
    primaryIsPlaying: Boolean?,
    showOnlineLyricRematch: Boolean,
    onOpenOnlineLyricRematch: () -> Unit,
    minCardHeight: Dp,
    onHeightMeasured: (Int) -> Unit,
    compactLandscape: Boolean = false,
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
    var cardBackgroundColor by remember { mutableStateOf(Color.Transparent) }

    LaunchedEffect(albumArt) {
        val palette = albumArt?.generatePalette()
        val colorInt = palette?.vibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb
        cardBackgroundColor = colorInt?.let { Color(it).copy(alpha = 0.22f) } ?: Color.Transparent
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minCardHeight)
            .onSizeChanged { onHeightMeasured(it.height) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackgroundColor)
        ) {
        Column(modifier = Modifier.padding(if (compactLandscape) 16.dp else 20.dp)) {
            val context = LocalContext.current
            val appName = remember(controller.packageName) {
                ParserRuleHelper.getAppNameForPackage(context, controller.packageName)
            }
            val appIcon = remember(controller.packageName) {
                try {
                    val pm = context.packageManager
                    val drawable = pm.getApplicationIcon(controller.packageName)
                    (drawable as? BitmapDrawable)?.bitmap ?: run {
                        val bmp = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                } catch (_: Exception) {
                    null
                }
            }
            val openApp = { openMediaApp(context, controller, appName) }

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

            val trackHeaderContent: @Composable () -> Unit = {
                val artSize = if (compactLandscape) 60.dp else 80.dp
                val badgeSize = if (compactLandscape) 20.dp else 24.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(if (compactLandscape) 12.dp else 16.dp))
                            .clickable(onClick = openApp)
                    ) {
                        if (albumArt != null) {
                            Image(
                                bitmap = albumArt.asImageBitmap(),
                                contentDescription = stringResource(R.string.main_album_art_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(if (compactLandscape) 12.dp else 16.dp),
                                color = MaterialTheme.colorScheme.surfaceDim,
                                modifier = Modifier.matchParentSize()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(if (compactLandscape) 16.dp else 24.dp)
                                )
                            }
                        }

                        if (appIcon != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(if (compactLandscape) 2.dp else 4.dp)
                                    .size(badgeSize)
                            ) {
                                Image(
                                    bitmap = appIcon.asImageBitmap(),
                                    contentDescription = appName,
                                    modifier = Modifier.padding(if (compactLandscape) 3.dp else 4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(if (compactLandscape) 12.dp else 16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)) {
                            Text(
                                text = appName,
                                modifier = Modifier.padding(
                                    horizontal = if (compactLandscape) 8.dp else 10.dp,
                                    vertical = if (compactLandscape) 3.dp else 6.dp
                                ),
                                style = if (compactLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(if (compactLandscape) 4.dp else 10.dp))
                        Text(
                            text = title ?: stringResource(R.string.media_control_unknown_title),
                            style = if (compactLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(if (compactLandscape) 2.dp else 4.dp))
                        Text(
                            text = artist ?: stringResource(R.string.media_control_unknown_artist),
                            style = if (compactLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val primaryStatus = when {
                            isPrimary && primaryIsInstrumental -> stringResource(R.string.main_instrumental_marked)
                            isPrimary && !primaryLyricSource.isNullOrBlank() -> {
                                stringResource(R.string.main_lyric_source_fmt, primaryLyricSource)
                            }
                            else -> null
                        }
                        if (primaryStatus != null) {
                            Spacer(modifier = Modifier.height(if (compactLandscape) 4.dp else 8.dp))
                            Text(
                                text = primaryStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            val lyricPanelContent: @Composable () -> Unit = {
                LyricPanel(
                    lyric = lyric,
                    emptyText = if (isPrimary) {
                        when {
                            primaryIsInstrumental -> stringResource(R.string.main_no_lyrics)
                            primaryHasTimeline -> stringResource(
                                R.string.main_lyrics_sync_placeholder_fmt,
                                formatPlaybackTime(position),
                                formatPlaybackTime(duration)
                            )
                            primaryLyricResolveState == LyricRepository.LyricResolveState.NOT_FOUND ->
                                stringResource(R.string.main_lyrics_not_found)
                            else -> stringResource(R.string.main_waiting_for_lyrics)
                        }
                    } else {
                        stringResource(R.string.main_lyrics_unavailable)
                    }
                )
            }

            val rematchButtonContent: @Composable (Modifier) -> Unit = { btnModifier ->
                if (showOnlineLyricRematch) {
                    OutlinedButton(
                        onClick = onOpenOnlineLyricRematch,
                        modifier = btnModifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.online_lyric_rematch_entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            val sliderContent: @Composable () -> Unit = {
                MediaProgressBar(
                    value = currentProgress,
                    enabled = duration > 0,
                    isPlaying = isPlaying,
                    isDragging = isDragging,
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val transportRowContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundTransportButton(R.drawable.ic_skip_previous, stringResource(R.string.action_previous)) { controller.transportControls.skipToPrevious() }
                    FilledTonalButton(
                        onClick = { if (isPlaying) controller.transportControls.pause() else controller.transportControls.play() },
                        shape = CircleShape,
                        contentPadding = PaddingValues(if (compactLandscape) 12.dp else 18.dp)
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

            if (compactLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(0.46f)) {
                        trackHeaderContent()
                        rematchButtonContent(Modifier.padding(top = 8.dp))
                    }
                    Box(modifier = Modifier.weight(0.54f)) {
                        lyricPanelContent()
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                sliderContent()
                Spacer(modifier = Modifier.height(8.dp))
                transportRowContent()
            } else {
                trackHeaderContent()
                Spacer(modifier = Modifier.height(18.dp))
                lyricPanelContent()
                rematchButtonContent(Modifier.padding(top = 12.dp))
                Spacer(modifier = Modifier.height(18.dp))
                sliderContent()
                Spacer(modifier = Modifier.height(14.dp))
                transportRowContent()
            }
        }
        }
    }
}

@Composable
private fun LyricPanel(
    lyric: String?,
    emptyText: String,
) {
    val context = LocalContext.current
    val copiedText = stringResource(R.string.toast_copied)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clickable(enabled = !lyric.isNullOrBlank()) {
                lyric?.let {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Lyric", it))
                    Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.tap_to_copy_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(
                        text = emptyText,
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

@Composable
private fun MediaProgressBar(
    value: Float,
    enabled: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val showWavyIndicator = enabled && isPlaying && !isDragging

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onValueChange((offset.x / width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        onValueChange((offset.x / width).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        onValueChange((change.position.x / width).coerceIn(0f, 1f))
                    },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished
                )
            }
    ) {
        val clamped = value.coerceIn(0f, 1f)
        val width = maxWidth
        val thumbSize = if (isDragging && enabled) 12.dp else 0.dp
        key(showWavyIndicator) {
            AndroidView(
                factory = { context ->
                    val themedContext = ContextThemeWrapper(
                        context,
                        if (showWavyIndicator) {
                            R.style.ThemeOverlay_IslandLyrics_MediaProgress_Wavy
                        } else {
                            R.style.ThemeOverlay_IslandLyrics_MediaProgress_Flat
                        }
                    )
                    LinearProgressIndicator(themedContext, null).apply {
                        max = 10_000
                        isIndeterminate = false
                        setTrackStopIndicatorSize(0)
                        setIndicatorColor(activeTrackColor.toArgb())
                        trackColor = inactiveTrackColor.toArgb()
                        setProgressCompat((clamped * max).roundToInt(), false)
                    }
                },
                update = { indicator ->
                    indicator.setIndicatorColor(activeTrackColor.toArgb())
                    indicator.trackColor = inactiveTrackColor.toArgb()
                    indicator.alpha = if (enabled) 1f else 0.38f
                    indicator.setProgressCompat((clamped * indicator.max).roundToInt(), false)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }

        if (thumbSize > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (width * clamped - thumbSize / 2).coerceAtLeast(0.dp))
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

private fun formatPrimaryLyricSource(
    apiPath: String?,
    sourceLabel: String?,
    sourceApp: String?
): String? {
    val label = sourceLabel?.trim().takeUnless { it.isNullOrBlank() }
        ?: sourceApp?.trim().takeUnless { it.isNullOrBlank() }

    return when (apiPath) {
        "Online API" -> label?.let { "$it [在线]" } ?: "在线歌词"
        "Online Cache" -> label?.let { "$it [缓存]" } ?: "在线歌词缓存"
        "Local LRC" -> label ?: "本地歌词"
        "Lyricon" -> label?.let { "$it [Lyricon]" } ?: "Lyricon"
        "SuperLyric" -> label?.let { "$it [SuperLyric]" } ?: "SuperLyric"
        "Lyric Getter" -> label?.let { "$it [Lyric Getter]" } ?: "Lyric Getter"
        "Notification" -> label?.let { "$it [通知歌词]" } ?: "通知歌词"
        else -> label ?: apiPath?.trim().takeUnless { it.isNullOrBlank() }
    }
}

private fun isOnlineLyricSource(apiPath: String?): Boolean {
    return apiPath == "Online API" || apiPath == "Online Cache"
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
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
        val timeDelta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        currentPos += (timeDelta * state.playbackSpeed).toLong()
    }
    if (duration > 0 && currentPos > duration) currentPos = duration
    return currentPos.coerceAtLeast(0L)
}
