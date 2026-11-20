package com.example.islandlyrics.ui.overlay.floating

import android.os.SystemClock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.config.OverlayRenderDefaults
import com.example.islandlyrics.ui.overlay.display.AlbumArtColorExtractor
import com.example.islandlyrics.ui.overlay.model.UIState
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FloatingLyricsSettingsPreview(
    style: FloatingLyricsStyle,
    display: FloatingLyricsDisplayConfig,
    useMiuix: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val frame = rememberLiveFloatingLyricsFrame(display.wordHighlight)
    val context = LocalContext.current
    val previewLabel = stringResource(R.string.settings_floating_preview)
    val toggleLabel = stringResource(
        if (expanded) R.string.settings_floating_preview_collapse else R.string.settings_floating_preview_expand
    )
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { contentDescription = previewLabel }
            .clickable(onClickLabel = toggleLabel) { expanded = !expanded }
    ) {
        if (useMiuix) {
            val lyrics = remember(context, expanded) { FloatingLyricsContentView(context) }
            val colors = MiuixTheme.colorScheme
            val background = if (expanded || style.enableTextBackground) colors.surfaceContainer else colors.background
            val textColor = remember(style, frame.albumColor, background) {
                style.textColor(frame.albumColor, background.toArgb())
            }
            val line: @Composable (Modifier) -> Unit = { modifier ->
                AndroidView(
                    modifier = modifier,
                    factory = { lyrics },
                    update = { it.render(frame, style, display, textColor, frame.fullLyric) }
                )
            }
            if (expanded) {
                FloatingLyricsMiuixExpandedPanel(
                    title = frame.title,
                    artist = frame.artist,
                    artwork = frame.albumArt,
                    playing = frame.isPlaying,
                    blur = false,
                    lyrics = line
                )
            } else {
                FloatingLyricsMiuixCompactPanel(
                    artwork = frame.albumArt.takeIf { style.showAlbumArt },
                    background = style.enableTextBackground,
                    lyrics = line
                )
            }
        } else {
            key(expanded) {
                val renderer = remember(context) { FloatingLyricsRenderer(context) }
                val themeBackground = MaterialTheme.colorScheme.background.toArgb()
                val textColor = remember(renderer, style, frame.albumColor, themeBackground) {
                    renderer.materialPreviewTextColor(frame.albumColor, style, expanded, themeBackground)
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { renderer.createMaterialPreview(expanded) },
                    update = { renderer.bindMaterialPreview(frame, style, display, textColor) }
                )
            }
        }
    }
}

@Composable
private fun rememberLiveFloatingLyricsFrame(wordHighlight: Boolean): UIState {
    val repo = remember { LyricRepository.getInstance() }
    val metadata by repo.liveMetadata.observeAsState()
    val lyric by repo.liveLyric.observeAsState()
    val progress by repo.liveProgress.observeAsState()
    val parsed by repo.liveParsedLyrics.observeAsState()
    val artwork by repo.liveAlbumArt.observeAsState()
    val playing by repo.isPlaying.observeAsState(false)
    val session by FloatingLyricsVisibility.session.collectAsState()
    val hasPlayback = playing || session.playback == FloatingLyricsPlayback.PAUSED
    val cover = if (hasPlayback) artwork else null
    val defaultColor = OverlayRenderDefaults.COLOR_PRIMARY
    val albumColor by produceState(defaultColor, cover) {
        value = defaultColor
        val extractor = AlbumArtColorExtractor(defaultColor)
        extractor.extract(cover)
        value = extractor.currentColor
    }
    if (!hasPlayback) return UIState(fullLyric = stringResource(R.string.settings_floating_preview_idle))

    val timeline = remember(parsed) { FloatingLyricsTimeline.from(parsed) }
    val liveLine = remember(parsed, lyric) { floatingLiveLine(parsed, lyric) }
    val clock = session.clock
    val samplePosition = progress?.position.takeIf { clock == null }
    val highFps = LabFeatureManager.isFloatingWordHighFpsEnabled(LocalContext.current)
    val frameIntervalMs = if (highFps) 16L else 33L
    val position by key(timeline, clock, samplePosition, frameIntervalMs, wordHighlight, liveLine.takeIf { timeline == null }) {
        produceState(clock?.positionAt(SystemClock.elapsedRealtime()) ?: samplePosition) {
            if (clock == null || clock.speed <= 0f || clock.updatedAtMs <= 0L) return@produceState
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val currentPosition = clock.positionAt(now)
                value = currentPosition
                val frame = timeline?.presentationAt(currentPosition) ?: liveLine
                val untilNext = frame.nextLine?.let { clock.delayUntil(it.startTime, now) }
                val wordEnd = frame.currentLine?.syllables?.maxOfOrNull { it.endTime }
                val nextUpdate = when {
                    wordHighlight && wordEnd != null && currentPosition < wordEnd ->
                        minOf(untilNext ?: frameIntervalMs, frameIntervalMs)
                    else -> untilNext ?: break
                }
                delay(nextUpdate)
            }
        }
    }
    val presentation = timeline?.presentationAt(position) ?: liveLine
    val wordProgress = if (wordHighlight) position?.let { presentation.currentLine?.wordProgressAt(it) } else null
    return UIState(
        title = metadata?.title.orEmpty(),
        artist = metadata?.artist.orEmpty(),
        isPlaying = playing,
        albumArt = cover,
        albumColor = albumColor,
        fullLyric = stringResource(R.string.main_waiting_for_lyrics),
        lyricPresentation = presentation.copy(wordProgress = wordProgress)
    )
}
