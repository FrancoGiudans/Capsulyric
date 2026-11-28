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

package com.example.islandlyrics.lyrics.source

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song

internal data class LyriconTrack(
    val packageName: String,
    val title: String,
    val artist: String,
    val rawTitle: String = title,
    val rawArtist: String = artist,
    val mediaId: String = ""
)

internal data class LyriconParsedText(
    val mainLyric: String,
    val translation: String?
)

internal object LyriconMapper {

    const val DEFAULT_LINE_DURATION_MS = 5_000L

    private data class IntermediateLine(
        val startTime: Long,
        val explicitEndTime: Long?,
        val text: String,
        val syllables: List<OnlineLyricFetcher.SyllableInfo>?,
        val translation: String?,
        val roma: String?,
        val alignment: OnlineLyricFetcher.LyricAlignment
    )

    fun convertSong(
        song: Song,
        receiveTranslation: Boolean,
        receiveRomanization: Boolean
    ): List<OnlineLyricFetcher.LyricLine> {
        val intermediates = song.lyrics.orEmpty()
            .mapNotNull { line -> convertLine(line, receiveTranslation, receiveRomanization) }
            .sortedBy { it.startTime }

        if (intermediates.isEmpty()) return emptyList()

        return intermediates.mapIndexed { index, current ->
            val resolvedEnd = current.explicitEndTime ?: run {
                val nextStart = intermediates.drop(index + 1)
                    .firstOrNull { it.startTime > current.startTime }
                    ?.startTime
                when {
                    nextStart != null -> nextStart
                    song.duration > current.startTime -> song.duration
                    else -> current.startTime + DEFAULT_LINE_DURATION_MS
                }
            }
            OnlineLyricFetcher.LyricLine(
                startTime = current.startTime,
                endTime = maxOf(resolvedEnd, current.startTime + 1L),
                text = current.text,
                syllables = current.syllables,
                translation = current.translation,
                roma = current.roma,
                alignment = current.alignment
            )
        }
    }

    private fun convertLine(
        line: RichLyricLine,
        receiveTranslation: Boolean,
        receiveRomanization: Boolean
    ): IntermediateLine? {
        val text = line.text.orEmpty().ifBlank {
            line.words.orEmpty().joinToString("") { it.text.orEmpty() }
        }.trim()
        if (text.isBlank()) return null

        val convertedWords = line.words.orEmpty()
            .mapNotNull { word ->
                val wordText = word.text.orEmpty()
                if (wordText.isEmpty()) return@mapNotNull null
                val wordBegin = word.begin.coerceAtLeast(0L)
                val wordEnd = when {
                    word.end > wordBegin -> word.end
                    word.duration > 0L -> wordBegin + word.duration
                    else -> wordBegin
                }
                if (wordEnd > wordBegin || wordText.isBlank()) {
                    OnlineLyricFetcher.SyllableInfo(
                        startTime = wordBegin,
                        endTime = wordEnd,
                        text = wordText
                    )
                } else {
                    null
                }
            }

        val syllables = convertedWords.takeIf { words ->
            words.any { it.text.isNotBlank() && it.endTime > it.startTime }
        }

        val timedWords = syllables.orEmpty().filter { it.endTime > it.startTime }
        val start = when {
            line.begin > 0L -> line.begin
            timedWords.isNotEmpty() -> timedWords.first().startTime
            else -> 0L
        }
        val explicitEnd = when {
            line.end > start -> line.end
            line.duration > 0L -> start + line.duration
            timedWords.isNotEmpty() && timedWords.last().endTime > start -> timedWords.last().endTime
            else -> null
        }

        val translation = if (receiveTranslation) {
            line.translation.orEmpty().ifBlank {
                line.translationWords.orEmpty().joinToString("") { it.text.orEmpty() }
            }.trim().takeIf { it.isNotBlank() }
        } else {
            null
        }

        val roma = if (receiveRomanization) {
            line.roma.orEmpty().ifBlank {
                line.secondary.orEmpty().ifBlank {
                    line.secondaryWords.orEmpty().joinToString("") { it.text.orEmpty() }
                }
            }.trim().takeIf { it.isNotBlank() }
        } else {
            null
        }

        val alignment = if (line.isAlignedRight) {
            OnlineLyricFetcher.LyricAlignment.RIGHT
        } else {
            OnlineLyricFetcher.LyricAlignment.UNSPECIFIED
        }

        return IntermediateLine(
            startTime = start,
            explicitEndTime = explicitEnd,
            text = text,
            syllables = syllables,
            translation = translation,
            roma = roma,
            alignment = alignment
        )
    }

    fun findActiveLine(
        lines: List<OnlineLyricFetcher.LyricLine>,
        positionMs: Long
    ): OnlineLyricFetcher.LyricLine? {
        if (positionMs < 0L || lines.isEmpty()) return null
        for (line in lines) {
            if (positionMs < line.startTime) break
            if (positionMs >= line.startTime && positionMs < line.endTime) {
                return line
            }
        }
        return null
    }

    fun parseReceiveText(
        rawText: String?,
        receiveTranslation: Boolean
    ): LyriconParsedText? {
        val parts = rawText.orEmpty()
            .split(LINE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        val mainLyric = parts[0]
        val translation = if (receiveTranslation && parts.size > 1) {
            parts.drop(1).joinToString(" ").takeIf { it.isNotBlank() }
        } else {
            null
        }
        return LyriconParsedText(mainLyric = mainLyric, translation = translation)
    }

    fun matchesSession(
        playerPkg: String,
        song: Song,
        track: LyriconTrack
    ): Boolean {
        if (playerPkg.isNotBlank() && track.packageName.isNotBlank() && playerPkg != track.packageName) {
            return false
        }
        if (!song.id.isNullOrBlank() && track.mediaId.isNotBlank() && song.id == track.mediaId) {
            return true
        }

        val titleMatches = matchesTitle(song.name.orEmpty(), track)
        val artistMatches = matchesArtist(song.artist.orEmpty(), track)
        return titleMatches && artistMatches
    }

    private fun matchesTitle(songTitle: String, track: LyriconTrack): Boolean {
        val normalizedSong = normalizeText(songTitle)
        if (normalizedSong.isEmpty()) return true
        val trackTitles = listOf(track.title, track.rawTitle)
            .map(::normalizeText)
            .filter { it.isNotEmpty() }
            .distinct()
        if (trackTitles.isEmpty()) return true
        return trackTitles.any { candidate ->
            normalizedSong == candidate ||
                (normalizedSong.length >= 2 && candidate.length >= 2 &&
                    (candidate.contains(normalizedSong) || normalizedSong.contains(candidate)))
        }
    }

    private fun matchesArtist(songArtist: String, track: LyriconTrack): Boolean {
        val normalizedSong = normalizeArtistText(songArtist)
        if (normalizedSong.isEmpty()) return true
        val trackArtists = listOf(track.artist, track.rawArtist)
            .map(::normalizeArtistText)
            .filter { it.isNotEmpty() }
            .distinct()
        if (trackArtists.isEmpty()) return true

        val songTokens = artistTokens(songArtist)
        return trackArtists.any { candidate ->
            normalizedSong == candidate ||
                (normalizedSong.length >= 2 && candidate.length >= 2 &&
                    (candidate.contains(normalizedSong) || normalizedSong.contains(candidate))) ||
                (songTokens.isNotEmpty() && artistTokens(candidate).any { it in songTokens })
        }
    }

    private fun normalizeText(input: String): String = input
        .trim()
        .lowercase()
        .replace('\u3000', ' ')
        .replace('（', '(')
        .replace('）', ')')
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun normalizeArtistText(input: String): String = normalizeText(input)
        .replace(ARTIST_FEAT_REGEX, " ")
        .replace(ARTIST_SEP_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun artistTokens(input: String): Set<String> = normalizeArtistText(input)
        .split(' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val LINE_SPLIT_REGEX = Regex("\\r?\\n")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val ARTIST_FEAT_REGEX = Regex("\\b(?:feat\\.?|ft\\.?|x)\\b", RegexOption.IGNORE_CASE)
    private val ARTIST_SEP_REGEX = Regex("[/、,&;]+")
}
