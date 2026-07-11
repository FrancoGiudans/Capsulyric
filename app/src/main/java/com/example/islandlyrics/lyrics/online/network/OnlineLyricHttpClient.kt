/*
 *
 *  * Copyright (c) 2026 Franco Giudance
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
    private val client: OkHttpClient,
    private val networkAllowed: () -> Boolean = { true }
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String? {
        if (!networkAllowed()) return null
        return suspendCancellableCoroutine { continuation ->
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
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? {
        if (!networkAllowed()) return null
        return suspendCancellableCoroutine { continuation ->
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
    }

    fun postFormBlocking(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? {
        if (!networkAllowed()) return null
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
    ): String? {
        if (!networkAllowed()) return null
        return suspendCancellableCoroutine { continuation ->
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
}

