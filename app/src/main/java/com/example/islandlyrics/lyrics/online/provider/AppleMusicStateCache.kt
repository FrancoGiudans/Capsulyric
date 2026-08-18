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

// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)
// (https://github.com/WXRIW/Lyricify-Lyrics-Helper)
// Copyright (C) WXRIW/Lyricify-Lyrics-Helper contributors
// Licensed under Apache-2.0

package com.example.islandlyrics.lyrics.online.provider

import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Base64

/**
 * Apple Music 全局状态缓存（静态单例）。
 *
 * 对应参考项目 C# `Api` 的静态字段：
 * - accessToken：匿名抓取的 Web JWT（header.kid=WebPlayKid / payload.iss=AMPWebPlay），过期自动重抓
 * - storefront / language：地区与语言，由设置写入（[setStorefrontCache]），默认 us / en-US
 *
 * 线程安全：@Volatile + Mutex。
 */
internal object AppleMusicStateCache {
    private const val STOREFRONT_DEFAULT = "cn"
    private const val LANGUAGE_DEFAULT = "zh-Hans"

    @Volatile
    var accessToken: String = ""
        private set

    @Volatile
    var storefront: String = STOREFRONT_DEFAULT
        private set

    @Volatile
    var language: String = LANGUAGE_DEFAULT
        private set

    /** Apple Music 网页登录态（media-user-token），由设置页导入；空表示匿名模式。 */
    @Volatile
    var mediaUserToken: String = ""
        private set

    /** 外部导入/清除 media-user-token；值变化时重置初始化，下次请求重新解析账号地区。 */
    fun setMediaUserToken(token: String?) {
        val trimmed = token?.trim().orEmpty()
        if (trimmed == mediaUserToken) return
        mediaUserToken = trimmed
        resetInit()
    }

    @Volatile
    private var inited = false

    private val mutex = Mutex()

    /** 外部注入/覆盖地区与语言（设置页调用），并标记已初始化。 */
    fun setStorefrontCache(storefront: String, language: String) {
        runBlocking {
            mutex.withLock {
                this@AppleMusicStateCache.storefront = storefront.ifBlank { STOREFRONT_DEFAULT }
                this@AppleMusicStateCache.language = language.ifBlank { LANGUAGE_DEFAULT }
                inited = true
            }
        }
    }

    /** 重置初始化状态（地区变化后下次请求重新初始化）。 */
    fun resetInit() {
        runBlocking {
            mutex.withLock {
                inited = false
            }
        }
    }

    /**
     * 确保已初始化（accessToken 有效、storefront/language 就绪）。
     * 匿名模式：只抓 Web JWT，不请求 /v1/me/storefront（无 Media User Token）。
     */
    suspend fun ensureInit(httpClient: OnlineLyricHttpClient) {
        if (inited && !isAccessTokenRefreshRequired(accessToken)) return

        mutex.withLock {
            if (inited && !isAccessTokenRefreshRequired(accessToken)) return@withLock
            runCatching {
                accessToken = fetchAccessToken(httpClient)
            }.onFailure { e ->
                accessToken = ""
                android.util.Log.w("AppleMusicStateCache", "fetch access token failed: ${e.message}")
            }
            // 已登录：地区从 Apple Music 账号自动解析（/v1/me/storefront），
            // 手动地区仅作匿名模式/解析失败的兜底
            if (mediaUserToken.isNotBlank()) {
                runCatching {
                    resolveStorefrontFromAccount(httpClient)
                }
            }
            inited = true
        }
    }

    private fun isAccessTokenRefreshRequired(token: String): Boolean {
        if (token.isBlank()) return true
        val payload = readJwtSegment(token, 1) ?: return true
        val exp = payload.optLong("exp", -1L)
        if (exp <= 0) return true
        return System.currentTimeMillis() >= exp * 1000 - 60_000
    }

    /** 从 browse 页的 index*.js 中抓取 Web JWT（打分：kid=WebPlayKid +100、iss=AMPWebPlay +100、root_https_origin +10）。 */
    private suspend fun fetchAccessToken(httpClient: OnlineLyricHttpClient): String {
        val html = httpClient.get(
            "https://music.apple.com/us/browse",
            headers = mapOf("User-Agent" to USER_AGENT)
        ) ?: throw IllegalStateException("AppleMusic: browse page fetch failed")

        val jsUrls = findIndexScriptUrls(html)
        if (jsUrls.isEmpty()) throw IllegalStateException("AppleMusic: Failed to find index*.js")

        for (jsUrl in jsUrls) {
            val js = httpClient.get(jsUrl, headers = mapOf("User-Agent" to USER_AGENT)) ?: continue
            val token = findAccessTokenInScript(js)
            if (!token.isNullOrBlank()) return token
        }
        throw IllegalStateException("AppleMusic: Failed to find access token")
    }

    /** 登录模式下用 media-user-token 从 /v1/me/storefront 解析账号地区。 */
    private suspend fun resolveStorefrontFromAccount(httpClient: OnlineLyricHttpClient) {
        val url = "https://amp-api.music.apple.com/v1/me/storefront?l=en-US"
        val response = httpClient.get(url, headers = accountHeaders()) ?: return
        val json = JSONObject(response)
        val data = json.optJSONArray("data")?.optJSONObject(0) ?: return
        val resolvedStorefront = data.optString("id", "")
        // /v1/me/storefront 的 id 可能是数字（如 143441），而 catalog URL 只接受两位地区码；
        // 非两位字母的 id 一律忽略，保留手动设置的地区
        if (!resolvedStorefront.matches(Regex("^[a-z]{2}$", RegexOption.IGNORE_CASE))) return
        storefront = resolvedStorefront.lowercase()
    }

    private fun accountHeaders(): Map<String, String> {
        val map = mutableMapOf(
            "Origin" to "https://music.apple.com",
            "Accept" to "application/json",
            "User-Agent" to USER_AGENT,
            "Accept-Language" to "$language,en;q=0.9"
        )
        if (accessToken.isNotBlank()) {
            map["Authorization"] = "Bearer $accessToken"
        }
        if (mediaUserToken.isNotBlank()) {
            map["media-user-token"] = mediaUserToken
        }
        return map
    }

    /**
     * 校验当前 media-user-token 是否有效（能否解析到账号地区）。
     * 返回 true=有效，false=被 Apple 明确拒绝（过期/无效），null=无法判定（网络/未登录/限流）。
     */
    suspend fun validateMediaUserToken(httpClient: OnlineLyricHttpClient): Boolean? {
        if (mediaUserToken.isBlank()) return null
        ensureInit(httpClient)
        if (accessToken.isBlank()) return null
        val response = httpClient.getDetailed(
            "https://amp-api.music.apple.com/v1/me/storefront?l=en-US",
            headers = accountHeaders()
        ) ?: return null
        return when (response.statusCode) {
            in 200..299 -> true
            401, 403 -> false
            else -> null
        }
    }

    private fun findIndexScriptUrls(html: String): List<String> {
        val primary = Regex(
            """(?:https://music\.apple\.com)?/?assets/index(?!-legacy)[^\"'<>\s]*?\.js""",
            setOf(RegexOption.IGNORE_CASE)
        ).findAll(html).map { normalizeAppleMusicAssetUrl(it.value) }.distinct().toList()
        if (primary.isNotEmpty()) return primary

        return Regex(
            """(?:https://music\.apple\.com)?/?assets/index[^\"'<>\s]*?\.js""",
            setOf(RegexOption.IGNORE_CASE)
        ).findAll(html).map { normalizeAppleMusicAssetUrl(it.value) }.distinct().toList()
    }

    private fun normalizeAppleMusicAssetUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") -> "https://music.apple.com$trimmed"
            else -> "https://music.apple.com/$trimmed"
        }
    }

    private fun findAccessTokenInScript(js: String): String? {
        val jwtRegex = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
        return jwtRegex.findAll(js)
            .map { it.value }
            .distinct()
            .map { token -> token to getAccessTokenScore(token) }
            .filter { it.second >= 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun getAccessTokenScore(token: String): Int {
        val header = readJwtSegment(token, 0) ?: return -1
        val payload = readJwtSegment(token, 1) ?: return -1
        val exp = payload.optLong("exp", -1L)
        if (exp <= 0 || System.currentTimeMillis() >= exp * 1000 - 60_000) return -1

        var score = 0
        if (header.optString("kid", "") == "WebPlayKid") score += 100
        if (payload.optString("iss", "") == "AMPWebPlay") score += 100
        if (payload.has("root_https_origin")) score += 10
        return score
    }

    private fun readJwtSegment(token: String, index: Int): JSONObject? {
        return runCatching {
            val parts = token.split('.')
            if (parts.size <= index) return null
            val json = String(Base64.getUrlDecoder().decode(parts[index]), Charsets.UTF_8)
            JSONObject(json)
        }.getOrNull()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
}
