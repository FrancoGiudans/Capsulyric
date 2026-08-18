/*
 *
 *  * Copyright (c) 2026 Youximi
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

package com.example.islandlyrics.core.settings

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object SensitiveBackupVault {
    const val ZIP_ENTRY_NAME = "sensitive_data.json"
    const val MAX_ENVELOPE_BYTES = 256 * 1024

    private const val FORMAT = "capsulyric-sensitive-backup-v1"
    private const val KDF_NAME = "PBKDF2WithHmacSHA256"
    private const val CIPHER_NAME = "AES/GCM/NoPadding"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val MIN_PBKDF2_ITERATIONS = 100_000
    private const val MAX_PBKDF2_ITERATIONS = 2_000_000
    private const val AES_KEY_SIZE_BITS = 256
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val MAX_PLAINTEXT_BYTES = 128 * 1024

    private val itemIdPattern = Regex("[a-z0-9_]{1,64}")
    private val secureRandom = SecureRandom()

    data class DecryptedVault(
        val itemIds: Set<String>,
        val payload: JSONObject
    )

    fun encrypt(payload: JSONObject, itemIds: Set<String>, password: CharArray): String {
        require(password.isNotEmpty()) { "A sensitive backup password is required" }

        val canonicalItemIds = canonicalItemIds(itemIds)
        val plaintext = payload.toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Sensitive backup data is too large" }

        val salt = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_SIZE_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        try {
            val cipher = Cipher.getInstance(CIPHER_NAME)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_SIZE_BITS, iv)
            )
            cipher.updateAAD(additionalAuthenticatedData(PBKDF2_ITERATIONS, salt, iv, canonicalItemIds))
            val ciphertext = cipher.doFinal(plaintext)
            val itemIdArray = JSONArray()
            canonicalItemIds.forEach(itemIdArray::put)

            return JSONObject()
                .put("format", FORMAT)
                .put("item_ids", itemIdArray)
                .put(
                    "kdf",
                    JSONObject()
                        .put("name", KDF_NAME)
                        .put("iterations", PBKDF2_ITERATIONS)
                        .put("salt_b64", encodeBase64(salt))
                )
                .put(
                    "cipher",
                    JSONObject()
                        .put("name", CIPHER_NAME)
                        .put("iv_b64", encodeBase64(iv))
                        .put("ciphertext_b64", encodeBase64(ciphertext))
                )
                .toString(2)
        } finally {
            plaintext.fill(0)
            key.fill(0)
        }
    }

    fun decrypt(serialized: String, password: CharArray): DecryptedVault {
        require(password.isNotEmpty()) { "A sensitive backup password is required" }
        require(serialized.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES) {
            "Sensitive backup data is too large"
        }

        val header = parseHeader(serialized)
        val key = deriveKey(password, header.salt, header.iterations)
        try {
            val cipher = Cipher.getInstance(CIPHER_NAME)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_SIZE_BITS, header.iv)
            )
            cipher.updateAAD(
                additionalAuthenticatedData(
                    header.iterations,
                    header.salt,
                    header.iv,
                    header.itemIds
                )
            )
            val plaintext = try {
                cipher.doFinal(header.ciphertext)
            } catch (_: GeneralSecurityException) {
                throw SecurityException("Incorrect sensitive backup password or corrupted data")
            }
            try {
                require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Invalid sensitive backup data" }

                val payload = try {
                    JSONObject(String(plaintext, Charsets.UTF_8))
                } catch (_: Exception) {
                    throw IllegalArgumentException("Invalid sensitive backup data")
                }
                return DecryptedVault(header.itemIds.toSet(), payload)
            } finally {
                plaintext.fill(0)
            }
        } finally {
            key.fill(0)
        }
    }

    fun readItemIds(serialized: String): Set<String> = parseHeader(serialized).itemIds.toSet()

    private fun parseHeader(serialized: String): Header {
        val root = try {
            JSONObject(serialized)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid sensitive backup data")
        }
        require(root.optString("format") == FORMAT) { "Unsupported sensitive backup format" }

        val itemIds = parseItemIds(root.optJSONArray("item_ids"))
        val kdf = root.optJSONObject("kdf") ?: invalidData()
        require(kdf.optString("name") == KDF_NAME) { "Unsupported sensitive backup key derivation" }
        val iterations = kdf.optInt("iterations", -1)
        require(iterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
            "Invalid sensitive backup key derivation"
        }
        val salt = decodeBase64(kdf.optString("salt_b64"))
        require(salt.size == SALT_SIZE_BYTES) { "Invalid sensitive backup data" }

        val cipher = root.optJSONObject("cipher") ?: invalidData()
        require(cipher.optString("name") == CIPHER_NAME) { "Unsupported sensitive backup cipher" }
        val iv = decodeBase64(cipher.optString("iv_b64"))
        require(iv.size == IV_SIZE_BYTES) { "Invalid sensitive backup data" }
        val ciphertext = decodeBase64(cipher.optString("ciphertext_b64"))
        require(ciphertext.size > TAG_SIZE_BITS / 8) { "Invalid sensitive backup data" }

        return Header(itemIds, iterations, salt, iv, ciphertext)
    }

    private fun parseItemIds(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) invalidData()

        val itemIds = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val itemId = array.optString(index)
            if (!itemIdPattern.matches(itemId) || itemId in itemIds) invalidData()
            itemIds += itemId
        }
        return itemIds.sorted()
    }

    private fun canonicalItemIds(itemIds: Set<String>): List<String> {
        if (itemIds.isEmpty() || itemIds.any { !itemIdPattern.matches(it) }) invalidData()
        return itemIds.sorted()
    }

    private fun additionalAuthenticatedData(
        iterations: Int,
        salt: ByteArray,
        iv: ByteArray,
        itemIds: List<String>
    ): ByteArray {
        return buildString {
            append(FORMAT).append('\n')
            append(KDF_NAME).append('\n')
            append(iterations).append('\n')
            append(encodeBase64(salt)).append('\n')
            append(CIPHER_NAME).append('\n')
            append(encodeBase64(iv)).append('\n')
            itemIds.forEach { append(it).append('\n') }
        }.toByteArray(Charsets.UTF_8)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, AES_KEY_SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeBase64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decodeBase64(value: String): ByteArray {
        if (value.isEmpty()) invalidData()
        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            invalidData()
        }
    }

    private fun invalidData(): Nothing = throw IllegalArgumentException("Invalid sensitive backup data")

    private data class Header(
        val itemIds: List<String>,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )
}
