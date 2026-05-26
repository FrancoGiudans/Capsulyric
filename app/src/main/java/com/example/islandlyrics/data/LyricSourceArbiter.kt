package com.example.islandlyrics.data

import android.os.SystemClock

internal class LyricSourceArbiter {

    private data class ActiveSource(
        val source: SourceType,
        val updatedAtMs: Long
    )

    private enum class SourceGroup {
        INJECTED
    }

    private enum class SourceType(
        val lyricPriority: Int,
        val parsedPriority: Int,
        val staleFallbackMs: Long,
        val group: SourceGroup? = null
    ) {
        SYSTEM(
            lyricPriority = Int.MIN_VALUE,
            parsedPriority = Int.MIN_VALUE,
            staleFallbackMs = 0L
        ),
        UNKNOWN(
            lyricPriority = 0,
            parsedPriority = 0,
            staleFallbackMs = 0L
        ),
        ONLINE(
            lyricPriority = 1,
            parsedPriority = 2,
            staleFallbackMs = 0L
        ),
        LOCAL(
            lyricPriority = 2,
            parsedPriority = 3,
            staleFallbackMs = 0L
        ),
        LYRIC_GETTER(
            lyricPriority = 3,
            parsedPriority = 1,
            staleFallbackMs = 8_000L,
            group = SourceGroup.INJECTED
        ),
        LYRICON(
            lyricPriority = 3,
            parsedPriority = 1,
            staleFallbackMs = 8_000L,
            group = SourceGroup.INJECTED
        ),
        SUPER_LYRIC(
            lyricPriority = 3,
            parsedPriority = 1,
            staleFallbackMs = 8_000L,
            group = SourceGroup.INJECTED
        ),
        NOTIFICATION(
            lyricPriority = 4,
            parsedPriority = Int.MIN_VALUE,
            staleFallbackMs = 8_000L
        );

        val sourceKey: String
            get() = name
    }

    private var activeLyricSource: ActiveSource? = null
    private var activeParsedSource: ActiveSource? = null

    fun resetForTrack() {
        activeLyricSource = null
        activeParsedSource = null
    }

    fun chooseLyric(
        current: LyricRepository.LyricInfo?,
        candidate: LyricRepository.LyricInfo,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): LyricRepository.LyricInfo? {
        val source = sourceTypeOf(candidate.apiPath)
        if (source == SourceType.SYSTEM) {
            activeLyricSource = null
            return candidate
        }

        if (!shouldAcceptLyricCandidate(source, nowMs)) {
            return null
        }

        activeLyricSource = ActiveSource(source = source, updatedAtMs = nowMs)
        return candidate
    }

    fun chooseParsedLyrics(
        current: LyricRepository.ParsedLyricsInfo?,
        candidate: LyricRepository.ParsedLyricsInfo,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): LyricRepository.ParsedLyricsInfo? {
        if (candidate.lines.isEmpty()) {
            activeParsedSource = null
            return candidate
        }

        val source = sourceTypeOf(candidate.apiPath)
        val active = activeParsedSource

        if (active == null) {
            activeParsedSource = ActiveSource(source = source, updatedAtMs = nowMs)
            return candidate
        }

        if (source.sourceKey == active.source.sourceKey) {
            activeParsedSource = active.copy(updatedAtMs = nowMs)
            return candidate
        }

        if (source.parsedPriority > active.source.parsedPriority) {
            activeParsedSource = ActiveSource(source = source, updatedAtMs = nowMs)
            return candidate
        }

        if (source.parsedPriority == active.source.parsedPriority &&
            source.group != null &&
            source.group == active.source.group &&
            isBetterParsedCandidate(current, candidate)
        ) {
            activeParsedSource = ActiveSource(source = source, updatedAtMs = nowMs)
            return candidate
        }

        return null
    }

    private fun shouldAcceptLyricCandidate(
        candidate: SourceType,
        nowMs: Long
    ): Boolean {
        val active = activeLyricSource ?: return true

        if (candidate.sourceKey == active.source.sourceKey) {
            return true
        }

        if (candidate.lyricPriority > active.source.lyricPriority) {
            return true
        }

        if (candidate.lyricPriority == active.source.lyricPriority) {
            return candidate.group != null &&
                candidate.group == active.source.group &&
                nowMs - active.updatedAtMs >= SAME_GROUP_SWITCH_TIMEOUT_MS
        }

        return nowMs - active.updatedAtMs >= active.source.staleFallbackMs
    }

    private fun isBetterParsedCandidate(
        current: LyricRepository.ParsedLyricsInfo?,
        candidate: LyricRepository.ParsedLyricsInfo
    ): Boolean {
        if (current == null) return true
        if (candidate.timelineCapability != current.timelineCapability) {
            return candidate.timelineCapability.ordinal > current.timelineCapability.ordinal
        }
        if (candidate.hasSyllable && !current.hasSyllable) return true
        return candidate.lines.size > current.lines.size
    }

    private fun sourceTypeOf(apiPath: String?): SourceType {
        return when (apiPath?.trim()) {
            "Notification" -> SourceType.NOTIFICATION
            "SuperLyric" -> SourceType.SUPER_LYRIC
            "Lyricon" -> SourceType.LYRICON
            "Lyric Getter" -> SourceType.LYRIC_GETTER
            "Local LRC" -> SourceType.LOCAL
            "Online API", "Online Cache" -> SourceType.ONLINE
            "System" -> SourceType.SYSTEM
            else -> SourceType.UNKNOWN
        }
    }

    private companion object {
        private const val SAME_GROUP_SWITCH_TIMEOUT_MS = 4_000L
    }
}
