package com.example.islandlyrics.lyrics.online.crypto

import android.text.Html
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.text.RegexOption.DOT_MATCHES_ALL

internal object QqLyricPayloadDecoder {
    fun decodeDownloadPayload(payload: String): String {
        if (payload.isBlank()) return ""
        val decoded = runCatching {
            decryptQrcPayload(payload)
        }.getOrElse {
            if (payload.isLikelyLrc()) payload else return ""
        }
        return normalizeDownloadPayload(decoded)
    }

    fun extractTagContent(content: String, tagName: String): String? {
        val cdataRegex = Regex("""<$tagName[^>]*><!\[CDATA\[(.*?)]]></$tagName>""", setOf(DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        cdataRegex.find(content)?.let { return it.groupValues[1] }

        val textRegex = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", setOf(DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return textRegex.find(content)?.groupValues?.getOrNull(1)
    }

    private fun String.isLikelyLrc(): Boolean {
        return Regex("""(?m)^\[\d{1,2}:\d{2}(?:\.\d{1,3})?]""").containsMatchIn(this)
    }

    private fun normalizeDownloadPayload(payload: String): String {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("<?xml")) {
            return trimmed
        }

        val attrMatch = Regex("""LyricContent="([^"]*)"""").find(trimmed)
        if (attrMatch != null) {
            return Html.fromHtml(attrMatch.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
        }

        val lyricMatch = extractTagContent(trimmed, "Lyric_1")
            ?: extractTagContent(trimmed, "lyric")
        if (!lyricMatch.isNullOrBlank()) {
            return Html.fromHtml(lyricMatch, Html.FROM_HTML_MODE_LEGACY).toString()
        }

        return trimmed
    }

    private fun decryptQrcPayload(encryptedLyrics: String): String {
        val decrypted = QqQrcDecrypter.decryptToCompressedBytes(encryptedLyrics)
        val inflated = inflateQrcPayload(decrypted)
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = if (inflated.size >= utf8Bom.size && inflated.copyOfRange(0, utf8Bom.size).contentEquals(utf8Bom)) {
            inflated.copyOfRange(utf8Bom.size, inflated.size)
        } else {
            inflated
        }
        return String(content, Charsets.UTF_8)
    }

    private fun inflateQrcPayload(decrypted: ByteArray): ByteArray {
        val zlibOffset = findLikelyZlibOffset(decrypted)
        val candidates = buildList {
            add(decrypted)
            if (zlibOffset > 0) add(decrypted.copyOfRange(zlibOffset, decrypted.size))
        }.distinctBy { it.contentHashCode() }

        for (candidate in candidates) {
            runCatching {
                return InflaterInputStream(candidate.inputStream()).use { it.readBytes() }
            }
            runCatching {
                return inflateRawDeflate(candidate)
            }
        }
        error("QQ QRC inflate failed")
    }

    private fun findLikelyZlibOffset(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 1) {
            val first = bytes[index].toInt() and 0xFF
            val second = bytes[index + 1].toInt() and 0xFF
            if (first == 0x78 && second in listOf(0x01, 0x5E, 0x9C, 0xDA)) {
                return index
            }
        }
        return 0
    }

    private fun inflateRawDeflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        inflater.setInput(bytes)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    output.write(buffer, 0, count)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
        } finally {
            inflater.end()
        }
        val result = output.toByteArray()
        if (result.isEmpty()) error("empty output")
        return result
    }
}

