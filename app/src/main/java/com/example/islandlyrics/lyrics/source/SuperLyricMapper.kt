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
    val playbackState: String? = null,
    val traceId: Long? = null
) {
    val hasFullTimeline: Boolean get() = lines.orEmpty().any { it.hasTiming() }
    val hasTimeline: Boolean get() = hasFullTimeline || currentLine?.hasTiming() == true

    fun timelineLines(): List<LyricLine> = when {
        hasFullTimeline -> lines.orEmpty().sortedBy { it.startTime }
        currentLine?.hasTiming() == true -> listOf(currentLine)
        else -> emptyList()
    }

    fun withSidecarPolicy(translation: Boolean, romanization: Boolean): SuperLyricUpdate {
        if (translation && romanization) return this
        fun filter(line: LyricLine) = line.copy(
            translation = line.translation.takeIf { translation },
            roma = line.roma.takeIf { romanization }
        )
        return copy(
            translation = this.translation.takeIf { translation },
            roma = roma.takeIf { romanization },
            currentLine = currentLine?.let(::filter),
            lines = lines?.map(::filter)
        )
    }
}

private fun LyricLine.hasTiming(): Boolean = startTime > 0 || endTime > startTime ||
    syllables.orEmpty().any { it.startTime > 0 || it.endTime > it.startTime }

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
        // Retain document order for currentLyricIndex; sort only when publishing a timeline.
        var full = data.allLyrics?.filterNotNull()?.map(::legacyLine)
            ?.takeIf { it.isNotEmpty() } ?: compatible?.lines
        val index = if (data.hasCurrentLyricIndex()) data.currentLyricIndex else -1
        var current = data.lyric?.let(::legacyLine) ?: full?.getOrNull(index)
        val translation = data.translation?.let(::legacyLine)?.text?.takeIf { it.isNotBlank() }
            ?: current?.translation
        val roma = data.secondary?.let(::legacyLine)?.text?.takeIf { it.isNotBlank() }
            ?: current?.roma
        current = current?.copy(translation = translation, roma = roma)
        if (full != null && current != null) {
            val document = full
            val activeLine = current
            val rowIndex = index.takeIf { document.getOrNull(it)?.sameLine(activeLine) == true }
                ?: document.indexOfFirst { it.sameLine(activeLine) }.takeIf { it >= 0 }
            if (rowIndex != null && document[rowIndex] != activeLine) {
                full = document.toMutableList().apply { this[rowIndex] = activeLine }
            }
        }
        return SuperLyricUpdate(
            publisher ?: compatible?.publisher, data.title ?: compatible?.title.orEmpty(),
            data.artist ?: compatible?.artist.orEmpty(), data.lyricId ?: compatible?.documentId,
            full, current, index, translation, roma,
            if (full == null) "legacy-line" else "legacy-full",
            data.position.takeIf { data.hasPosition() }
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyLine(value: SuperLyricLine): LyricLine {
        val words = value.words?.filterNotNull()?.map { SyllableInfo(it.startTime, it.endTime, it.word) }
            ?.filter { it.startTime >= 0 && it.endTime >= it.startTime }?.sortedBy { it.startTime }
        val hasLineInterval = value.startTime >= 0 && value.endTime > value.startTime
        val start = value.startTime.takeIf { it > 0 || hasLineInterval } ?: words?.firstOrNull()?.startTime ?: 0L
        val end = value.endTime.takeIf { it > 0 && it >= start } ?: words?.lastOrNull()?.endTime?.coerceAtLeast(start) ?: start
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

    fun matchBasis(update: SuperLyricUpdate, track: SuperLyricTrack): String? {
        if (!update.publisher.isNullOrBlank() && update.publisher != track.packageName) return null
        // A rule can split a title suffix into the artist; an exact ID from the same player
        // still identifies the track. Unequal IDs may belong to different namespaces.
        if (!update.publisher.isNullOrBlank() && update.publisher == track.packageName &&
            !update.documentId.isNullOrBlank() && update.documentId == track.mediaId) return "mediaId"
        return if ((update.title.isBlank() || track.title.isBlank() || update.title.equals(track.title, true)) &&
            (update.artist.isBlank() || track.artist.isBlank() || update.artist.equals(track.artist, true))) "metadata" else null
    }

    fun matches(update: SuperLyricUpdate, track: SuperLyricTrack): Boolean = matchBasis(update, track) != null

    fun lineKey(track: SuperLyricTrack, update: SuperLyricUpdate): List<Any?> = listOf(
        track, update.documentId, update.index, update.currentLine,
        update.translation, update.roma
    )
}

private fun LyricLine.sameLine(other: LyricLine): Boolean =
    text == other.text && startTime == other.startTime && endTime == other.endTime

/** Called only after Source validates the current track; reset on track/connection/STOP. */
internal class SuperLyricLegacyLineState {
    private var previous: SuperLyricUpdate? = null

    fun reset() { previous = null }

    fun accept(update: SuperLyricUpdate): SuperLyricUpdate {
        if (update.mode != "legacy-line") { reset(); return update }
        val cached = previous?.takeIf {
            (update.publisher.isNullOrBlank() || it.publisher == update.publisher) &&
                (update.documentId.isNullOrBlank() || it.documentId == update.documentId)
        }
        if (cached == null) previous = null
        val incoming = update.currentLine
        val oldLine = cached?.currentLine
        val same = incoming != null && oldLine?.sameLine(incoming) == true
        val base = incoming ?: oldLine?.takeIf { update.translation != null || update.roma != null }
        if (base == null) return update
        val translation = update.translation ?: base.translation ?: oldLine?.translation?.takeIf { same }
        val roma = update.roma ?: base.roma ?: oldLine?.roma?.takeIf { same }
        val merged = update.copy(
            currentLine = base.copy(translation = translation, roma = roma),
            translation = translation, roma = roma
        )
        previous = merged
        return merged
    }
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
