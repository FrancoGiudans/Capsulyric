/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.example.islandlyrics.ui.overlay.floating.toLyricsClock
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import kotlinx.coroutines.delay
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val CurrentLineScale = 1.11f
private const val LyricRowScaleLimit = 1.13f
private const val LyricTransitionMillis = 330
private val LyricRowSpacing = 10.dp
private val LyricVerticalPadding = 6.dp
private val InterludeDotDiameter = 11.dp
private val InterludeDotSpacing = 11.dp
private val InterludeDotWidth = 60.dp
private const val InterludeBreathPeriodMillis = 1_250L
private const val SeekAcknowledgementTimeout = 4_000L
private const val SeekPositionTolerance = 1_000L
private const val ControlsHideDelayMillis = 4_500L
private const val ControlsVisibilityMillis = 220
private const val AutoFollowDelayMillis = 5_000L
private val LyricTransitionSpec = tween<Float>(LyricTransitionMillis, easing = FastOutSlowInEasing)

private enum class PlaybackSheet { Settings, Volume }

@Composable
internal fun MiuixLyricsPlaybackScreen(
    start: LyricsPlaybackStart,
    onBack: () -> Unit,
    onOpenOnlineLyricRematch: () -> Unit,
) {
    MiuixTheme(colors = remember { darkColorScheme() }) {
        LyricsPlaybackContent(start, onBack, onOpenOnlineLyricRematch)
    }
}

@Composable
private fun PlaybackSystemBars(hideBars: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
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
    if (hideBars) {
        DisposableEffect(window, view) {
            val controller = WindowCompat.getInsetsController(window, view)
            val bars = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(bars)
            onDispose {
                controller.show(bars)
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}

@Composable
private fun LyricsPlaybackContent(
    start: LyricsPlaybackStart,
    onBack: () -> Unit,
    onOpenOnlineLyricRematch: () -> Unit,
) {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    PlaybackSystemBars(LyricsPlaybackPreference.HideSystemBars.observe(prefs))
    val showTranslation = LyricsPlaybackPreference.Translation.observe(prefs)
    val showRomanization = LyricsPlaybackPreference.Romanization.observe(prefs)
    val blurLyrics = LyricsPlaybackPreference.LyricBlur.observe(prefs)
    val typography = LyricsPlaybackTypography.observe(prefs)
    val mainLyricStyle = MiuixTheme.textStyles.title2.copy(
        fontSize = typography.size.sp,
        fontWeight = FontWeight(typography.weight.weight),
    )
    val secondaryLyricStyle = MiuixTheme.textStyles.body2.copy(
        fontSize = (typography.size * (14f / 24f)).coerceAtLeast(12f).sp,
        fontWeight = FontWeight((typography.weight.weight - 200).coerceAtLeast(FontWeight.Normal.weight)),
    )
    val tintProgress = LyricsPlaybackPreference.AlbumProgress.observe(prefs)
    val tintLyrics = LyricsPlaybackPreference.AlbumText.observe(prefs)
    val keepScreenOn = LyricsPlaybackPreference.KeepScreenOn.observe(prefs)
    val autoHideControls = LyricsPlaybackPreference.AutoHideControls.observe(prefs)
    val autoFollow = LyricsPlaybackPreference.AutoFollow.observe(prefs)
    val coverAppearance by rememberPlaybackCoverAppearance(cover)
    val albumAccent = remember(coverAppearance) { playbackAlbumAccent(coverAppearance?.colors) }
    val lyricColor = if (tintLyrics) albumAccent else MiuixTheme.colorScheme.onSurface
    val expansionProgress = LocalPageExpansionProgress.current
    val title = stringResource(R.string.main_now_playing_title)
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
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTouched by remember { mutableStateOf(false) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var languageMenuOpen by remember(landscape) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<PlaybackSheet?>(null) }
    val overlayOpen = sheet != null || languageMenuOpen
    val canHideControls = !landscape && autoHideControls && playing && lines.isNotEmpty() && following &&
        !controlsTouched && !overlayOpen && pendingSeek == null
    val showControls = controlsVisible || !canHideControls
    LaunchedEffect(canHideControls, controlsVisible, controlsInteraction) {
        if (!canHideControls) {
            controlsVisible = true
        } else if (controlsVisible) {
            delay(ControlsHideDelayMillis)
            controlsVisible = false
        }
    }
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

    val lyricsContent: @Composable (Modifier, Modifier, PaddingValues, Boolean, Dp) -> Unit =
        { listModifier, fallbackModifier, contentPadding, blurDepth, rowSpacing ->
            if (lines.isNotEmpty()) {
                key(trackIdentity) {
                    LyricsPlaybackList(
                        lines = lines,
                        showTranslation = showTranslation,
                        showRomanization = showRomanization,
                        color = lyricColor,
                        mainStyle = mainLyricStyle,
                        secondaryStyle = secondaryLyricStyle,
                        position = position,
                        following = following,
                        autoFollow = autoFollow && playing && !overlayOpen && pendingSeek == null,
                        blurDepth = blurDepth,
                        landscape = landscape,
                        onFollowingChange = { following = it },
                        onSeek = seek,
                        contentPadding = contentPadding,
                        rowSpacing = rowSpacing,
                        modifier = listModifier,
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
                    mainStyle = mainLyricStyle,
                    secondaryStyle = secondaryLyricStyle,
                    showTranslation = showTranslation,
                    showRomanization = showRomanization,
                    modifier = fallbackModifier,
                )
            }
        }
    val controls: @Composable () -> Unit = {
        LyricsPlaybackControls(
            controller = controller.takeIf { matchedTrack != null },
            position = position,
            duration = duration,
            pendingSeek = pendingSeek,
            actions = actions,
            isPlaying = playing,
            onSeek = seek,
            progressColor = if (tintProgress) albumAccent else Color.White,
            compact = landscape,
        )
    }
    val playbackActions: @Composable () -> Unit = {
        PlaybackActions(
            showTranslation = showTranslation,
            showRomanization = showRomanization,
            onOpenSettings = { sheet = PlaybackSheet.Settings },
            onOpenVolume = { sheet = PlaybackSheet.Volume },
            onTranslationChange = { LyricsPlaybackPreference.Translation.set(prefs, it) },
            onRomanizationChange = { LyricsPlaybackPreference.Romanization.set(prefs, it) },
            onLanguageMenuExpandedChange = { languageMenuOpen = it },
            buttonSize = if (landscape) 44.dp else 56.dp,
        )
    }
    val showFollowButton = lines.isNotEmpty() && !following
    val followButton: @Composable () -> Unit = {
        TextButton(
            text = stringResource(R.string.lyrics_playback_follow),
            onClick = { following = true },
        )
    }
    val lyricModifier = Modifier.fillMaxSize().graphicsLayer {
        alpha = ((expansionProgress() - 0.1f) / 0.65f).coerceIn(0f, 1f)
    }
    Scaffold(
        modifier = if (keepScreenOn && playing) Modifier.keepScreenOn() else Modifier,
        containerColor = MiuixTheme.colorScheme.surface.copy(alpha = 1f),
    ) { insets ->
        val canRematch = hasTrack && !AppPreferences.isOfflineModeEnabled(prefs) &&
            isOnlineLyricSource(parsed?.apiPath ?: lyric?.apiPath)
        LyricsPlaybackSettingsSheet(
            show = sheet == PlaybackSheet.Settings,
            prefs = prefs,
            onDismiss = { sheet = null },
            onOpenOnlineLyricRematch = if (canRematch) {
                {
                    if (sheet == PlaybackSheet.Settings) {
                        sheet = null
                        onOpenOnlineLyricRematch()
                    }
                }
            } else {
                null
            },
        )
        LyricsPlaybackVolumeSheet(
            show = sheet == PlaybackSheet.Volume,
            onDismiss = { sheet = null },
        )
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
        val useBlur = blurLyrics && isRenderEffectSupported()
        if (landscape) {
            val hasSubLyrics = remember(lines, showTranslation, showRomanization) {
                lines.any { playbackSecondaryText(it.roma, it.translation, showRomanization, showTranslation).isNotEmpty() }
            }
            Box(Modifier.fillMaxSize()) {
                LyricsPlaybackBackground(coverAppearance, playing)
                LandscapeLyricsPlaybackLayout(
                    title = track?.title ?: title,
                    artist = track?.artist.orEmpty(),
                    cover = cover,
                    onBack = onBack,
                    hasSubLyrics = hasSubLyrics,
                    playbackActions = playbackActions,
                    followButton = followButton.takeIf { showFollowButton },
                    controls = controls,
                    lyrics = { listModifier, padding, rowSpacing ->
                        lyricsContent(lyricModifier.then(listModifier), lyricModifier, padding, useBlur && following, rowSpacing)
                    },
                    modifier = Modifier.fillMaxSize()
                        .padding(top = headerInset + 4.dp, bottom = footerInset + 8.dp),
                )
            }
            return@Scaffold
        }
        var headerHeight by remember { mutableStateOf(0.dp) }
        var footerHeight by remember { mutableStateOf(0.dp) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableForLyrics = (maxHeight - headerHeight - footerHeight).coerceAtLeast(0.dp)
            val fadeDistance = (availableForLyrics / 5f).coerceAtMost(56.dp)
            val blurDistance = (availableForLyrics / 3f).coerceAtMost(112.dp)
            val anchor = headerHeight + availableForLyrics / 3f
            val trailingSpace = (maxHeight - anchor - 32.dp).coerceAtLeast(0.dp)
            val visibleBottomFade = (footerHeight - 8.dp).coerceAtLeast(footerInset)
            val bottomFade = if (autoHideControls) {
                animateDpAsState(
                    targetValue = if (showControls) visibleBottomFade else footerInset,
                    animationSpec = tween(ControlsVisibilityMillis),
                    label = "lyric bottom fade position",
                )
            } else {
                rememberUpdatedState(visibleBottomFade)
            }

            Box(Modifier.fillMaxSize()) {
                LyricsPlaybackBackground(coverAppearance, playing)
                val topFade = (headerHeight - 16.dp).coerceAtLeast(headerInset)
                val fading = lyricModifier.fadeLyricsAroundBars(topFade, bottomFade, fadeDistance)
                val lyricLayer = if (lines.isNotEmpty() && useBlur && isRuntimeShaderSupported()) {
                    fading.blurLyricsAroundBars(
                        top = topFade,
                        bottomFade = bottomFade,
                        bottomWithControls = visibleBottomFade,
                        reach = blurDistance,
                    )
                } else {
                    fading
                }
                lyricsContent(
                    lyricLayer.padding(sidePadding),
                    lyricModifier.padding(sidePadding).padding(top = headerHeight, bottom = footerHeight),
                    PaddingValues(top = anchor, bottom = trailingSpace),
                    useBlur && following,
                    LyricRowSpacing,
                )
            }

            LyricsPlaybackHeader(
                title = track?.title ?: title,
                artist = track?.artist.orEmpty(),
                cover = cover,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                    .padding(sidePadding).padding(top = headerInset)
                    .revealPlaybackChrome(expansionProgress),
            )

            if (!showControls) {
                val revealLabel = stringResource(R.string.lyrics_playback_show_controls)
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(footerInset + 52.dp)
                        .semantics { contentDescription = revealLabel }
                        .clickable(role = Role.Button) { controlsVisible = true },
                )
            }
            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(ControlsVisibilityMillis)),
                exit = fadeOut(tween(ControlsVisibilityMillis)),
            ) {
                val interactionModifier = if (autoHideControls) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            controlsTouched = true
                            controlsInteraction++
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                controlsTouched = false
                            }
                        }
                    }
                } else {
                    Modifier
                }
                Column(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                        .padding(sidePadding).padding(bottom = footerInset + 24.dp)
                        .revealPlaybackChrome(expansionProgress)
                        .then(interactionModifier),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        playbackActions()
                        if (showFollowButton) followButton()
                    }
                    controls()
                }
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
    mainStyle: TextStyle,
    secondaryStyle: TextStyle,
    showTranslation: Boolean,
    showRomanization: Boolean,
    modifier: Modifier = Modifier,
) {
    val line = currentLine?.takeIf { hasTrack && !SecondaryTextResolver.isPlaceholder(it.text) }
    val text = lyric?.takeIf { hasTrack && !SecondaryTextResolver.isPlaceholder(it.lyric) }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Column {
            when {
                line != null -> PlaybackLyricText(line, current = true, position = position, color = Color.White, style = mainStyle)
                text != null -> Text(text.lyric, style = mainStyle, color = Color.White)
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
                Text(it, style = secondaryStyle, color = lyricColor.copy(alpha = PlaybackFocusedSecondaryAlpha))
            }
        }
    }
}

@Composable
private fun LandscapeLyricsPlaybackLayout(
    title: String,
    artist: String,
    cover: Bitmap?,
    onBack: () -> Unit,
    hasSubLyrics: Boolean,
    playbackActions: @Composable () -> Unit,
    followButton: (@Composable () -> Unit)?,
    controls: @Composable () -> Unit,
    lyrics: @Composable (Modifier, PaddingValues, Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expansionProgress = LocalPageExpansionProgress.current
    val direction = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    Row(modifier) {
        LandscapePlaybackPanel(
            title, artist, cover, onBack, playbackActions, controls,
            cutoutStart = cutout.calculateStartPadding(direction),
            modifier = Modifier.weight(0.44f).fillMaxHeight().revealPlaybackChrome(expansionProgress),
        )
        BoxWithConstraints(
            Modifier.weight(0.56f).fillMaxHeight()
                .padding(start = 24.dp, end = 20.dp + cutout.calculateEndPadding(direction)),
        ) {
            val anchor = maxHeight * LandscapeLyricFocusFraction
            val trailingSpace = maxHeight - anchor
            val topFade = (maxHeight * 0.09f).coerceAtMost(28.dp)
            val bottomFade = (maxHeight * 0.11f).coerceAtMost(32.dp)
            val spaceFraction = ((maxHeight - 260.dp) / 160.dp).coerceIn(0f, 1f)
            // Lyrics already have 12 dp of vertical row padding.
            val rowSpacing = if (hasSubLyrics) lerp(6f, 8f, spaceFraction).dp else lerp(16f, 20f, spaceFraction).dp
            lyrics(
                Modifier.fadeLyricsAroundBars(0.dp, rememberUpdatedState(0.dp), topFade, bottomFade),
                PaddingValues(top = anchor, bottom = trailingSpace),
                rowSpacing,
            )
            if (followButton != null) {
                Box(Modifier.align(Alignment.TopEnd).revealPlaybackChrome(expansionProgress)) {
                    followButton()
                }
            }
        }
    }
}

@Composable
private fun LandscapePlaybackPanel(
    title: String,
    artist: String,
    cover: Bitmap?,
    onBack: () -> Unit,
    playbackActions: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    cutoutStart: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val coverLimit = minOf(maxWidth * 0.68f, maxHeight * 0.46f)
        Layout(
            modifier = Modifier.fillMaxSize(),
            contents = listOf(
                {
                    IconButton(onClick = onBack) {
                        MiuixBackIcon(contentDescription = stringResource(R.string.online_lyric_debug_back))
                    }
                },
                playbackActions,
                { LyricsPlaybackCover(cover, Modifier, cornerRadius = 16.dp) },
                { LyricsPlaybackTrackInfo(title, artist, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                controls,
            ),
        ) { (back, actions, artwork, metadata, transport), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val safeStart = cutoutStart.roundToPx().coerceIn(0, width)
            val safeWidth = width - safeStart
            val backStart = (safeStart + 20.dp.roundToPx()).coerceAtMost(width)
            val toolbarConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val backPlaceable = back.single().measure(toolbarConstraints.copy(maxWidth = width - backStart))
            val backEnd = backStart + backPlaceable.width
            val actionsPlaceable = actions.single().measure(toolbarConstraints.copy(maxWidth = width - backEnd))
            val toolbarHeight = maxOf(backPlaceable.height, actionsPlaceable.height)
            val groupHeightLimit = height - toolbarHeight
            val controls = transport.single()
            val controlsHeight = controls.maxIntrinsicHeight(width).coerceAtMost(groupHeightLimit)
            val metadataHeightLimit = groupHeightLimit - controlsHeight
            val track = metadata.single()
            val trackHeight = track.maxIntrinsicHeight(width).coerceAtMost(metadataHeightLimit)
            val available = metadataHeightLimit - trackHeight
            val gap = minOf(16.dp.roundToPx(), available / 4)
            val coverSize = minOf(coverLimit.roundToPx(), available - 2 * gap, safeWidth)
            val controlsWidth = minOf(maxOf(coverSize, 250.dp.roundToPx()), (width * 0.85f).toInt(), safeWidth)
            val centeredControlsStart = (width - controlsWidth) / 2
            // Only shift the group if the centered controls would overlap the cutout.
            val controlsStart = maxOf(centeredControlsStart, safeStart)
            val groupShift = controlsStart - centeredControlsStart
            val coverStart = (width - coverSize) / 2 + groupShift
            val trackWidth = (width - 2 * groupShift).coerceAtLeast(0)
            val trackStart = width - trackWidth
            val coverPlaceable = artwork.single().measure(Constraints.fixed(coverSize, coverSize))
            val trackPlaceable = track.measure(Constraints.fixedWidth(trackWidth).copy(maxHeight = trackHeight))
            val controlsPlaceable = controls.measure(
                Constraints.fixedWidth(controlsWidth).copy(
                    maxHeight = groupHeightLimit - coverSize - 2 * gap - trackPlaceable.height,
                ),
            )
            val groupHeight = coverSize + 2 * gap + trackPlaceable.height + controlsPlaceable.height
            layout(width, height) {
                backPlaceable.placeRelative(backStart, (toolbarHeight - backPlaceable.height) / 2)
                actionsPlaceable.placeRelative(
                    (controlsStart + controlsWidth - actionsPlaceable.width).coerceAtLeast(backEnd),
                    (toolbarHeight - actionsPlaceable.height) / 2,
                )
                val groupTop = toolbarHeight + (groupHeightLimit - groupHeight) / 2
                coverPlaceable.placeRelative(coverStart, groupTop)
                val trackTop = groupTop + coverSize + gap
                trackPlaceable.placeRelative(trackStart, trackTop)
                controlsPlaceable.placeRelative(
                    controlsStart,
                    trackTop + trackPlaceable.height + gap,
                )
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            MiuixBackIcon(contentDescription = stringResource(R.string.online_lyric_debug_back))
        }
        LyricsPlaybackCover(cover, Modifier.size(52.dp), cornerRadius = 12.dp)
        LyricsPlaybackTrackInfo(title, artist, Modifier.weight(1f))
    }
}

@Composable
private fun LyricsPlaybackCover(
    cover: Bitmap?,
    modifier: Modifier,
    cornerRadius: Dp,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)).background(colors.surface),
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
}

@Composable
private fun LyricsPlaybackTrackInfo(
    title: String,
    artist: String,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val color = MiuixTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title, Modifier.fillMaxWidth(), color = color, style = MiuixTheme.textStyles.title4,
            textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (artist.isNotBlank()) {
            Text(
                artist, Modifier.fillMaxWidth(), color = color, style = MiuixTheme.textStyles.body2,
                textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsPlaybackList(
    lines: List<LyricLine>,
    showTranslation: Boolean,
    showRomanization: Boolean,
    color: Color,
    mainStyle: TextStyle,
    secondaryStyle: TextStyle,
    position: State<Long?>,
    following: Boolean,
    autoFollow: Boolean,
    blurDepth: Boolean,
    landscape: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    onSeek: ((Long) -> Unit)?,
    contentPadding: PaddingValues,
    rowSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val updateFollowing by rememberUpdatedState(onFollowingChange)
    LaunchedEffect(autoFollow, following) {
        if (autoFollow && !following) {
            snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
                if (!scrolling) {
                    delay(AutoFollowDelayMillis)
                    updateFollowing(true)
                }
            }
        }
    }
    val density = LocalDensity.current
    val spacing = with(density) { rowSpacing.roundToPx() }
    val linePadding = with(density) { LyricVerticalPadding.roundToPx() }
    val textMeasurer = rememberTextMeasurer()
    val interludeHeight = textMeasurer.measure("•••", style = mainStyle, maxLines = 1).size.height
    val interludes = remember(lines) { lines.playbackInterludes() }
    val target by remember(lines, position) {
        derivedStateOf {
            val playbackMs = position.value
            val current = playbackMs?.let(lines::currentPlaybackLine) ?: -1
            val interlude = playbackMs?.let { interludes.activeInterlude(current, it) }
            if (interlude != null && playbackMs >= interlude.exitStartTime) {
                PlaybackFocus(interlude.beforeLineIndex, null)
            } else {
                PlaybackFocus(current, interlude)
            }
        }
    }
    val frame = remember(lines) { mutableStateOf(PlaybackLyricFrame(target.highlightedLine)) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) updateFollowing(false)
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(
        target, lines, following, contentPadding, mainStyle, secondaryStyle, interludeHeight,
        showTranslation, showRomanization, spacing, landscape, linePadding,
    ) {
        val layout = snapshotFlow { listState.layoutInfo }.first { it.totalItemsCount == lines.size }
        val targetIndex = target.lineIndex.coerceAtLeast(0)
        val from = frame.value
        val targetGap = target.interlude
        val previousGap = from.interlude
        val lineFocusOffset = if (landscape && following && targetGap == null) {
            val textWidth = (layout.viewportSize.width / LyricRowScaleLimit).roundToInt()
            val textHeight = textMeasurer.measure(
                lines[targetIndex].text,
                style = mainStyle,
                constraints = Constraints(maxWidth = textWidth),
            ).size.height
            mainLyricCenter(textHeight, linePadding, activation = 1f).roundToInt()
        } else {
            0
        }
        when {
            targetGap != null -> {
                val intro = targetGap.beforeLineIndex == 0
                val continuing = previousGap == targetGap
                if (!continuing && previousGap != null) {
                    frame.value = PlaybackLyricFrame(target.highlightedLine)
                }
                val item = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                val anchorItem = if (following && (item == null || !continuing && previousGap != null)) {
                    listState.scrollToItem(targetIndex)
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                } else {
                    item
                }
                val startOffset = -(anchorItem?.offset ?: 0).toFloat()
                val startHeight = if (continuing) from.gapHeight else 0f
                val height = interludeHeight.toFloat()
                val rowHeight = if (intro) 0f else anchorItem?.let { it.size - startHeight } ?: height
                val targetOffset = rowHeight + if (landscape) height / 2f else 0f
                if (continuing && startHeight == height && from.dotAlpha == 1f && from.dotScale == 1f) {
                    if (following) listState.scrollToItem(targetIndex, targetOffset.roundToInt())
                    return@LaunchedEffect
                }
                animate(0f, 1f, animationSpec = LyricTransitionSpec) { progress, _ ->
                    val gapHeight = lerp(startHeight, height, progress)
                    val enterDots = if (continuing) progress else ((progress - 0.35f) / 0.65f).coerceAtLeast(0f)
                    frame.value = PlaybackLyricFrame(
                        focusedLine = -1,
                        previousLine = from.focusedLine,
                        progress = progress,
                        interlude = targetGap,
                        gapHeight = gapHeight,
                        dotAlpha = lerp(if (continuing) from.dotAlpha else 0f, 1f, enterDots),
                        dotScale = lerp(if (continuing) from.dotScale else 0.75f, 1f, enterDots),
                    )
                    if (following) {
                        // Move past the outgoing row as the single-line slot opens.
                        val offset = lerp(startOffset, targetOffset, progress)
                        listState.requestScrollToItem(targetIndex, offset.roundToInt())
                    }
                }
            }
            previousGap != null && previousGap.beforeLineIndex == target.lineIndex -> {
                val intro = targetIndex == 0
                val anchorIndex = if (intro) 0 else targetIndex - 1
                val anchorItem = layout.visibleItemsInfo.firstOrNull { it.index == anchorIndex }
                val startHeight = from.gapHeight
                val endOffset = lineFocusOffset + if (intro) 0f else {
                    (anchorItem?.let { it.size - startHeight } ?: startHeight) + spacing
                }
                val startOffset = anchorItem?.let { -it.offset.toFloat() } ?: endOffset
                val startProgress = if (from.focusedLine == target.highlightedLine && from.progress < 1f) {
                    from.progress
                } else {
                    0f
                }
                snapshotFlow { position.value ?: previousGap.endTime }.first { playbackMs ->
                    val progress = FastOutSlowInEasing.transform(previousGap.exitProgress(playbackMs))
                    val remainingProgress = ((progress - startProgress) / (1f - startProgress)).coerceIn(0f, 1f)
                    frame.value = PlaybackLyricFrame(
                        focusedLine = target.highlightedLine,
                        previousLine = -1,
                        progress = progress,
                        interlude = previousGap.takeIf { progress < 1f },
                        gapHeight = startHeight * (1f - remainingProgress),
                        dotAlpha = from.dotAlpha,
                        dotScale = from.dotScale,
                    )
                    if (following) {
                        if (progress == 1f) {
                            listState.requestScrollToItem(targetIndex, lineFocusOffset)
                        } else {
                            listState.requestScrollToItem(
                                anchorIndex,
                                lerp(startOffset, endOffset, remainingProgress).roundToInt(),
                            )
                        }
                    }
                    progress == 1f
                }
            }
            else -> {
                val item = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                val distance = (item?.offset ?: 0) + lineFocusOffset
                val adjacent = target.lineIndex == from.focusedLine + 1
                val jumpToTarget = item == null || !adjacent && target.highlightedLine != from.focusedLine
                when {
                    previousGap != null || following && jumpToTarget -> {
                        frame.value = PlaybackLyricFrame(target.highlightedLine)
                        if (following) listState.scrollToItem(targetIndex, lineFocusOffset)
                    }
                    following && distance != 0 -> {
                        listState.scroll {
                            var scrolled = 0f
                            animate(0f, 1f, animationSpec = LyricTransitionSpec) { progress, _ ->
                                scrolled += scrollBy(distance * progress - scrolled)
                                frame.value = PlaybackLyricFrame(target.highlightedLine, from.focusedLine, progress)
                            }
                        }
                    }
                    else -> animate(0f, 1f, animationSpec = LyricTransitionSpec) { progress, _ ->
                        frame.value = PlaybackLyricFrame(target.highlightedLine, from.focusedLine, progress)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.clipToBounds().nestedScroll(scrollConnection),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        items(count = lines.size, key = { it }) { index ->
            val line = lines[index]
            val secondary = remember(line, showTranslation, showRomanization) {
                playbackSecondaryText(line.roma, line.translation, showRomanization, showTranslation)
            }
            Column {
                if (index == 0 && interludes.containsKey(0)) {
                    PlaybackInterludeSpace(0, interludeHeight, frame, position, color)
                }
                PlaybackLyricRow(
                    line = line,
                    secondary = secondary,
                    index = index,
                    frame = frame,
                    following = following,
                    blurDepth = blurDepth,
                    landscapeListState = listState.takeIf { landscape },
                    color = color,
                    mainStyle = mainStyle,
                    secondaryStyle = secondaryStyle,
                    position = position,
                    onSeek = onSeek,
                )
                if (interludes.containsKey(index + 1)) {
                    PlaybackInterludeSpace(index + 1, interludeHeight, frame, position, color)
                }
            }
        }
    }
}

private data class PlaybackFocus(val lineIndex: Int, val interlude: PlaybackInterlude?) {
    val highlightedLine: Int get() = if (interlude == null) lineIndex else -1
}

private data class PlaybackLyricFrame(
    val focusedLine: Int,
    val previousLine: Int = focusedLine,
    val progress: Float = 1f,
    val interlude: PlaybackInterlude? = null,
    val gapHeight: Float = 0f,
    val dotAlpha: Float = 0f,
    val dotScale: Float = 1f,
) {
    fun activation(index: Int): Float = lerp(
        if (index == previousLine) 1f else 0f,
        if (index == focusedLine) 1f else 0f,
        progress,
    )

    fun distance(index: Int): Float {
        val gapIndex = interlude?.beforeLineIndex
        fun distanceTo(line: Int): Float =
            when {
                line >= 0 -> abs(index - line).toFloat()
                gapIndex == null -> index + 1f
                index < gapIndex -> (gapIndex - index).toFloat()
                else -> index - gapIndex + 1f
            }
        return lerp(distanceTo(previousLine), distanceTo(focusedLine), progress)
    }
}

@Composable
private fun PlaybackInterludeSpace(
    beforeIndex: Int,
    lineHeight: Int,
    frame: State<PlaybackLyricFrame>,
    position: State<Long?>,
    color: Color,
) {
    val interlude = remember(beforeIndex, frame) {
        derivedStateOf { frame.value.interlude?.takeIf { it.beforeLineIndex == beforeIndex } }
    }.value ?: return
    val description = stringResource(R.string.lyrics_playback_interlude)
    Box(
        propagateMinConstraints = true,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = lineHeight, maxHeight = lineHeight))
            val height = constraints.constrainHeight(frame.value.gapHeight.roundToInt())
            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
        },
    ) {
        Canvas(
            modifier = Modifier.width(InterludeDotWidth)
                .semantics { contentDescription = description },
        ) {
            val motion = frame.value
            val playbackMs = position.value ?: interlude.startTime
            val elapsed = (playbackMs - interlude.startTime).coerceAtLeast(0L)
            val cycle = (elapsed % InterludeBreathPeriodMillis).toFloat() / InterludeBreathPeriodMillis
            val charge = FastOutSlowInEasing.transform(interlude.chargeProgress(playbackMs))
            val exit = if (motion.focusedLine == interlude.beforeLineIndex) motion.progress else 0f
            val groupScale = lerp(1f, 1.08f, charge) * (1f - exit) * motion.dotScale
            val baseRadius = InterludeDotDiameter.toPx() / 2f
            val pitch = (InterludeDotDiameter + InterludeDotSpacing).toPx()
            repeat(3) { index ->
                val phase = cycle + 0.22f - index * 0.14f
                val breath = (1f - cos(phase * 2.0 * Math.PI).toFloat()) / 2f
                val light = interlude.dotProgress(index, playbackMs)
                val breathScale = 1f + 0.04f * breath * light * (1f - charge)
                val dotScale = lerp(0.85f, 1f, light) * breathScale * groupScale
                val centerX = (baseRadius + index * pitch) * groupScale
                drawCircle(
                    color = color.copy(alpha = motion.dotAlpha * (1f - exit) * lerp(0.28f, 0.95f, light)),
                    radius = baseRadius * dotScale,
                    center = Offset(centerX, size.height / 2f),
                )
            }
        }
    }
}

@Composable
private fun PlaybackLyricRow(
    line: LyricLine,
    secondary: List<String>,
    index: Int,
    frame: State<PlaybackLyricFrame>,
    following: Boolean,
    blurDepth: Boolean,
    landscapeListState: LazyListState?,
    color: Color,
    mainStyle: TextStyle,
    secondaryStyle: TextStyle,
    position: State<Long?>,
    onSeek: ((Long) -> Unit)?,
) {
    val sweeping by remember(frame, index) {
        derivedStateOf { frame.value.focusedLine == index }
    }
    val mainTextHeight = remember { mutableIntStateOf(0) }
    val linePadding = with(LocalDensity.current) { LyricVerticalPadding.roundToPx() }
    val depth = remember(frame, index, landscapeListState, linePadding) {
        derivedStateOf {
            val motion = frame.value
            val layout = landscapeListState?.layoutInfo
            val item = layout?.visibleItemsInfo?.firstOrNull { it.index == index }
            val distance = if (layout != null && item != null && mainTextHeight.intValue > 0) {
                val introHeight = if (index == 0 && motion.interlude?.beforeLineIndex == 0) motion.gapHeight else 0f
                val center = item.offset + introHeight +
                    mainLyricCenter(mainTextHeight.intValue, linePadding, motion.activation(index))
                landscapeLyricDistance(center, layout.viewportStartOffset, layout.viewportEndOffset)
            } else {
                motion.distance(index)
            }
            playbackLyricDepth(distance, landscape = landscapeListState != null)
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
            .then(if (onSeek != null) Modifier.clickable(role = Role.Button) { onSeek(line.startTime) } else Modifier)
            .padding(vertical = LyricVerticalPadding),
    ) {
        Column(
            modifier = Modifier.width(maxWidth / LyricRowScaleLimit)
                .layout { measurable, constraints ->
                    val child = measurable.measure(constraints)
                    val height = constraints.constrainHeight((child.height * LyricRowScaleLimit).roundToInt())
                    layout(child.width, height) { child.placeRelative(0, 0) }
                }
                .graphicsLayer {
                    val motion = frame.value
                    val activation = motion.activation(index)
                    val scale = lerp(1f, CurrentLineScale, activation)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val mainModifier = Modifier.graphicsLayer {
                val activation = frame.value.activation(index)
                alpha = if (following) maxOf(depth.value.mainAlpha, activation) else lerp(0.78f, 1f, activation)
                val radius = if (blurDepth) depth.value.mainBlur.dp.toPx() else 0f
                renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
            }
            val measuredMainModifier = if (landscapeListState != null) {
                mainModifier.onSizeChanged { mainTextHeight.intValue = it.height }
            } else {
                mainModifier
            }
            PlaybackLyricText(
                line, sweeping, position, if (sweeping) Color.White else color, mainStyle, measuredMainModifier,
            ) { frame.value.activation(index) }
            if (secondary.isNotEmpty()) {
                Column(
                    modifier = Modifier.graphicsLayer {
                        val activation = frame.value.activation(index)
                        alpha = if (following) {
                            maxOf(depth.value.secondaryAlpha, PlaybackFocusedSecondaryAlpha * activation)
                        } else {
                            lerp(0.42f, PlaybackFocusedSecondaryAlpha, activation)
                        }
                        val radius = if (blurDepth) depth.value.secondaryBlur.dp.toPx() else 0f
                        renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    secondary.forEach { text ->
                        Text(text, style = secondaryStyle, color = color)
                    }
                }
            }
        }
    }
}

private fun mainLyricCenter(textHeight: Int, linePadding: Int, activation: Float): Float =
    linePadding + textHeight * lerp(1f, CurrentLineScale, activation) / 2f

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
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val playAction = if (isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    val canToggle = actions and (playAction or PlaybackState.ACTION_PLAY_PAUSE) != 0L
    val controls = controller?.transportControls
    Column(
        modifier = modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LyricsPlaybackProgress(position, duration, pendingSeek, progressColor, onSeek)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (compact) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val buttonModifier = if (compact) Modifier.weight(1f, fill = false) else Modifier
            val skipButtonSize = if (compact) 44.dp else 56.dp
            val skipIconSize = if (compact) 30.dp else 34.dp
            PlaybackControl(
                R.drawable.ic_skip_previous,
                R.string.action_previous,
                actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                modifier = buttonModifier,
                buttonSize = skipButtonSize,
                iconSize = skipIconSize,
            ) {
                controls.dispatch { skipToPrevious() }
            }
            PlaybackControl(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) R.string.lyrics_playback_pause else R.string.action_play,
                canToggle,
                modifier = buttonModifier,
                buttonSize = if (compact) 52.dp else 64.dp,
                iconSize = if (compact) 34.dp else 40.dp,
            ) {
                controls.dispatch { if (isPlaying) pause() else play() }
            }
            PlaybackControl(
                R.drawable.ic_skip_next,
                R.string.lyrics_playback_next,
                actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
                modifier = buttonModifier,
                buttonSize = skipButtonSize,
                iconSize = skipIconSize,
            ) {
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
                thumbColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
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
            Text(formatPlaybackTime(elapsedSecond * 1_000L), style = MiuixTheme.textStyles.body2, maxLines = 1)
            Text(formatPlaybackTime(duration), style = MiuixTheme.textStyles.body2, maxLines = 1)
        }
    }
}

@Composable
private fun PlaybackActions(
    showTranslation: Boolean,
    showRomanization: Boolean,
    onOpenSettings: () -> Unit,
    onOpenVolume: () -> Unit,
    onTranslationChange: (Boolean) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onLanguageMenuExpandedChange: (Boolean) -> Unit,
    buttonSize: Dp,
) {
    Row(Modifier.width(buttonSize * 3)) {
        IconButton(onClick = onOpenVolume, modifier = Modifier.weight(1f), minHeight = buttonSize) {
            Icon(
                MiuixIcons.VolumeUp,
                contentDescription = stringResource(R.string.lyrics_playback_volume),
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.weight(1f), minHeight = buttonSize) {
            Icon(
                MiuixIcons.MoreCircle,
                contentDescription = stringResource(R.string.settings_fullscreen_playback),
                modifier = Modifier.size(30.dp),
            )
        }
        PlaybackLanguageMenu(
            showTranslation = showTranslation,
            showRomanization = showRomanization,
            onTranslationChange = onTranslationChange,
            onRomanizationChange = onRomanizationChange,
            onExpandedChange = onLanguageMenuExpandedChange,
            buttonSize = buttonSize,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaybackLanguageMenu(
    showTranslation: Boolean,
    showRomanization: Boolean,
    onTranslationChange: (Boolean) -> Unit,
    onRomanizationChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    buttonSize: Dp,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = {
                expanded = !expanded
                onExpandedChange(expanded)
            },
            holdDownState = expanded,
            minWidth = buttonSize,
            minHeight = buttonSize,
        ) {
            Icon(
                imageVector = MiuixIcons.Translate,
                contentDescription = stringResource(R.string.lyric_text_display_mode_translation),
                modifier = Modifier.size(30.dp),
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = if (showTranslation || showRomanization) 1f else 0.65f),
            )
        }
        OverlayListPopup(
            show = expanded,
            enableWindowDim = false,
            renderInRootScaffold = false,
            onDismissRequest = {
                expanded = false
                onExpandedChange(false)
            },
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
    buttonSize: Dp = 56.dp,
    iconSize: Dp = 34.dp,
    onClick: () -> Unit,
) {
    val color = MiuixTheme.colorScheme.onSurface
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, minWidth = buttonSize, minHeight = buttonSize) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(description),
            modifier = Modifier.size(iconSize),
            tint = color.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}
