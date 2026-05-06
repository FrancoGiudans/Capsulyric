package com.example.islandlyrics.data.lyric

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class QqRomanFetcher {
    data class Result(
        val queryTitle: String,
        val queryArtist: String,
        val matchedTitle: String,
        val matchedArtist: String,
        val songId: String,
        val songMid: String,
        val romanLyrics: String,
        val rawRomanPayloadPreview: String,
        val rawRomanPayloadLength: Int,
        val decryptedRomanPayload: String,
        val decryptedRomanPayloadHexPreview: String = "",
        val decryptError: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun fetchRomanLyrics(title: String, artist: String): Result? = withContext(Dispatchers.IO) {
        val queryTitle = title.trim()
        val queryArtist = artist.trim()
        if (queryTitle.isBlank()) return@withContext null

        try {
            val keyword = listOf(queryTitle, queryArtist).filter { it.isNotBlank() }.joinToString(" ")
            val searchUrl =
                "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&inCharset=utf8&outCharset=utf-8&data=" +
                    "{\"music.search.SearchCgiService\":{\"method\":\"DoSearchForQQMusicDesktop\",\"module\":\"music.search.SearchCgiService\",\"param\":{\"num_per_page\":10,\"page_num\":1,\"query\":\"${escapeJson(keyword)}\",\"search_type\":0}}}".encodeUrl()
            val searchResponse = executeGet(searchUrl, referer = "https://c.y.qq.com/") ?: return@withContext null

            val songs = org.json.JSONObject(searchResponse)
                .optJSONObject("music.search.SearchCgiService")
                ?.optJSONObject("data")
                ?.optJSONObject("body")
                ?.optJSONObject("song")
                ?.optJSONArray("list")
                ?: return@withContext null

            if (songs.length() == 0) return@withContext null

            val firstSong = songs.optJSONObject(0) ?: return@withContext null
            val songId = firstSong.optString("id", "")
            val songMid = firstSong.optString("mid", "")
            val matchedTitle = firstSong.optString("title", firstSong.optString("name", queryTitle))
            val matchedArtist = firstSong.optJSONArray("singer")
                ?.let { singers ->
                    buildString {
                        for (index in 0 until singers.length()) {
                            val singerName = singers.optJSONObject(index)?.optString("name").orEmpty()
                            if (singerName.isBlank()) continue
                            if (isNotEmpty()) append("/")
                            append(singerName)
                        }
                    }
                }
                .orEmpty()

            if (songId.isBlank()) {
                AppLogger.getInstance().w("QqRomanFetcher", "QQ search returned no song id for $keyword")
                return@withContext null
            }

            val rawDownloadResponse = executeLyricDownload(songId) ?: return@withContext null
            val cleanedResponse = rawDownloadResponse
                .replace("<!--", "")
                .replace("-->", "")
                .trim()
            val rawRomanPayload = extractTagContent(cleanedResponse, "contentroma").orEmpty()
            if (rawRomanPayload.isBlank()) {
                AppLogger.getInstance().i("QqRomanFetcher", "QQ returned no contentroma for $songId / $songMid")
                return@withContext null
            }

            val decryptAttempt = runCatching { decryptQrcPayload(rawRomanPayload) }
            val decryptError = decryptAttempt.exceptionOrNull()?.message
            if (decryptError != null) {
                AppLogger.getInstance().e("QqRomanFetcher", "Failed to decrypt QQ roman payload: $decryptError")
            }
            val decryptedRomanPayload = decryptAttempt.getOrNull()?.content.orEmpty()
            val decryptedRomanPayloadHexPreview = decryptAttempt.getOrNull()?.decryptedHexPreview.orEmpty()
            val romanLyrics = decryptedRomanPayload.takeIf { it.isNotBlank() }?.let(::normalizeRomanPayload).orEmpty()

            Result(
                queryTitle = queryTitle,
                queryArtist = queryArtist,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist,
                songId = songId,
                songMid = songMid,
                romanLyrics = romanLyrics,
                rawRomanPayloadPreview = buildPreview(rawRomanPayload),
                rawRomanPayloadLength = rawRomanPayload.length,
                decryptedRomanPayload = buildPreview(decryptedRomanPayload),
                decryptedRomanPayloadHexPreview = decryptedRomanPayloadHexPreview,
                decryptError = decryptError
            )
        } catch (t: Throwable) {
            AppLogger.getInstance().e("QqRomanFetcher", "fetchRomanLyrics failed: ${t.message}")
            null
        }
    }

    private fun normalizeRomanPayload(payload: String): String {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("<?xml")) {
            return trimmed
        }

        val attrMatch = Regex("""LyricContent="([^"]*)"""").find(trimmed)
        if (attrMatch != null) {
            return android.text.Html.fromHtml(attrMatch.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString()
        }

        val lyricMatch = extractTagContent(trimmed, "Lyric_1")
            ?: extractTagContent(trimmed, "lyric")
        if (!lyricMatch.isNullOrBlank()) {
            return android.text.Html.fromHtml(lyricMatch, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        }

        return trimmed
    }

    private fun executeLyricDownload(songId: String): String? {
        val body = FormBody.Builder()
            .add("version", "15")
            .add("miniversion", "82")
            .add("lrctype", "4")
            .add("musicid", songId)
            .build()
        val request = Request.Builder()
            .url("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg")
            .post(body)
            .header("Referer", "https://c.y.qq.com/")
            .header("User-Agent", QQ_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response -> response.bodyStringOrNull() }
    }

    private fun executeGet(url: String, referer: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("User-Agent", QQ_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response -> response.bodyStringOrNull() }
    }

    private data class DecryptResult(
        val content: String,
        val decryptedHexPreview: String
    )

    private fun decryptQrcPayload(encryptedLyrics: String): DecryptResult {
        val decrypted = QqQrcDecrypter.decryptToCompressedBytes(encryptedLyrics)
        val inflated = inflateQrcPayload(decrypted)
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = if (inflated.size >= utf8Bom.size && inflated.copyOfRange(0, utf8Bom.size).contentEquals(utf8Bom)) {
            inflated.copyOfRange(utf8Bom.size, inflated.size)
        } else {
            inflated
        }
        return DecryptResult(
            content = String(content, Charsets.UTF_8),
            decryptedHexPreview = decrypted.take(64).joinToString(" ") { "%02X".format(it) }
        )
    }

    private fun inflateQrcPayload(decrypted: ByteArray): ByteArray {
        val zlibOffset = findLikelyZlibOffset(decrypted)
        val candidates = buildList {
            add(decrypted)
            if (zlibOffset > 0) add(decrypted.copyOfRange(zlibOffset, decrypted.size))
        }.distinctBy { it.contentHashCode() }

        val errors = mutableListOf<String>()
        for (candidate in candidates) {
            runCatching {
                return InflaterInputStream(ByteArrayInputStream(candidate)).use { it.readBytes() }
            }.onFailure { errors += "zlib: ${it.message}" }

            runCatching {
                return inflateRawDeflate(candidate)
            }.onFailure { errors += "raw: ${it.message}" }
        }

        val head = decrypted.take(32).joinToString(" ") { "%02X".format(it) }
        error("QRC inflate failed; decrypted head=$head; ${errors.joinToString(" | ")}")
    }

    private fun findLikelyZlibOffset(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 1) {
            val first = bytes[index].toInt() and 0xFF
            val second = bytes[index + 1].toInt() and 0xFF
            if (first == 0x78 && second in listOf(0x01, 0x5E, 0x9C, 0xDA)) {
                return index
            }
        }
        return 0
    }

    private fun inflateRawDeflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        inflater.setInput(bytes)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    output.write(buffer, 0, count)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
        } finally {
            inflater.end()
        }
        val result = output.toByteArray()
        if (result.isEmpty()) error("empty output")
        return result
    }

    private fun extractTagContent(content: String, tagName: String): String? {
        val cdataRegex = Regex("""<$tagName[^>]*><!\[CDATA\[(.*?)]]></$tagName>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        cdataRegex.find(content)?.let { return it.groupValues[1] }

        val textRegex = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return textRegex.find(content)?.groupValues?.getOrNull(1)
    }

    private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")

    private fun escapeJson(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun buildPreview(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.length <= MAX_PREVIEW_CHARS) {
            trimmed
        } else {
            trimmed.take(MAX_PREVIEW_CHARS) + "\n\n... 已截断，原始长度 ${trimmed.length} 字符"
        }
    }

    private fun Response.bodyStringOrNull(): String? {
        if (!isSuccessful) return null
        return body.string()
    }

    companion object {
        private const val QQ_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private const val MAX_PREVIEW_CHARS = 12000
    }
}
