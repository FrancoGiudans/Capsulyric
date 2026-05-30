package com.example.islandlyrics.feature.lyric

import android.content.Context
import com.example.islandlyrics.R
import com.example.islandlyrics.data.lyric.LyricExporter

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
