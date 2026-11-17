package com.example.islandlyrics.lyrics.source

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine

internal data class SuperLyricUpdate(
    val publisher: String?,
    val title: String,
    val artist: String,
    val documentId: String?,
    val lines: List<LyricLine>?,
    val currentLine: LyricLine?,
    val index: Int,
    val translation: String?,
    val roma: String?,
    val mode: String,
    val position: Long? = null,
    val speed: Float? = null,
    val playbackState: String? = null
) {
    val hasTimeline: Boolean get() = lines.orEmpty().any { it.startTime > 0 || it.endTime > it.startTime }
}

internal data class SuperLyricTrack(
    val packageName: String,
    val title: String,
    val artist: String,
    val mediaId: String = ""
)

internal object SuperLyricMapper {
    fun modern(view: LyricView, cachedLines: List<LyricLine>? = null): SuperLyricUpdate {
        fun map(value: com.hchen.superlyricapi.model.Line) = normalizeLine(
            value.text, value.start, value.end,
            value.words?.map { SyllableInfo(it.start, it.end, it.text) },
            value.translation, value.phonetic
        )
        val current = view.line?.let(::map)
        return SuperLyricUpdate(
            view.publisher, view.lyric.title, view.lyric.artist, view.lyric.id,
            cachedLines ?: view.lyric.lines.map(::map).sortedBy { it.startTime }, current, view.index,
            current?.translation, current?.roma, "v4", view.position, view.speed,
            view.state?.name
        )
    }

    @Suppress("DEPRECATION")
    fun legacy(publisher: String?, data: SuperLyricData, previous: SuperLyricUpdate? = null): SuperLyricUpdate {
        val compatible = previous?.takeIf {
            it.lines != null && (publisher.isNullOrBlank() || it.publisher == publisher) &&
                (data.lyricId.isNullOrBlank() || data.lyricId == it.documentId) &&
                (data.title.isNullOrBlank() || data.title.equals(it.title, true)) &&
                (data.artist.isNullOrBlank() || data.artist.equals(it.artist, true))
        }
        val full = data.allLyrics?.filterNotNull()?.map(::legacyLine)?.sortedBy { it.startTime }
            ?.takeIf { it.isNotEmpty() } ?: compatible?.lines
        val current = data.lyric?.let(::legacyLine)
            ?: if (data.hasCurrentLyricIndex()) full?.getOrNull(data.currentLyricIndex) else null
        return SuperLyricUpdate(
            publisher ?: compatible?.publisher, data.title ?: compatible?.title.orEmpty(),
            data.artist ?: compatible?.artist.orEmpty(), data.lyricId ?: compatible?.documentId,
            full, current, if (data.hasCurrentLyricIndex()) data.currentLyricIndex else -1,
            data.translation?.let(::legacyLine)?.text ?: current?.translation,
            data.secondary?.let(::legacyLine)?.text ?: current?.roma,
            if (full == null) "legacy-line" else "legacy-full",
            data.position.takeIf { data.hasPosition() }
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyLine(value: SuperLyricLine): LyricLine {
        val words = value.words?.filterNotNull()?.map { SyllableInfo(it.startTime, it.endTime, it.word) }
        val start = value.startTime.takeIf { it > 0 } ?: words?.firstOrNull()?.startTime ?: 0L
        val end = value.endTime.takeIf { it > 0 } ?: words?.lastOrNull()?.endTime ?: start
        return normalizeLine(value.text, start, end, words, value.translation, value.secondary)
    }

    private fun normalizeLine(
        text: String, start: Long, end: Long, words: List<SyllableInfo>?,
        translation: String?, roma: String?
    ): LyricLine {
        val validWords = words?.filter { it.startTime >= 0 && it.endTime >= it.startTime }
            ?.sortedBy { it.startTime }?.takeIf { it.isNotEmpty() }
        val safeStart = start.coerceAtLeast(0)
        return LyricLine(
            safeStart, end.coerceAtLeast(safeStart),
            text.ifBlank { validWords.orEmpty().joinToString("") { it.text } },
            validWords, translation?.takeIf { it.isNotBlank() }, roma?.takeIf { it.isNotBlank() }
        )
    }

    fun matches(update: SuperLyricUpdate, track: SuperLyricTrack): Boolean =
        (update.publisher.isNullOrBlank() || update.publisher == track.packageName) &&
            (update.title.isBlank() || track.title.isBlank() || update.title.equals(track.title, true)) &&
            (update.artist.isBlank() || track.artist.isBlank() || update.artist.equals(track.artist, true))

    fun lineKey(track: SuperLyricTrack, update: SuperLyricUpdate): List<Any?> = listOf(
        track, update.documentId, update.index, update.currentLine,
        update.translation, update.roma
    )
}

/** STATE frames reuse the converted document; a new FULL object refreshes its contents. */
internal class SuperLyricDocumentMapper {
    private var document: com.hchen.superlyricapi.model.Lyric? = null
    private var lines: List<LyricLine>? = null
    fun map(view: LyricView): SuperLyricUpdate {
        val update = SuperLyricMapper.modern(view, lines.takeIf { document === view.lyric })
        document = view.lyric
        lines = update.lines
        return update
    }
}

/** Invalidates callbacks, retries and queued work belonging to a closed connection. */
internal class SuperLyricSession {
    var generation = 0L
        private set
    var running = false
        private set

    fun begin(): Long { running = true; return ++generation }
    fun end() { running = false; generation++ }
    fun accepts(token: Long, observed: SuperLyricTrack?, current: SuperLyricTrack?): Boolean =
        running && token == generation && observed != null && observed == current
}
