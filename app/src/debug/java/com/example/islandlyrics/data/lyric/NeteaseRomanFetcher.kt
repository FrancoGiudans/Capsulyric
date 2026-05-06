package com.example.islandlyrics.data.lyric

import com.example.islandlyrics.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class NeteaseRomanFetcher {
    data class Result(
        val queryTitle: String,
        val queryArtist: String,
        val matchedTitle: String,
        val matchedArtist: String,
        val songId: Long,
        val lyricEndpoint: String,
        val lyrics: String,
        val translatedLyrics: String,
        val romanLyrics: String,
        val yrcLyrics: String,
        val yTranslatedLyrics: String,
        val yRomanLyrics: String,
        val rawLyricResponsePreview: String,
        val rawLyricResponseLength: Int
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
            val searchResponse = executeNeteaseEapi(
                url = "https://interface.music.163.com/eapi/cloudsearch/pc",
                data = linkedMapOf(
                    "s" to keyword,
                    "type" to "1",
                    "limit" to "30",
                    "offset" to "0",
                    "total" to "true"
                )
            ) ?: executeGet("https://music.163.com/api/search/get?s=${keyword.encodeUrl()}&type=1&limit=10")
                ?: return@withContext null

            val songs = JSONObject(searchResponse)
                .optJSONObject("result")
                ?.optJSONArray("songs")
                ?: return@withContext null

            if (songs.length() == 0) return@withContext null

            val firstSong = songs.optJSONObject(0) ?: return@withContext null
            val songId = firstSong.optLong("id", 0L)
            if (songId == 0L) {
                AppLogger.getInstance().w("NeteaseRomanFetcher", "Netease search returned no song id for $keyword")
                return@withContext null
            }

            val matchedTitle = firstSong.optString("name", queryTitle)
            val matchedArtist = (firstSong.optJSONArray("ar") ?: firstSong.optJSONArray("artists"))
                ?.let { artists ->
                    buildString {
                        for (index in 0 until artists.length()) {
                            val artistName = artists.optJSONObject(index)?.optString("name").orEmpty()
                            if (artistName.isBlank()) continue
                            if (isNotEmpty()) append("/")
                            append(artistName)
                        }
                    }
                }
                .orEmpty()

            val lyricNewUrl = "https://interface3.music.163.com/eapi/song/lyric/v1"
            val lyricResponse = executeNeteaseEapi(
                url = lyricNewUrl,
                data = linkedMapOf(
                    "id" to songId.toString(),
                    "cp" to "false",
                    "lv" to "0",
                    "kv" to "0",
                    "tv" to "0",
                    "rv" to "0",
                    "yv" to "0",
                    "ytv" to "0",
                    "yrv" to "0",
                    "csrf_token" to ""
                )
            ) ?: executeGet("https://music.163.com/api/song/lyric?id=$songId&lv=-1&tv=-1&rv=-1&kv=-1&yv=-1")
                ?: return@withContext null

            val lyricJson = JSONObject(lyricResponse)
            val lyrics = lyricJson.optLyricText("lrc")
            val translatedLyrics = lyricJson.optLyricText("tlyric")
            val romanLyrics = lyricJson.optLyricText("romalrc")
            val yrcLyrics = lyricJson.optLyricText("yrc")
            val yTranslatedLyrics = lyricJson.optLyricText("ytlrc")
            val yRomanLyrics = lyricJson.optLyricText("yromalrc")

            if (lyrics.isBlank() &&
                translatedLyrics.isBlank() &&
                romanLyrics.isBlank() &&
                yrcLyrics.isBlank() &&
                yTranslatedLyrics.isBlank() &&
                yRomanLyrics.isBlank()
            ) {
                return@withContext null
            }

            Result(
                queryTitle = queryTitle,
                queryArtist = queryArtist,
                matchedTitle = matchedTitle,
                matchedArtist = matchedArtist,
                songId = songId,
                lyricEndpoint = lyricNewUrl,
                lyrics = lyrics,
                translatedLyrics = translatedLyrics,
                romanLyrics = romanLyrics,
                yrcLyrics = yrcLyrics,
                yTranslatedLyrics = yTranslatedLyrics,
                yRomanLyrics = yRomanLyrics,
                rawLyricResponsePreview = buildPreview(lyricResponse),
                rawLyricResponseLength = lyricResponse.length
            )
        } catch (t: Throwable) {
            AppLogger.getInstance().e("NeteaseRomanFetcher", "fetchRomanLyrics failed: ${t.message}")
            null
        }
    }

    private fun JSONObject.optLyricText(key: String): String {
        return optJSONObject(key)?.optString("lyric", "").orEmpty()
    }

    private fun executeNeteaseEapi(url: String, data: LinkedHashMap<String, String>): String? {
        val header = linkedMapOf(
            "__csrf" to "",
            "appver" to "8.0.0",
            "buildver" to (System.currentTimeMillis() / 1000L).toString(),
            "channel" to "",
            "deviceId" to "",
            "mobilename" to "",
            "resolution" to "1920x1080",
            "os" to "android",
            "osver" to "",
            "requestId" to "${System.currentTimeMillis()}_${(0..999).random().toString().padStart(4, '0')}",
            "versioncode" to "140",
            "MUSIC_U" to ""
        )
        val payload = LinkedHashMap(data)
        payload["header"] = JSONObject(header as Map<*, *>).toString()

        val requestBody = FormBody.Builder()
            .add("params", buildEapiParams(url, JSONObject(payload as Map<*, *>).toString()))
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", NETEASE_EAPI_USER_AGENT)
            .header("Cookie", header.entries.joinToString("; ") { "${it.key}=${it.value}" })
            .build()
        return client.newCall(request).execute().use { response -> response.bodyStringOrNull() }
    }

    private fun buildEapiParams(url: String, payloadJson: String): String {
        val path = url
            .replace("https://interface3.music.163.com/e", "/")
            .replace("https://interface.music.163.com/e", "/")
        val digest = md5Hex("nobody${path}use${payloadJson}md5forencrypt")
        val data = "${path}-36cd479b6b5-${payloadJson}-36cd479b6b5-${digest}"
        return aesEcbEncryptHex(data)
    }

    private fun aesEcbEncryptHex(value: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(NETEASE_EAPI_KEY.toByteArray(Charsets.US_ASCII), "AES"))
        return cipher.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", NETEASE_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response -> response.bodyStringOrNull() }
    }

    private fun String.encodeUrl(): String = URLEncoder.encode(this, "UTF-8")

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
        private const val NETEASE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private const val NETEASE_EAPI_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.64 HuaweiBrowser/10.0.3.311 Mobile Safari/537.36"
        private const val NETEASE_EAPI_KEY = "e82ckenh8dichen8"
        private const val MAX_PREVIEW_CHARS = 12000
    }
}
