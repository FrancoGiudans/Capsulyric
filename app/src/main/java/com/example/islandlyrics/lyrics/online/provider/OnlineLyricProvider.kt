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

enum class OnlineLyricProvider(
    val id: String,
    @param:androidx.annotation.StringRes val nameResId: Int
) {
    QQMusic("qq_music", com.example.islandlyrics.R.string.provider_qq_music),
    Kugou("kugou", com.example.islandlyrics.R.string.provider_kugou_music),
    SodaMusic("soda_music", com.example.islandlyrics.R.string.provider_soda_music),
    Lrclib("lrclib", com.example.islandlyrics.R.string.provider_lrclib),
    Netease("netease", com.example.islandlyrics.R.string.provider_netease_music),
    LrcApi("lrc_api", com.example.islandlyrics.R.string.provider_lrcapi);

    companion object {
        fun fromId(id: String?): OnlineLyricProvider? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }

        fun defaultOrder(): List<OnlineLyricProvider> = listOf(QQMusic, Netease, Kugou, SodaMusic, LrcApi, Lrclib)

        fun defaultIds(): List<String> = defaultOrder().map { it.id }

        fun defaultOrderForPackage(packageName: String?): List<OnlineLyricProvider> {
            val preferred = when (packageName) {
                "com.tencent.qqmusic" -> QQMusic
                "com.netease.cloudmusic" -> Netease
                "com.kugou.android" -> Kugou
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
    }

    fun displayName(context: android.content.Context): String = context.getString(nameResId)
}

