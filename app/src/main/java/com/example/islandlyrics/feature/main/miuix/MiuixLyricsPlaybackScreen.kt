/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.feature.main.belongsTo
import com.example.islandlyrics.feature.main.rememberWhitelistedMediaSessions
import com.example.islandlyrics.feature.main.selectHomePlaybackController
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import com.example.islandlyrics.ui.navigation.LocalPageExpansionProgress
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsClock
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsVisibility
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private const val CurrentLineScale = 1.08f
private const val SeekAcknowledgementTimeout = 4_000L
private const val SeekPositionTolerance = 1_000L

@Composable
internal fun MiuixLyricsPlaybackScreen(start: LyricsPlaybackStart, onBack: () -> Unit) {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null) {
        DisposableEffect(window, view) {
            val controller = WindowCompat.getInsetsController(window, view)
            val lightStatusBars = controller.isAppearanceLightStatusBars
            val lightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = lightStatusBars
                controller.isAppearanceLightNavigationBars = lightNavigationBars
            }
        }
    }
    MiuixTheme(colors = remember { darkColorScheme() }) {
        LyricsPlaybackContent(start, onBack)
    }
}

@Composable
private fun LyricsPlaybackContent(start: LyricsPlaybackStart, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { LyricRepository.getInstance() }
    val track by repo.liveMetadata.observeAsState(start.track)
    val parsed by repo.liveParsedLyrics.observeAsState()
    val lyric by repo.liveLyric.observeAsState()
    val currentLine by repo.liveCurrentLine.observeAsState()
    val albumArt by repo.liveTrackAlbumArt.observeAsState()
    val repoProgress = repo.liveProgress.observeAsState()
    val resolveState by repo.liveLyricResolveState.observeAsState(LyricRepository.LyricResolveState.PENDING)
    val primaryTokens = remember { FloatingLyricsVisibility.session.map { it.mediaSession }.distinctUntilChanged() }
    val primaryToken by primaryTokens.collectAsState(FloatingLyricsVisibility.session.value.mediaSession)
    val controllers = rememberWhitelistedMediaSessions()
    val controller = remember(controllers, track, primaryToken) {
        selectHomePlaybackController(controllers, track, primaryToken)
    }
    var media by remember(controller) { mutableStateOf(controller?.metadata) }
    var playback by remember(controller) { mutableStateOf(controller?.playbackState) }

    DisposableEffect(controller) {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { media = metadata }
            override fun onPlaybackStateChanged(state: PlaybackState?) { playback = state }
            override fun onSessionDestroyed() {
                media = null
                playback = null
            }
        }
        controller?.registerCallback(callback)
        onDispose { controller?.unregisterCallback(callback) }
    }

    val matchedTrack = track.takeIf { media.belongsTo(it) }
    val cover = remember(albumArt, media, track, matchedTrack) {
        val published = albumArt?.takeIf { it.trackKey == track?.albumArtKey }?.bitmap
        published?.takeUnless(Bitmap::isRecycled) ?: media.takeIf { matchedTrack != null }?.let {
            (it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.takeUnless(Bitmap::isRecycled)
        }
    }
    val actions = if (matchedTrack != null) playback?.actions ?: 0L else 0L
    val playing = playback?.state == PlaybackState.STATE_PLAYING
    val duration = track?.duration?.takeIf { it > 0L }
        ?: media?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { matchedTrack != null && it > 0L }
        ?: 0L
    val matchedPlayback = playback.takeIf { matchedTrack != null }
    val sessionClock = remember(matchedPlayback) { matchedPlayback.toLyricsClock(null) }
    val hasTrack = track != null
    val clock = remember(sessionClock, matchedPlayback, hasTrack, repoProgress) {
        derivedStateOf {
            sessionClock ?: matchedPlayback.toLyricsClock(repoProgress.value?.position?.takeIf { hasTrack })
        }
    }
    val liveLines = remember(parsed) { parsed.playbackLines() }
    val prefs = remember(context) { AppPreferences.of(context) }
    val showTranslation = LyricsPlaybackPreference.Translation.observe(prefs)
    val showRomanization = LyricsPlaybackPreference.Romanization.observe(prefs)
    val blurLyrics = LyricsPlaybackPreference.LyricBlur.observe(prefs)
    val tintProgress = LyricsPlaybackPreference.AlbumProgress.observe(prefs)
    val tintLyrics = LyricsPlaybackPreference.AlbumText.observe(prefs)
    val coverColors by rememberPlaybackCoverColors(cover)
    val albumAccent = remember(coverColors) { playbackAlbumAccent(coverColors) }
    val lyricColor = if (tintLyrics) albumAccent else MiuixTheme.colorScheme.onSurface
    val expansionProgress = LocalPageExpansionProgress.current
    val title = stringResource(R.string.main_now_playing_title)
    val playerPackage = track?.packageName
    val playerIntent = remember(context, playerPackage) {
        playerPackage?.let(context.packageManager::getLaunchIntentForPackage)
    }

    val trackIdentity = remember(track) { track?.albumArtKey?.copy(duration = 0L) }
    var retainedLines by remember(start, trackIdentity) {
        mutableStateOf(start.lines.takeIf { start.matches(track) }.orEmpty())
    }
    val lines = when {
        track == null -> emptyList()
        liveLines.isNotEmpty() -> liveLines
        resolveState == LyricRepository.LyricResolveState.NOT_FOUND -> emptyList()
        else -> retainedLines
    }
    SideEffect { retainedLines = lines }
    val position = key(track?.packageName, track?.title, track?.artist) {
        rememberLyricsPlaybackPosition(clock, duration)
    }
    var following by remember(trackIdentity) { mutableStateOf(true) }
    var pendingSeek by remember(trackIdentity, controller) { mutableStateOf<Long?>(null) }
    val canSeek = controller != null && duration > 0L && actions and PlaybackState.ACTION_SEEK_TO != 0L
    val seek = if (canSeek) { requested: Long ->
        if (controller.metadata.belongsTo(matchedTrack)) {
            val target = requested.coerceIn(0L, duration)
            if (runCatching { controller.transportControls.seekTo(target) }.isSuccess) pendingSeek = target
        }
    } else {
        null
    }

    LaunchedEffect(pendingSeek, trackIdentity, controller) {
        val target = pendingSeek ?: return@LaunchedEffect
        val confirmed = withTimeoutOrNull(SeekAcknowledgementTimeout) {
            snapshotFlow { position.value }.first { it != null && abs(it - target) <= SeekPositionTolerance }
        }
        if (confirmed != null) following = true
        pendingSeek = null
    }

    Scaffold(containerColor = Color.Transparent) { insets ->
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val statusBarInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
        val navigationBarInsets = WindowInsets.navigationBars.asPaddingValues()
        val headerInset = maxOf(insets.calculateTopPadding(), statusBarInsets.calculateTopPadding())
        val footerInset = maxOf(insets.calculateBottomPadding(), navigationBarInsets.calculateBottomPadding())
        val sidePadding = PaddingValues(
            start = insets.calculateStartPadding(layoutDirection) + 20.dp,
            end = insets.calculateEndPadding(layoutDirection) + 20.dp,
        )
        val useBlur = blurLyrics && isRenderEffectSupported() && isRuntimeShaderSupported()
        var headerHeight by remember { mutableStateOf(0.dp) }
        var footerHeight by remember { mutableStateOf(0.dp) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableForLyrics = (maxHeight - headerHeight - footerHeight).coerceAtLeast(0.dp)
            val fadeDistance = (availableForLyrics / 7f).coerceAtMost(32.dp)
            val blurDistance = (availableForLyrics / 3f).coerceAtMost(112.dp)
            val anchor = headerHeight + availableForLyrics / 3f
            val trailingSpace = (maxHeight - anchor - 32.dp).coerceAtLeast(0.dp)

            Box(Modifier.fillMaxSize()) {
                LyricsPlaybackBackground(coverColors)
                val lyricModifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = ((expansionProgress() - 0.1f) / 0.65f).coerceIn(0f, 1f)
                }
                if (lines.isNotEmpty()) {
                    val topFade = (headerHeight - 16.dp).coerceAtLeast(headerInset)
                    val bottomFade = (footerHeight - 8.dp).coerceAtLeast(footerInset)
                    val fading = lyricModifier.fadeLyricsAroundBars(topFade, bottomFade, fadeDistance)
                    val lyricLayer = if (useBlur) fading.blurLyricsAroundBars(topFade, bottomFade, blurDistance) else fading
                    key(trackIdentity) {
                        LyricsPlaybackList(
                            lines = lines,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            color = lyricColor,
                            position = position,
                            following = following,
                            onFollowingChange = { following = it },
                            onSeek = seek,
                            contentPadding = PaddingValues(top = anchor, bottom = trailingSpace),
                            modifier = lyricLayer.padding(sidePadding),
                        )
                    }
                } else {
                    PlaybackLyricFallback(
                        hasTrack = hasTrack,
                        currentLine = currentLine?.takeUnless { SecondaryTextResolver.isPlaceholder(it.text) } ?: parsed?.takeIf {
                            it.timelineCapability == LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY
                        }?.lines?.firstOrNull(),
                        lyric = lyric,
                        resolveState = resolveState,
                        position = position,
                        lyricColor = lyricColor,
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        modifier = lyricModifier.padding(sidePadding).padding(top = headerHeight, bottom = footerHeight),
                    )
                }
            }

            LyricsPlaybackHeader(
                title = track?.title ?: title,
                artist = track?.artist.orEmpty(),
                cover = cover,
                onBack = onBack,
                onOpenPlayer = playerIntent?.let { intent ->
                    {
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            // The player can be removed while the page is open.
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                    .padding(sidePadding).padding(top = headerInset)
                    .revealPlaybackChrome(expansionProgress),
            )

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                    .padding(sidePadding).padding(bottom = footerInset + 24.dp)
                    .revealPlaybackChrome(expansionProgress),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaybackLanguageMenu(
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        onTranslationChange = { LyricsPlaybackPreference.Translation.set(prefs, it) },
                        onRomanizationChange = { LyricsPlaybackPreference.Romanization.set(prefs, it) },
                    )
                    if (lines.isNotEmpty() && !following) {
                        TextButton(
                            text = stringResource(R.string.lyrics_playback_follow),
                            onClick = { following = true },
                        )
                    }
                }
                LyricsPlaybackControls(
                    controller = controller.takeIf { matchedTrack != null },
                    position = position,
                    duration = duration,
                    pendingSeek = pendingSeek,
                    actions = actions,
                    isPlaying = playing,
                    onSeek = seek,
                    progressColor = if (tintProgress) albumAccent else Color.White,
                )
            }
        }
    }
}

@Composable
private fun rememberLyricsPlaybackPosition(clock: State<FloatingLyricsClock?>, duration: Long): State<Long?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestClock = rememberUpdatedState(clock)
    return produceState<Long?>(null, duration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { latestClock.value.value }.collectLatest { anchor ->
                if (anchor == null) return@collectLatest
                while (true) {
                    val position = anchor.positionAt(SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    value = if (duration > 0L) position.coerceAtMost(duration) else position
                    if (anchor.speed <= 0f || (duration > 0L && position >= duration)) break
                    withFrameNanos { }
                }
            }
        }
    }
}

private fun Modifier.revealPlaybackChrome(progress: () -> Float): Modifier = graphicsLayer {
    alpha = ((progress() - 0.42f) / 0.58f).coerceIn(0f, 1f)
}

private fun playbackSecondaryText(
    romanization: String?,
    translation: String?,
    showRomanization: Boolean,
    showTranslation: Boolean,
): List<String> = listOfNotNull(
    romanization.takeIf { showRomanization },
    translation.takeIf { showTranslation },
).filterNot(SecondaryTextResolver::isPlaceholder).distinct()

@Composable
private fun PlaybackLyricFallback(
    hasTrack: Boolean,
    currentLine: LyricLine?,
    lyric: LyricRepository.LyricInfo?,
    resolveState: LyricRepository.LyricResolveState,
    position: State<Long?>,
    lyricColor: Color,
    showTranslation: Boolean,
    showRomanization: Boolean,
    modifier: Modifier = Modifier,
) {
    val line = currentLine?.takeIf { hasTrack && !SecondaryTextResolver.isPlaceholder(it.text) }
    val text = lyric?.takeIf { hasTrack && !SecondaryTextResolver.isPlaceholder(it.lyric) }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Column {
            when {
                line != null -> PlaybackLyricText(line, current = true, position = position, color = lyricColor)
                text != null -> Text(text.lyric, style = MiuixTheme.textStyles.title2, color = lyricColor)
                else -> Text(
                    text = stringResource(
                        when {
                            !hasTrack -> R.string.main_now_playing_subtitle_idle
                            resolveState == LyricRepository.LyricResolveState.NOT_FOUND -> R.string.main_lyrics_not_found
                            resolveState == LyricRepository.LyricResolveState.FOUND -> R.string.main_no_lyrics
                            else -> R.string.main_waiting_for_lyrics
                        }
                    ),
                    style = MiuixTheme.textStyles.body1,
                    color = lyricColor,
                )
            }
            playbackSecondaryText(
                if (line != null) line.roma else text?.roma,
                if (line != null) line.translation else text?.translation,
                showRomanization,
                showTranslation,
            ).forEach {
                Text(it, style = MiuixTheme.textStyles.body2, color = lyricColor)
            }
        }
    }
}

@Composable
private fun LyricsPlaybackHeader(
    title: String,
    artist: String,
    cover: Bitmap?,
    onBack: () -> Unit,
    onOpenPlayer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            MiuixBackIcon(contentDescription = stringResource(R.string.online_lyric_debug_back))
        }
        val openPlayerLabel = stringResource(R.string.lyrics_playback_open_player)
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                .then(if (onOpenPlayer != null) Modifier.clickable(onClickLabel = openPlayerLabel, onClick = onOpenPlayer) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = stringResource(R.string.main_album_art_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = colors.onSurface)
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, color = colors.onSurface, style = MiuixTheme.textStyles.title4, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) {
                Text(artist, color = colors.onSurface, style = MiuixTheme.textStyles.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LyricsPlaybackList(
    lines: List<LyricLine>,
    showTranslation: Boolean,
    showRomanization: Boolean,
    color: Color,
    position: State<Long?>,
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    onSeek: ((Long) -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val interludes = remember(lines) { lines.playbackInterludes() }
    val currentLineIndex by remember(lines, position) {
        derivedStateOf { position.value?.let(lines::currentPlaybackLine) ?: -1 }
    }
    val interlude by remember(lines, interludes, position) {
        derivedStateOf { position.value?.let { interludes.activeInterlude(currentLineIndex, it) } }
    }
    val interludeIndex = interlude?.beforeLineIndex
    val interludeKey = interludeIndex?.let { "interlude:$it" }
    val itemCount = lines.size + if (interludeIndex == null) 0 else 1
    val focusedIndex = interludeIndex ?: currentLineIndex.coerceAtLeast(0)
    val focusedKey = interludeKey ?: focusedIndex
    val scrollConnection = remember(onFollowingChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) onFollowingChange(false)
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(focusedKey, lines, following, contentPadding, showTranslation, showRomanization) {
        if (following) {
            val layout = snapshotFlow { listState.layoutInfo }.first { it.totalItemsCount == itemCount }
            val item = layout.visibleItemsInfo.firstOrNull { it.key == focusedKey }
            if (item != null) {
                listState.animateScrollBy(
                    item.offset.toFloat(),
                    animationSpec = folmeSpring(damping = 0.86f, response = 0.38f),
                )
            } else {
                listState.scrollToItem(focusedIndex)
            }
        }
    }

    LazyColumn(
        modifier = modifier.clipToBounds().nestedScroll(scrollConnection),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            count = itemCount,
            key = { index ->
                when {
                    index == interludeIndex -> "interlude:$index"
                    interludeIndex != null && index > interludeIndex -> index - 1
                    else -> index
                }
            },
            contentType = { index -> index == interludeIndex },
        ) { index ->
            val activeInterlude = interlude
            if (activeInterlude != null && index == activeInterlude.beforeLineIndex) {
                PlaybackInterludeDots(
                    interlude = activeInterlude,
                    position = position,
                    color = color,
                    modifier = Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(160)),
                )
            } else {
                val lineIndex = if (interludeIndex != null && index > interludeIndex) index - 1 else index
                val line = lines[lineIndex]
                val secondary = remember(line, showTranslation, showRomanization) {
                    playbackSecondaryText(line.roma, line.translation, showRomanization, showTranslation)
                }
                PlaybackLyricRow(
                    line = line,
                    secondary = secondary,
                    current = interludeIndex == null && lineIndex == currentLineIndex,
                    following = following,
                    color = color,
                    position = position,
                    onSeek = onSeek,
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                )
            }
        }
    }
}

@Composable
private fun PlaybackInterludeDots(
    interlude: PlaybackInterlude,
    position: State<Long?>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.lyrics_playback_interlude)
    Canvas(
        modifier = modifier.size(width = 58.dp, height = 38.dp)
            .semantics { contentDescription = description },
    ) {
        val elapsed = (position.value ?: interlude.startTime) - interlude.startTime
        val progress = (elapsed.toFloat() / (interlude.endTime - interlude.startTime)).coerceIn(0f, 1f)
        val radius = 3.8.dp.toPx()
        repeat(3) { index ->
            val dotProgress = (progress * 3f - index).coerceIn(0f, 1f)
            val pulse = sin(dotProgress * Math.PI).toFloat()
            drawCircle(
                color = color.copy(alpha = WordProgressCalculator.highlightAlpha(dotProgress)),
                radius = radius + pulse * 0.9.dp.toPx(),
                center = Offset(radius + index * 16.dp.toPx(), size.height / 2f),
            )
        }
    }
}

@Composable
private fun PlaybackLyricRow(
    line: LyricLine,
    secondary: List<String>,
    current: Boolean,
    following: Boolean,
    color: Color,
    position: State<Long?>,
    onSeek: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        if (current) CurrentLineScale else 1f,
        folmeSpring(damping = 0.78f, response = 0.34f),
        label = "lyric scale",
    )
    val lift by animateDpAsState(
        if (current && following) (-3).dp else 0.dp,
        folmeSpring(damping = 0.82f, response = 0.36f),
        label = "lyric lift",
    )
    val brightness by animateFloatAsState(
        if (current) 1f else if (following) 0.58f else 0.78f,
        tween(if (current) 155 else 280, easing = LinearOutSlowInEasing),
        label = "lyric brightness",
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .then(if (onSeek != null) Modifier.clickable(role = Role.Button) { onSeek(line.startTime) } else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.width(maxWidth / CurrentLineScale)
                .layout { measurable, constraints ->
                    val child = measurable.measure(constraints)
                    val height = constraints.constrainHeight((child.height * CurrentLineScale).roundToInt())
                    layout(child.width, height) { child.placeRelative(0, 0) }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift.toPx()
                    transformOrigin = TransformOrigin(0f, 0f)
                    alpha = brightness
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlaybackLyricText(line, current, position, color)
            secondary.forEach { text ->
                Text(text, style = MiuixTheme.textStyles.body2, color = color)
            }
        }
    }
}

@Composable
private fun LyricsPlaybackControls(
    controller: MediaController?,
    position: State<Long?>,
    duration: Long,
    pendingSeek: Long?,
    actions: Long,
    isPlaying: Boolean,
    onSeek: ((Long) -> Unit)?,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    val playAction = if (isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    val canToggle = actions and (playAction or PlaybackState.ACTION_PLAY_PAUSE) != 0L
    val controls = controller?.transportControls
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LyricsPlaybackProgress(position, duration, pendingSeek, progressColor, onSeek)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackControl(R.drawable.ic_skip_previous, R.string.action_previous, actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) {
                controls.dispatch { skipToPrevious() }
            }
            PlaybackControl(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) R.string.lyrics_playback_pause else R.string.action_play,
                canToggle,
                modifier = Modifier.size(56.dp),
            ) {
                controls.dispatch { if (isPlaying) pause() else play() }
            }
            PlaybackControl(R.drawable.ic_skip_next, R.string.lyrics_playback_next, actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) {
                controls.dispatch { skipToNext() }
            }
        }
    }
}

@Composable
private fun LyricsPlaybackProgress(
    position: State<Long?>,
    duration: Long,
    pendingSeek: Long?,
    color: Color,
    onSeek: ((Long) -> Unit)?,
) {
    var draggedProgress by remember(duration, onSeek != null) { mutableStateOf<Float?>(null) }
    val latestPendingSeek by rememberUpdatedState(pendingSeek)
    val displayed = remember(position, duration, onSeek != null) {
        derivedStateOf {
            draggedProgress?.let { (it * duration).roundToLong() }
                ?: latestPendingSeek ?: position.value ?: 0L
        }
    }
    val elapsedSecond by remember(displayed) { derivedStateOf { displayed.value / 1_000L } }
    val fraction by remember(displayed, duration) {
        derivedStateOf {
            if (duration > 0L) (displayed.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value = fraction,
            onValueChange = { draggedProgress = it },
            onValueChangeFinished = {
                draggedProgress?.let { onSeek?.invoke((it * duration).roundToLong()) }
                draggedProgress = null
            },
            enabled = onSeek != null,
            height = 10.dp,
            colors = SliderDefaults.sliderColors(
                foregroundColor = color,
                disabledForegroundColor = color.copy(alpha = 0.55f),
                backgroundColor = Color.White.copy(alpha = 0.24f),
                disabledBackgroundColor = Color.White.copy(alpha = 0.18f),
                thumbColor = Color.White,
                disabledThumbColor = Color.White.copy(alpha = 0.55f),
            ),
            modifier = Modifier.fillMaxWidth().semantics {
                if (onSeek != null) {
                    setProgress { target ->
                        onSeek((target.coerceIn(0f, 1f) * duration).roundToLong())
                        true
                    }
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(elapsedSecond * 1_000L), style = MiuixTheme.textStyles.body2)
            Text(formatPlaybackTime(duration), style = MiuixTheme.textStyles.body2)
        }
    }
}

@Composable
private fun PlaybackLanguageMenu(
    showTranslation: Boolean,
    showRomanization: Boolean,
    onTranslationChange: (Boolean) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            holdDownState = expanded,
            minWidth = 48.dp,
            minHeight = 48.dp,
        ) {
            Icon(
                imageVector = MiuixIcons.Translate,
                contentDescription = stringResource(R.string.lyric_text_display_mode_translation),
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (showTranslation || showRomanization) 1f else 0.65f),
            )
        }
        WindowListPopup(
            show = expanded,
            enableWindowDim = false,
            onDismissRequest = { expanded = false },
        ) {
            ListPopupColumn {
                PlaybackLanguageOption(
                    label = stringResource(R.string.lyric_text_display_mode_translation),
                    checked = showTranslation,
                    onCheckedChange = onTranslationChange,
                )
                PlaybackLanguageOption(
                    label = stringResource(R.string.lyrics_playback_romanization),
                    checked = showRomanization,
                    onCheckedChange = onRomanizationChange,
                )
            }
        }
    }
}

@Composable
private fun PlaybackLanguageOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
        Switch(checked = checked, onCheckedChange = null)
    }
}

private inline fun MediaController.TransportControls?.dispatch(action: MediaController.TransportControls.() -> Unit) {
    if (this == null) return
    try {
        action()
    } catch (_: RuntimeException) {
        // The active media session may have ended since the button was enabled.
    }
}

@Composable
private fun PlaybackControl(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = MiuixTheme.colorScheme.onSurface
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(description),
            modifier = Modifier.size(28.dp),
            tint = color.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}
