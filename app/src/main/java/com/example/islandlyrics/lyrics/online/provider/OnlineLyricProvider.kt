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

package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.R

enum class OnlineLyricProvider(
    val id: String,
    @param:androidx.annotation.StringRes val nameResId: Int
) {
    QQMusic("qq_music", com.example.islandlyrics.R.string.provider_qq_music),
    Kugou("kugou", com.example.islandlyrics.R.string.provider_kugou_music),
    SodaMusic("soda_music", com.example.islandlyrics.R.string.provider_soda_music),
    Lrclib("lrclib", com.example.islandlyrics.R.string.provider_lrclib),
    Netease("netease", com.example.islandlyrics.R.string.provider_netease_music),
    LrcApi("lrc_api", com.example.islandlyrics.R.string.provider_lrcapi),
    AppleMusic("apple_music", com.example.islandlyrics.R.string.provider_apple_music),
    Musixmatch("musixmatch", com.example.islandlyrics.R.string.provider_musixmatch);

    val supportsTrackId: Boolean get() = this != LrcApi

    val trackIdLabelResId: Int get() = when (this) {
        QQMusic -> R.string.online_lyric_track_id_qq_label
        Kugou -> R.string.online_lyric_track_id_hash_label
        else -> R.string.online_lyric_track_id_label
    }

    val trackIdHintResId: Int get() = when (this) {
        QQMusic -> R.string.online_lyric_track_id_qq_hint
        Kugou -> R.string.online_lyric_track_id_hash_hint
        Lrclib -> R.string.online_lyric_track_id_lrclib_hint
        AppleMusic -> R.string.online_lyric_track_id_apple_hint
        Musixmatch -> R.string.online_lyric_track_id_musixmatch_hint
        else -> R.string.online_lyric_track_id_numeric_hint
    }

    /** Accept bare platform IDs only; do not turn a malformed ID into a keyword search. */
    fun normalizeTrackId(input: String): String? {
        val value = input.trim()
        return when (this) {
            LrcApi -> null
            Kugou -> value.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }?.uppercase()
            QQMusic -> value.takeIf {
                it.matches(Regex("[0-9A-Za-z]+")) && it.any { char -> char != '0' }
            }
            Netease -> value.takeIf {
                it.matches(Regex("[0-9]+")) && (it.toLongOrNull() ?: 0L) > 0L
            }
            else -> value.takeIf {
                it.matches(Regex("[0-9]+")) && it.any { char -> char != '0' }
            }
        }
    }

    companion object {
        fun fromId(id: String?): OnlineLyricProvider? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }

        fun defaultOrder(): List<OnlineLyricProvider> = listOf(
            QQMusic, Netease, Kugou, SodaMusic, LrcApi, Lrclib, AppleMusic, Musixmatch
        )

        fun defaultIds(): List<String> = defaultOrder().map { it.id }

        fun defaultOrderForPackage(packageName: String?): List<OnlineLyricProvider> {
            val preferred = when (packageName) {
                "com.tencent.qqmusic" -> QQMusic
                "com.netease.cloudmusic" -> Netease
                "com.kugou.android" -> Kugou
                "com.apple.android.music" -> AppleMusic
                else -> null
            }
            return preferred?.let { provider ->
                listOf(provider) + defaultOrder().filterNot { it == provider }
            } ?: defaultOrder()
        }

        fun defaultIdsForPackage(packageName: String?): List<String> =
            defaultOrderForPackage(packageName).map { it.id }

        fun normalizeOrder(ids: List<String>?): List<OnlineLyricProvider> {
            val resolved = ids.orEmpty()
                .mapNotNull(::fromId)
                .distinct()
                .toMutableList()

            for (provider in defaultOrder()) {
                if (provider !in resolved) {
                    resolved += provider
                }
            }
            return resolved
        }

        /** Keeps only ids that map to a known [OnlineLyricProvider]. */
        fun normalizeDisabledIds(ids: Set<String>?): Set<String> =
            ids.orEmpty().mapNotNull(::fromId).map { it.id }.toSet()
    }

    fun displayName(context: android.content.Context): String = context.getString(nameResId)
}

