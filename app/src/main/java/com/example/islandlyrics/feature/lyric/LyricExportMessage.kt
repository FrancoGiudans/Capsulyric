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

package com.example.islandlyrics.feature.lyric

import android.content.Context
import com.example.islandlyrics.R
import com.example.islandlyrics.lyrics.export.LyricExporter

fun LyricExporter.ExportResult.toUserMessage(context: Context): String {
    return when {
        success -> context.getString(R.string.export_lyric_success, fileName ?: "")
        error == "no_directory" -> context.getString(R.string.export_lyric_no_directory)
        error == "directory_not_writable" ||
            error == "invalid_directory" ||
            error == "create_failed" ||
            error == "write_failed" -> context.getString(R.string.export_lyric_directory_not_writable)
        error == "no_lyrics" ||
            error == "no_metadata" ||
            error == "empty_lyrics" -> context.getString(R.string.export_lyric_no_lyrics)
        else -> context.getString(R.string.export_lyric_failed)
    }
}
