package com.example.islandlyrics.integration.lastfm

import android.net.Uri
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class LastFmApiClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val networkAllowed: () -> Boolean = { true }
) {
    fun authUrl(apiKey: String, token: String): String =
        "https://www.last.fm/api/auth/?api_key=${Uri.encode(apiKey)}&token=${Uri.encode(token)}"

    suspend fun getToken(credentials: LastFmCredentials): Result<String> {
        if (!credentials.hasApiCredentials) return Result.failure(IllegalStateException("Missing Last.fm API credentials"))
        return postSigned(
            credentials = credentials,
            params = mapOf("method" to "auth.getToken")
        ).mapCatching { json ->
            json.getString("token")
        }
    }

    suspend fun getSession(credentials: LastFmCredentials, token: String): Result<Session> {
        if (!credentials.hasApiCredentials) return Result.failure(IllegalStateException("Missing Last.fm API credentials"))
        return postSigned(
            credentials = credentials,
            params = mapOf(
                "method" to "auth.getSession",
                "token" to token
            )
        ).mapCatching { json ->
            val session = json.getJSONObject("session")
            Session(
                key = session.getString("key"),
                username = session.optString("name").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun updateNowPlaying(credentials: LastFmCredentials, track: LastFmTrack): Result<Unit> {
        if (!credentials.isConnected) return Result.failure(IllegalStateException("Last.fm is not connected"))
        return postSigned(
            credentials = credentials,
            params = track.toParams("track.updateNowPlaying", credentials.sessionKey.orEmpty())
        ).mapCatching { }
    }

    suspend fun scrobble(credentials: LastFmCredentials, track: LastFmTrack, startedAtSeconds: Long): Result<Unit> {
        if (!credentials.isConnected) return Result.failure(IllegalStateException("Last.fm is not connected"))
        return postSigned(
            credentials = credentials,
            params = track.toParams("track.scrobble", credentials.sessionKey.orEmpty()) +
                ("timestamp" to startedAtSeconds.toString())
        ).mapCatching { }
    }

    private suspend fun postSigned(
        credentials: LastFmCredentials,
        params: Map<String, String>
    ): Result<JSONObject> = suspendCancellableCoroutine { continuation ->
        if (!networkAllowed()) {
            continuation.resume(Result.failure(IllegalStateException("Network actions are blocked")))
            return@suspendCancellableCoroutine
        }
        val signedParams = params +
            ("api_key" to credentials.apiKey) +
            ("format" to "json") +
            ("api_sig" to sign(params + ("api_key" to credentials.apiKey), credentials.apiSecret))

        val body = FormBody.Builder().apply {
            signedParams.forEach { (key, value) -> add(key, value) }
        }.build()

        val request = Request.Builder()
            .url(API_URL)
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()

        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                continuation.resume(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val text = it.body.string()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!it.isSuccessful || json == null) {
                        continuation.resume(Result.failure(IllegalStateException("Last.fm HTTP ${it.code}")))
                        return
                    }
                    val error = json.optInt("error", 0)
                    if (error != 0) {
                        continuation.resume(Result.failure(IllegalStateException(json.optString("message", "Last.fm error $error"))))
                    } else {
                        continuation.resume(Result.success(json))
                    }
                }
            }
        })
    }

    private fun sign(params: Map<String, String>, secret: String): String {
        val raw = buildString {
            params.toSortedMap().forEach { (key, value) ->
                if (key != "format" && key != "callback") {
                    append(key)
                    append(value)
                }
            }
            append(secret)
        }
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.US, it) }
    }

    data class Session(
        val key: String,
        val username: String?
    )

    companion object {
        const val API_KEY_CREATE_URL = "https://www.last.fm/api/account/create"
        private const val API_URL = "https://ws.audioscrobbler.com/2.0/"
        private const val USER_AGENT = "Capsulyric/LastFmScrobbler"
    }
}

data class LastFmTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Long = 0L
) {
    fun isValid(): Boolean =
        title.isNotBlank() &&
            artist.isNotBlank() &&
            !title.equals("Unknown", ignoreCase = true) &&
            !artist.equals("Unknown", ignoreCase = true)

    fun toParams(method: String, sessionKey: String): Map<String, String> {
        return buildMap {
            put("method", method)
            put("sk", sessionKey)
            put("track", title)
            put("artist", artist)
            if (!album.isNullOrBlank()) put("album", album)
            if (durationSeconds > 0) put("duration", durationSeconds.toString())
        }
    }
}
