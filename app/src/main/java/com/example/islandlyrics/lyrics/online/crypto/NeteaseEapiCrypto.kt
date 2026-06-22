package com.example.islandlyrics.lyrics.online.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object NeteaseEapiCrypto {
    private const val EAPI_KEY = "e82ckenh8dichen8"

    fun buildParams(url: String, payloadJson: String): String {
        val path = url
            .replace("https://interface3.music.163.com/e", "/")
            .replace("https://interface.music.163.com/e", "/")
        val digest = md5Hex("nobody${path}use${payloadJson}md5forencrypt")
        val data = "${path}-36cd479b6b5-${payloadJson}-36cd479b6b5-${digest}"
        return aesEcbEncryptHex(data)
    }

    private fun aesEcbEncryptHex(value: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(EAPI_KEY.toByteArray(Charsets.US_ASCII), "AES"))
        return cipher.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

