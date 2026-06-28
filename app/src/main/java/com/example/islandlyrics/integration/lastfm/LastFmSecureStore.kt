package com.example.islandlyrics.integration.lastfm

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

class LastFmSecureStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCredentials(): LastFmCredentials {
        return LastFmCredentials(
            apiKey = readEncrypted(KEY_API_KEY).orEmpty(),
            apiSecret = readEncrypted(KEY_API_SECRET).orEmpty(),
            sessionKey = readEncrypted(KEY_SESSION_KEY),
            username = readEncrypted(KEY_USERNAME)
        )
    }

    fun saveApiCredentials(apiKey: String, apiSecret: String) {
        prefs.edit {
            putEncrypted(this, KEY_API_KEY, apiKey.trim())
            putEncrypted(this, KEY_API_SECRET, apiSecret.trim())
            remove(KEY_SESSION_KEY)
            remove(KEY_USERNAME)
        }
    }

    fun saveSession(sessionKey: String, username: String?) {
        prefs.edit {
            putEncrypted(this, KEY_SESSION_KEY, sessionKey.trim())
            putEncrypted(this, KEY_USERNAME, username.orEmpty().trim())
            remove(KEY_PENDING_TOKEN)
        }
    }

    fun savePendingToken(token: String) {
        prefs.edit { putEncrypted(this, KEY_PENDING_TOKEN, token.trim()) }
    }

    fun getPendingToken(): String? = readEncrypted(KEY_PENDING_TOKEN)

    fun clearSession() {
        prefs.edit {
            remove(KEY_SESSION_KEY)
            remove(KEY_USERNAME)
            remove(KEY_PENDING_TOKEN)
        }
    }

    fun clearAll() {
        prefs.edit { clear() }
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
        private const val PREFS_NAME = "LastFmSecurePrefs"
        private const val KEY_ALIAS = "capsulyric_lastfm_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128

        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_PENDING_TOKEN = "pending_token"
    }
}
