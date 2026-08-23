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
 *  *
 *  *
 */

package com.example.islandlyrics.feature.applemusic

import android.content.Context
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.islandlyrics.integration.applemusic.AppleMusicSecureStore
import com.example.islandlyrics.lyrics.online.network.OnlineLyricHttpClient
import com.example.islandlyrics.lyrics.online.provider.AppleMusicStateCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Apple Music WebView 登录助手。
 *
 * 在应用内 WebView 打开 Apple Music 网页，用户登录后从 WebView 的
 * cookie store（应用私有）读取 `media-user-token`，再交由 [AppleMusicSecureStore]
 * 加密保存。WebView 的 cookie 只属于本应用，不影响系统浏览器。
 */
internal object AppleMusicWebLoginHelper {

    private const val BROWSE_URL = "https://music.apple.com/us/browse"

    /** 创建用于登录的 WebView（启用 JS/DOM 存储与第三方 cookie）。 */
    fun createWebView(context: Context): WebView {
        return WebView(context).apply {
            // 不透明背景：避免在模糊/半透明窗口下重绘时闪烁
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                // 仅允许 http/https 在本 WebView 内继续加载；
                // 其它 scheme（intent://、itms-apps:// 等）一律拦截，避免唤起外部浏览器/应用。
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val scheme = request?.url?.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }
            }
            loadUrl(BROWSE_URL)
        }
    }

    /** 从 WebView cookie store 读取 media-user-token（含 HttpOnly cookie）。 */
    fun readMediaUserToken(): String? {
        val cookieManager = CookieManager.getInstance()
        val urls = listOf(
            "https://music.apple.com",
            "https://www.apple.com",
            "https://apple.com"
        )
        for (url in urls) {
            val cookies = cookieManager.getCookie(url) ?: continue
            if (cookies.isBlank()) continue
            for (part in cookies.split(";")) {
                val pair = part.trim()
                if (pair.startsWith("media-user-token=", ignoreCase = true)) {
                    return pair.substringAfter('=').trim().takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    /**
     * 校验已保存的 media-user-token 是否有效（能解析到账号地区）。
     * 返回 true=有效，false=被 Apple 明确拒绝（过期/无效），null=无法判定（网络/未登录/限流）。
     * 校验前先从安全存储载入 token，避免进程重启后内存缓存为空导致误报。
     */
    suspend fun validateMediaUserToken(context: Context): Boolean? = withContext(Dispatchers.IO) {
        val stored = AppleMusicSecureStore(context).getMediaUserToken()
        if (!stored.isNullOrBlank()) {
            AppleMusicStateCache.setMediaUserToken(stored)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        AppleMusicStateCache.validateMediaUserToken(OnlineLyricHttpClient(client))
    }
}
