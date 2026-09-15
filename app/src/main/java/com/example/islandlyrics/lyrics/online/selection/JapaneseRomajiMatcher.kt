/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of Capsulyric.
 *
 * Capsulyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.example.islandlyrics.lyrics.online.selection

import java.lang.reflect.Method
import java.text.Normalizer
import java.util.Locale

/**
 * Compares Japanese kana titles with their romanized forms.
 *
 * This deliberately stays a title-equivalence helper instead of being used as
 * a general language detector. Japanese kana can be converted deterministically;
 * kanji readings need the Android ICU transliterator and are only attempted when
 * the text also contains kana, which prevents ordinary Chinese titles from being
 * treated as Japanese by accident.
 */
internal object JapaneseRomajiMatcher {

    fun areEquivalent(first: String, second: String): Boolean {
        val left = normalizeText(first)
        val right = normalizeText(second)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true

        val japaneseSignal = containsKana(left) || containsKana(right)
        if (!japaneseSignal) return false

        val leftForms = romanizedForms(left)
        val rightForms = romanizedForms(right)
        return leftForms.any { it.isNotBlank() && it in rightForms }
    }

    private fun romanizedForms(value: String): Set<String> {
        val forms = linkedSetOf<String>()
        normalizeRomanization(value)?.let(forms::add)
        romanizeKana(value)?.let { normalizeRomanization(it) }?.let(forms::add)

        if (containsKanji(value) && containsKana(value)) {
            icuRomanize(value)?.let { normalizeRomanization(it) }?.let(forms::add)
        }
        return forms
    }

    private fun normalizeText(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        return buildString(normalized.length) {
            normalized.forEach { char ->
                val hiragana = toHiragana(char)
                when {
                    hiragana != null -> append(hiragana)
                    char.isLetterOrDigit() -> append(char)
                    char == '\'' || char == '’' || char == 'ʼ' -> append(' ')
                    else -> append(' ')
                }
            }
        }.trim().replace(Regex("\\s+"), " ")
    }

    private fun normalizeRomanization(value: String): String? {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val ascii = buildString(decomposed.length) {
            decomposed.forEach { char ->
                if (char.code in 0x41..0x5A || char.code in 0x61..0x7A || char.isDigit()) {
                    append(char.lowercaseChar())
                }
            }
        }
        if (ascii.isBlank()) return null

        var result = ascii
            .replace("ltsu", "tsu")
            .replace("xtsu", "tsu")
            .replace("sya", "sha")
            .replace("syu", "shu")
            .replace("syo", "sho")
            .replace("tya", "cha")
            .replace("tyu", "chu")
            .replace("tyo", "cho")
            .replace("zya", "ja")
            .replace("zyu", "ju")
            .replace("zyo", "jo")
            .replace("jya", "ja")
            .replace("jyu", "ju")
            .replace("jyo", "jo")
            .replace("si", "shi")
            .replace("ti", "chi")
            .replace("tu", "tsu")
            .replace("hu", "fu")
            .replace("zi", "ji")
            .replace("di", "ji")
            .replace("du", "zu")
            .replace("wo", "o")
            .replace("nn", "n")

        // A long vowel can be written with a macron, a prolonged vowel, or
        // the kana-style ou spelling. The kana converter omits the prolonged
        // mark, so fold these spellings to the same compact form.
        result = result
            .replace("ou", "o")
            .replace("oo", "o")
            .replace("aa", "a")
            .replace("ii", "i")
            .replace("uu", "u")
            .replace("ee", "e")
        return result
    }

    private fun romanizeKana(value: String): String? {
        val result = StringBuilder(value.length * 2)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char.isLetterOrDigit() && !isKana(char)) {
                result.append(char)
                index++
                continue
            }
            if (char == 'ー') {
                index++
                continue
            }
            if (!isKana(char)) return null

            if (char == 'っ') {
                val next = longestKanaReading(value, index + 1)
                if (next != null) {
                    next.value.firstOrNull { it in 'a'..'z' && it !in "aeiou" }
                        ?.let(result::append)
                }
                index++
                continue
            }

            val reading = longestKanaReading(value, index) ?: return null
            result.append(reading.value)
            index += reading.length
        }
        return result.toString()
    }

    private data class KanaReading(val length: Int, val value: String)

    private fun longestKanaReading(value: String, start: Int): KanaReading? {
        val maxLength = minOf(3, value.length - start)
        for (length in maxLength downTo 1) {
            val key = value.substring(start, start + length)
            kanaReadings[key]?.let { return KanaReading(length, it) }
        }
        return null
    }

    private fun toHiragana(char: Char): Char? {
        return when {
            char in '\u30A1'..'\u30F6' -> (char.code - 0x60).toChar()
            isKana(char) -> char
            else -> null
        }
    }

    private fun isKana(char: Char): Boolean =
        char in '\u3040'..'\u309F' ||
            char in '\u30A0'..'\u30FF'

    private fun containsKana(value: String): Boolean = value.any(::isKana)

    private fun containsKanji(value: String): Boolean = value.any { char ->
        char in '\u3400'..'\u4DBF' ||
            char in '\u4E00'..'\u9FFF' ||
            char in '\uF900'..'\uFAFF'
    }

    private data class IcuTransliterator(val instance: Any, val method: Method)

    private val icuTransliterator: IcuTransliterator? by lazy {
        val clazz = runCatching {
            Class.forName("android.icu.text.Transliterator")
        }.getOrNull() ?: return@lazy null
        val factory = runCatching {
            clazz.getMethod("getInstance", String::class.java)
        }.getOrNull() ?: return@lazy null
        val method = runCatching {
            clazz.getMethod("transliterate", String::class.java)
        }.getOrNull() ?: return@lazy null

        listOf("ja_Latn", "ja-Latn", "Any-Latin; Latin-ASCII")
            .firstNotNullOfOrNull { id ->
                runCatching {
                    val instance = factory.invoke(null, id) ?: error("ICU returned no transliterator")
                    IcuTransliterator(instance, method)
                }.getOrNull()
            }
    }

    private fun icuRomanize(value: String): String? {
        val transliterator = icuTransliterator ?: return null
        return runCatching {
            transliterator.method.invoke(transliterator.instance, value) as? String
        }.getOrNull()?.takeIf { converted ->
            converted != value && !containsKanji(converted)
        }
    }

    private val kanaReadings: Map<String, String> = buildMap {
        putAll(
            mapOf(
                "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
                "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
                "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
                "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
                "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
                "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
                "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
                "や" to "ya", "ゆ" to "yu", "よ" to "yo",
                "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
                "わ" to "wa", "ゐ" to "wi", "ゑ" to "we", "を" to "o", "ん" to "n",
                "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
                "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
                "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
                "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
                "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
                "ゔ" to "vu", "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o",
                "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo"
            )
        )
        putAll(
            mapOf(
                "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
                "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
                "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
                "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
                "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
                "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
                "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
                "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
                "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
                "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
                "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
                "いぇ" to "ye", "うぁ" to "wa", "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo",
                "くぁ" to "kwa", "くぃ" to "kwi", "くぇ" to "kwe", "くぉ" to "kwo",
                "ぐぁ" to "gwa", "ぐぃ" to "gwi", "ぐぇ" to "gwe", "ぐぉ" to "gwo",
                "しぇ" to "she", "じぇ" to "je", "ちぇ" to "che",
                "つぁ" to "tsa", "つぃ" to "tsi", "つぇ" to "tse", "つぉ" to "tso",
                "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu", "どぅ" to "du",
                "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "ふゅ" to "fyu",
                "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo", "ゔゅ" to "vyu"
            )
        )
    }
}
