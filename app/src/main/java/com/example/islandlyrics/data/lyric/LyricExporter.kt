package com.example.islandlyrics.data.lyric

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LyricExporter {

    data class ExportResult(
        val success: Boolean,
        val fileName: String? = null,
        val error: String? = null
    )

    suspend fun exportCurrentLyrics(context: Context): ExportResult = withContext(Dispatchers.IO) {
        val metadata = LyricRepository.getInstance().liveMetadata.value
            ?: return@withContext ExportResult(false, error = "no_metadata")

        val parsedInfo = LyricRepository.getInstance().liveParsedLyrics.value
            ?: return@withContext ExportResult(false, error = "no_lyrics")

        if (parsedInfo.lines.isEmpty()) {
            return@withContext ExportResult(false, error = "empty_lyrics")
        }

        val dirManager = LocalLyricDirectoryManager.getInstance(context)
        val targetDirUri = dirManager.getExportDirectoryUri()
            ?: return@withContext ExportResult(false, error = "no_directory")

        val fileName = buildFileName(metadata.artist, metadata.title)
        val lrcContent = serializeToLrc(parsedInfo.lines, metadata)

        try {
            val targetDir = DocumentFile.fromTreeUri(context, targetDirUri)
                ?: return@withContext ExportResult(false, error = "invalid_directory")

            val existing = targetDir.findFile(fileName)
            existing?.delete()

            val newFile = targetDir.createFile("application/octet-stream", fileName)
                ?: return@withContext ExportResult(false, error = "create_failed")

            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                out.write(lrcContent.toByteArray(Charsets.UTF_8))
            } ?: return@withContext ExportResult(false, error = "write_failed")

            dirManager.invalidateIndex()
            AppLogger.getInstance().i(TAG, "Exported: $fileName (${parsedInfo.lines.size} lines)")
            ExportResult(true, fileName = fileName)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Export failed: ${e.message}")
            ExportResult(false, error = e.message)
        }
    }

    suspend fun exportCacheEntry(
        context: Context,
        title: String,
        artist: String,
        lines: List<OnlineLyricFetcher.LyricLine>
    ): ExportResult = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext ExportResult(false, error = "empty_lyrics")

        val dirManager = LocalLyricDirectoryManager.getInstance(context)
        val targetDirUri = dirManager.getExportDirectoryUri()
            ?: return@withContext ExportResult(false, error = "no_directory")

        val metadata = LyricRepository.MediaInfo(title, artist, "", 0L)
        val fileName = buildFileName(artist, title)
        val lrcContent = serializeToLrc(lines, metadata)

        try {
            val targetDir = DocumentFile.fromTreeUri(context, targetDirUri)
                ?: return@withContext ExportResult(false, error = "invalid_directory")

            val existing = targetDir.findFile(fileName)
            existing?.delete()

            val newFile = targetDir.createFile("application/octet-stream", fileName)
                ?: return@withContext ExportResult(false, error = "create_failed")

            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                out.write(lrcContent.toByteArray(Charsets.UTF_8))
            } ?: return@withContext ExportResult(false, error = "write_failed")

            dirManager.invalidateIndex()
            AppLogger.getInstance().i(TAG, "Exported cache entry: $fileName")
            ExportResult(true, fileName = fileName)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Export cache entry failed: ${e.message}")
            ExportResult(false, error = e.message)
        }
    }

    fun serializeToLrc(
        lines: List<OnlineLyricFetcher.LyricLine>,
        metadata: LyricRepository.MediaInfo
    ): String {
        val sb = StringBuilder()
        sb.appendLine("[ti:${metadata.title}]")
        sb.appendLine("[ar:${metadata.artist}]")
        sb.appendLine("[by:IslandLyrics]")
        sb.appendLine("")

        for (line in lines) {
            if (line.text.isBlank()) continue
            val timestamp = formatTimestamp(line.startTime)
            sb.appendLine("$timestamp${line.text}")
            line.translation?.takeIf { it.isNotBlank() }?.let {
                sb.appendLine("$timestamp$it")
            }
        }

        return sb.toString()
    }

    private fun formatTimestamp(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val centis = (ms % 1000) / 10
        return "[%02d:%02d.%02d]".format(minutes, seconds, centis)
    }

    private fun buildFileName(artist: String, title: String): String {
        val sanitized = sanitizeFileName("$artist - $title")
        return "$sanitized.lrc"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private const val TAG = "LyricExporter"
}
