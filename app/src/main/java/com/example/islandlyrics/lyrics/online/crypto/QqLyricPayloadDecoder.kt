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

package com.example.islandlyrics.lyrics.online.crypto

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
        return Regex("""(?m)^\[\d{1,2}:\d{2}(?:\.\d{1,3})?]""").containsMatchIn(this) ||
            // QQ 逐行/逐字形态：[startMs,durMs]text（无 mm:ss.xx 时间戳）
            Regex("""(?m)^\[\d+,\d+]""").containsMatchIn(this)
    }

    private fun normalizeDownloadPayload(payload: String): String {
        val trimmed = payload.trim()
        if (!trimmed.startsWith("<?xml")) {
            return trimmed
        }

        // 注意：不能用 Html.fromHtml 处理 LyricContent —— 它会把换行折叠成空格，
        // 使 QRC 变成单行 [ti:...][0,3946]... 形态，破坏逐行/逐字解析。
        // 这里只做实体解码，保留字面换行。
        val attrMatch = Regex("""LyricContent="([^"]*)"""").find(trimmed)
        if (attrMatch != null) {
            return decodeHtmlEntities(attrMatch.groupValues[1])
        }

        val lyricMatch = extractTagContent(trimmed, "Lyric_1")
            ?: extractTagContent(trimmed, "lyric")
        if (!lyricMatch.isNullOrBlank()) {
            return decodeHtmlEntities(lyricMatch)
        }

        return trimmed
    }

    /** 解码常见 HTML 实体，保留 \n/\r 换行（区别于 Html.fromHtml 的空格折叠）。 */
    private fun decodeHtmlEntities(input: String): String {
        var result = input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        result = Regex("""&#(\d+);""").replace(result) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        result = Regex("""&#[xX]([0-9a-fA-F]+);""").replace(result) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        return result
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

