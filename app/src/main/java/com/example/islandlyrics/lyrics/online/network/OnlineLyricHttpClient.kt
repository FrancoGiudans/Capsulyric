package com.example.islandlyrics.lyrics.online.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

internal class OnlineLyricHttpClient(
    private val client: OkHttpClient
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                continuation.resume(response.body.string())
            }
        })
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? = suspendCancellableCoroutine { continuation ->
        val requestBody = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder().url(url).post(requestBody).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                continuation.resume(response.body.string())
            }
        })
    }

    fun postFormBlocking(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? {
        val requestBody = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder().url(url).post(requestBody).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body.string()
            }
        }.getOrNull()
    }

    suspend fun postJsonString(
        url: String,
        bodyJson: String,
        headers: Map<String, String> = emptyMap()
    ): String? = suspendCancellableCoroutine { continuation ->
        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                continuation.resume(response.body.string())
            }
        })
    }
}

