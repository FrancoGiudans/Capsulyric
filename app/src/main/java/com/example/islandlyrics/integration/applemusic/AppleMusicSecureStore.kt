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

package com.example.islandlyrics.integration.applemusic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Apple Music media-user-token 安全存储。
 *
 * media-user-token 是用户 Apple Music 网页登录态的 Cookie，等同登录凭据，会随会话过期，
 * 因此与 Last.fm 凭据一样使用 Android Keystore 支持的 AES-GCM 加密存储，
 * 排除在 Android 备份/设备迁移与常规设置导出之外；仅可写入密码加密的敏感数据备份项。
 */
class AppleMusicSecureStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMediaUserToken(): String? = readEncrypted(KEY_MEDIA_USER_TOKEN)

    fun hasMediaUserToken(): Boolean = prefs.contains(KEY_MEDIA_USER_TOKEN)

    fun saveMediaUserToken(token: String) {
        prefs.edit { putEncrypted(this, KEY_MEDIA_USER_TOKEN, token.trim()) }
    }

    fun clearMediaUserToken() {
        prefs.edit { remove(KEY_MEDIA_USER_TOKEN) }
    }

    internal fun restoreFromBackup(token: String) {
        require(token.isNotBlank()) { "Apple Music media-user-token is required" }
        prefs.edit { putEncrypted(this, KEY_MEDIA_USER_TOKEN, token.trim()) }
    }

    private fun putEncrypted(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        value: String
    ) {
        if (value.isBlank()) {
            editor.remove(key)
        } else {
            editor.putString(key, encrypt(value))
        }
    }

    private fun readEncrypted(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrNull()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES)
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        private const val PREFS_NAME = "AppleMusicSecurePrefs"
        private const val KEY_ALIAS = "capsulyric_apple_music_media_user_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128

        private const val KEY_MEDIA_USER_TOKEN = "media_user_token"
    }
}
