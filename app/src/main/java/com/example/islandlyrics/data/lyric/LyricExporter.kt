package com.example.islandlyrics.data.lyric

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
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
        val exportDirectory = dirManager.resolveExportDirectory()
        val targetDirUri = exportDirectory.uri ?: return@withContext ExportResult(
            false,
            error = when (exportDirectory.status) {
                LocalLyricDirectoryManager.ExportDirectoryStatus.NONE_CONFIGURED -> "no_directory"
                LocalLyricDirectoryManager.ExportDirectoryStatus.NOT_WRITABLE -> "directory_not_writable"
                LocalLyricDirectoryManager.ExportDirectoryStatus.AVAILABLE -> "invalid_directory"
            }
        )
        val shouldSyncExportMatch = dirManager.isExportMatchSyncEnabled()

        val baseName = buildBaseFileName(metadata.artist, metadata.title)
        val fileName = "$baseName.lrc"
        val lrcContent = serializeToLrc(parsedInfo.lines, metadata)
        val exportMatch = if (shouldSyncExportMatch) {
            val rule = ParserRuleHelper.getRuleForPackage(context, metadata.packageName)
                ?: ParserRuleHelper.createDefaultRule(metadata.packageName)
            val currentSongState = OnlineLyricCacheStore(context).getCurrentSongState(
                mediaInfo = metadata,
                fallbackTitle = metadata.title,
                fallbackArtist = metadata.artist,
                useRawMetadata = rule.useRawMetadataForOnlineMatching
            )
            if (currentSongState.matchOverride != null) {
                currentSongState.effectiveTitle to currentSongState.effectiveArtist
            } else {
                null
            }
        } else {
            null
        }

        try {
            val targetDir = DocumentFile.fromTreeUri(context, targetDirUri)
                ?: return@withContext ExportResult(false, error = "invalid_directory")

            val newFile = writeTextFile(
                context = context,
                targetDir = targetDir,
                fileName = fileName,
                mimeType = "application/octet-stream",
                content = lrcContent
            )
            if (shouldSyncExportMatch && exportMatch != null) {
                dirManager.setCustomMatch(newFile.uri, exportMatch.first, exportMatch.second)
            }

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
        lines: List<OnlineLyricFetcher.LyricLine>,
        customMatch: Pair<String, String>? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext ExportResult(false, error = "empty_lyrics")

        val dirManager = LocalLyricDirectoryManager.getInstance(context)
        val exportDirectory = dirManager.resolveExportDirectory()
        val targetDirUri = exportDirectory.uri ?: return@withContext ExportResult(
            false,
            error = when (exportDirectory.status) {
                LocalLyricDirectoryManager.ExportDirectoryStatus.NONE_CONFIGURED -> "no_directory"
                LocalLyricDirectoryManager.ExportDirectoryStatus.NOT_WRITABLE -> "directory_not_writable"
                LocalLyricDirectoryManager.ExportDirectoryStatus.AVAILABLE -> "invalid_directory"
            }
        )
        val shouldSyncExportMatch = dirManager.isExportMatchSyncEnabled()

        val metadata = LyricRepository.MediaInfo(title, artist, "", 0L)
        val baseName = buildBaseFileName(artist, title)
        val fileName = "$baseName.lrc"
        val lrcContent = serializeToLrc(lines, metadata)

        try {
            val targetDir = DocumentFile.fromTreeUri(context, targetDirUri)
                ?: return@withContext ExportResult(false, error = "invalid_directory")

            val newFile = writeTextFile(
                context = context,
                targetDir = targetDir,
                fileName = fileName,
                mimeType = "application/octet-stream",
                content = lrcContent
            )
            if (shouldSyncExportMatch && customMatch != null) {
                dirManager.setCustomMatch(newFile.uri, customMatch.first, customMatch.second)
            }

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

    private fun buildBaseFileName(artist: String, title: String): String {
        return sanitizeFileName("$artist - $title")
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private fun writeTextFile(
        context: Context,
        targetDir: DocumentFile,
        fileName: String,
        mimeType: String,
        content: String
    ): DocumentFile {
        targetDir.findFile(fileName)?.delete()
        val newFile = targetDir.createFile(mimeType, fileName)
            ?: error("create_failed")
        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("write_failed")
        return newFile
    }

    private const val TAG = "LyricExporter"
}
