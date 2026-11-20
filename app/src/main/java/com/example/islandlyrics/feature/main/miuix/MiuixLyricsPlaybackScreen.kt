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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.feature.main.HomeLyricPreviewDisplay
import com.example.islandlyrics.feature.main.belongsTo
import com.example.islandlyrics.feature.main.rememberWhitelistedMediaSessions
import com.example.islandlyrics.feature.main.selectHomePlaybackController
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.miuix.blur.LocalMiuixBlurEnabled
import com.example.islandlyrics.ui.miuix.blur.MiuixBlurScaffold
import com.example.islandlyrics.ui.miuix.navigation.MiuixBackIcon
import com.example.islandlyrics.ui.navigation.LocalPageExpansionProgress
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsClock
import com.example.islandlyrics.ui.overlay.floating.FloatingLyricsVisibility
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver
import com.example.islandlyrics.ui.overlay.model.WordProgressCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private const val CurrentLineScale = 1.08f
private const val PlaybackFrameInterval = 33L
private const val SeekAcknowledgementTimeout = 4_000L
private const val SeekPositionTolerance = 1_000L

@Composable
internal fun MiuixLyricsPlaybackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { LyricRepository.getInstance() }
    val track by repo.liveMetadata.observeAsState()
    val parsed by repo.liveParsedLyrics.observeAsState()
    val lyric by repo.liveLyric.observeAsState()
    val albumArt by repo.liveTrackAlbumArt.observeAsState()
    val repoProgress by repo.liveProgress.observeAsState()
    val resolveState by repo.liveLyricResolveState.observeAsState(LyricRepository.LyricResolveState.PENDING)
    val session by FloatingLyricsVisibility.session.collectAsState()
    val controllers = rememberWhitelistedMediaSessions()
    val primaryToken = session.mediaSession
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
    val fallbackPosition = repoProgress?.position?.takeIf { track != null }
    val clock = remember(playback, fallbackPosition, matchedTrack) {
        playback.takeIf { matchedTrack != null }.toLyricsClock(fallbackPosition)
    }
    val lines = remember(parsed, track != null) { parsed.takeIf { track != null }.playbackLines() }
    val secondaryModes = remember(context) {
        HomeLyricPreviewDisplay.read(AppPreferences.of(context)).filter { it != SecondaryTextMode.NEXT_LYRIC }
    }
    val expansionProgress = LocalPageExpansionProgress.current
    val title = stringResource(R.string.main_now_playing_title)
    val playerPackage = track?.packageName
    val playerIntent = remember(context, playerPackage) {
        playerPackage?.let(context.packageManager::getLaunchIntentForPackage)
    }

    key(track?.packageName, track?.mediaId, track?.mediaUri, track?.rawTitle, track?.rawArtist) {
        val position = rememberLyricsPlaybackPosition(clock, duration)
        var following by remember { mutableStateOf(true) }
        var pendingSeek by remember { mutableStateOf<Long?>(null) }
        val supportsSeek = controller != null && duration > 0L && actions and PlaybackState.ACTION_SEEK_TO != 0L
        val seek: (Long) -> Unit = { requested ->
            if (supportsSeek && controller.metadata.belongsTo(matchedTrack)) {
                val target = requested.coerceIn(0L, duration)
                if (runCatching { controller.transportControls.seekTo(target) }.isSuccess) pendingSeek = target
            }
        }

        LaunchedEffect(pendingSeek) {
            val target = pendingSeek ?: return@LaunchedEffect
            val confirmed = withTimeoutOrNull(SeekAcknowledgementTimeout) {
                snapshotFlow { position.value }.first { it != null && abs(it - target) <= SeekPositionTolerance }
            }
            if (confirmed != null) following = true
            pendingSeek = null
        }

        MiuixBlurScaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { insets ->
            Box(Modifier.fillMaxSize()) {
                LyricsPlaybackBackground(cover)
                Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
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
                        modifier = Modifier.revealPlaybackChrome(expansionProgress),
                    )
                    Box(
                        Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                            alpha = ((expansionProgress() - 0.1f) / 0.65f).coerceIn(0f, 1f)
                        }
                    ) {
                        if (lines.isNotEmpty()) {
                            LyricsPlaybackList(
                                lines = lines,
                                secondaryModes = secondaryModes,
                                position = position,
                                following = following,
                                onFollowingChange = { following = it },
                                onSeek = seek.takeIf { supportsSeek },
                            )
                        } else {
                            val currentText = lyric?.lyric.takeIf {
                                track != null && !SecondaryTextResolver.isPlaceholder(it)
                            }
                            Text(
                                text = currentText ?: stringResource(
                                    when {
                                        track == null -> R.string.main_now_playing_subtitle_idle
                                        resolveState == LyricRepository.LyricResolveState.NOT_FOUND ->
                                            R.string.main_lyrics_not_found
                                        resolveState == LyricRepository.LyricResolveState.FOUND ->
                                            R.string.main_no_lyrics
                                        else -> R.string.main_waiting_for_lyrics
                                    }
                                ),
                                modifier = Modifier.align(Alignment.CenterStart),
                                style = if (currentText != null) {
                                    MiuixTheme.textStyles.title2
                                } else {
                                    MiuixTheme.textStyles.body1
                                },
                                color = MiuixTheme.colorScheme.onSurface,
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
                        onSeek = seek.takeIf { supportsSeek },
                        modifier = Modifier.revealPlaybackChrome(expansionProgress),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberLyricsPlaybackPosition(clock: FloatingLyricsClock?, duration: Long): State<Long?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<Long?>(null, clock, duration, lifecycle) {
        if (clock == null) {
            value = null
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                val position = clock.positionAt(SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                value = if (duration > 0) position.coerceAtMost(duration) else position
                if (clock.speed <= 0f || (duration > 0L && position >= duration)) break
                delay(PlaybackFrameInterval)
            } while (true)
        }
    }
}

private fun Modifier.revealPlaybackChrome(progress: () -> Float): Modifier = graphicsLayer {
    alpha = ((progress() - 0.42f) / 0.58f).coerceIn(0f, 1f)
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
    secondaryModes: List<SecondaryTextMode>,
    position: State<Long?>,
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    onSeek: ((Long) -> Unit)?,
) {
    val listState = rememberLazyListState()
    val timeline = remember(lines) { lines.withInterludes() }
    val currentIndex by remember(timeline, position) {
        derivedStateOf { position.value?.let(timeline::currentPlaybackLine) ?: -1 }
    }
    val scrollConnection = remember(onFollowingChange) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) onFollowingChange(false)
                return Offset.Zero
            }
        }
    }
    val blurEnabled = LocalMiuixBlurEnabled.current && isRenderEffectSupported()
    LaunchedEffect(currentIndex, timeline, following) {
        if (following) {
            val index = currentIndex.coerceAtLeast(0)
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (item != null) {
                listState.animateScrollBy(
                    item.offset.toFloat(),
                    animationSpec = folmeSpring(damping = 0.86f, response = 0.38f),
                )
            } else {
                listState.scrollToItem(index)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val anchor = maxHeight / 3f
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = anchor, bottom = maxHeight - anchor),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(timeline.size) { index ->
                val current = index == currentIndex
                when (val row = timeline[index]) {
                    is PlaybackTimelineItem.Verse -> {
                        val line = row.line
                        val secondary = remember(line, secondaryModes) {
                            SecondaryTextResolver.resolve(secondaryModes, line.translation, line.roma, nextLyric = null)
                        }
                        PlaybackLyricRow(
                            line = line,
                            secondary = secondary,
                            current = current,
                            index = index,
                            listState = listState,
                            following = following,
                            blurEnabled = blurEnabled,
                            position = position,
                            onSeek = onSeek,
                        )
                    }
                    is PlaybackTimelineItem.Interlude -> PlaybackInterlude(row, current, position)
                }
            }
        }
        if (!following) {
            TextButton(
                text = stringResource(R.string.lyrics_playback_follow),
                onClick = { onFollowingChange(true) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun PlaybackInterlude(
    interlude: PlaybackTimelineItem.Interlude,
    current: Boolean,
    position: State<Long?>,
) {
    val color = MiuixTheme.colorScheme.onSurface
    val description = stringResource(R.string.lyrics_playback_interlude)
    val intensity by animateFloatAsState(
        if (current) 1f else 0.55f,
        animationSpec = tween(220),
        label = "interlude intensity",
    )
    Canvas(
        modifier = Modifier.size(width = 58.dp, height = 38.dp)
            .semantics { contentDescription = description },
    ) {
        val elapsed = if (current) (position.value ?: interlude.startTime) - interlude.startTime else 0L
        val progress = (elapsed.toFloat() / (interlude.endTime - interlude.startTime)).coerceIn(0f, 1f)
        val radius = 3.8.dp.toPx()
        repeat(3) { index ->
            val dotProgress = (progress * 3f - index).coerceIn(0f, 1f)
            val pulse = sin(dotProgress * Math.PI).toFloat()
            drawCircle(
                color = color.copy(alpha = intensity * WordProgressCalculator.highlightAlpha(dotProgress)),
                radius = radius + pulse * 0.9.dp.toPx(),
                center = Offset(radius + index * 16.dp.toPx(), size.height / 2f),
            )
        }
    }
}

@Composable
private fun PlaybackLyricRow(
    line: LyricLine,
    secondary: String?,
    current: Boolean,
    index: Int,
    listState: LazyListState,
    following: Boolean,
    blurEnabled: Boolean,
    position: State<Long?>,
    onSeek: ((Long) -> Unit)?,
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
    val edgeLevel by remember(listState, index) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val row = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@derivedStateOf 0
            val band = (layout.viewportSize.height * 0.14f).coerceAtLeast(1f)
            val edgeDistance = minOf(
                row.offset + row.size / 2f - layout.viewportStartOffset,
                layout.viewportEndOffset - row.offset - row.size / 2f,
            )
            ((1f - edgeDistance / band).coerceIn(0f, 1f) * 4f).roundToInt()
        }
    }
    val edge = if (current) 0f else edgeLevel / 4f * if (following) 1f else 0.4f
    val coverColor = MiuixTheme.colorScheme.onSurface
    val progress = if (current && !line.syllables.isNullOrEmpty()) {
        position.value?.let { WordProgressCalculator.compute(line.text, line.startTime, line.endTime, line.syllables, it) }
    } else {
        null
    }
    val styledText = remember(line.text, progress, coverColor) {
        buildAnnotatedString { appendHighlightedLyric(line.text, progress, coverColor) }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
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
                    alpha = brightness * (1f - edge * 0.55f)
                }
                .then(if (blurEnabled && edge > 0f) Modifier.blur((6f * edge).dp, BlurredEdgeTreatment.Unbounded) else Modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(styledText, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.SemiBold, color = coverColor)
            if (secondary != null) {
                Text(secondary, style = MiuixTheme.textStyles.body2, color = coverColor)
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
    modifier: Modifier = Modifier,
) {
    var draggedProgress by remember(controller, duration) { mutableStateOf<Float?>(null) }
    val displayed = draggedProgress?.let { (it * duration).roundToLong() }
        ?: pendingSeek ?: position.value ?: 0L
    val fraction = if (duration > 0L) (displayed.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val playAction = if (isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    val canToggle = actions and (playAction or PlaybackState.ACTION_PLAY_PAUSE) != 0L
    val controls = controller?.transportControls
    val color = MiuixTheme.colorScheme.onSurface
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Slider(
            value = fraction,
            onValueChange = { if (onSeek != null) draggedProgress = it },
            onValueChangeFinished = {
                draggedProgress?.let { onSeek?.invoke((it * duration).roundToLong()) }
                draggedProgress = null
            },
            enabled = onSeek != null,
            height = 12.dp,
            modifier = Modifier.fillMaxWidth().semantics {
                setProgress { target ->
                    if (onSeek == null) {
                        false
                    } else {
                        onSeek((target.coerceIn(0f, 1f) * duration).roundToLong())
                        true
                    }
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(displayed), style = MiuixTheme.textStyles.body2, color = color)
            Text(formatPlaybackTime(duration), style = MiuixTheme.textStyles.body2, color = color)
        }
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
