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

package com.example.islandlyrics.lyrics.online.provider

import android.content.Context
import androidx.core.content.edit

/**
 * Musixmatch userToken 持久化。
 *
 * userToken 是公开的 API 会话令牌（非用户凭据），用 SharedPreferences 保存即可。
 * 跨进程复用 token 可以显著减少 `token.get` 调用频率，从而降低 Musixmatch
 * 反爬验证码（401 + hint:captcha）的触发概率。
 */
class MusixmatchTokenStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(token: String) {
        if (token.isBlank()) {
            clear()
        } else {
            prefs.edit { putString(KEY, token) }
        }
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val PREFS_NAME = "MusixmatchPrefs"
        const val KEY = "user_token"
    }
}
