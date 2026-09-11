package com.example.islandlyrics.lyrics.online.provider

import java.util.Locale

/** Canonicalization and validation for International Standard Recording Codes. */
internal object AppleMusicIsrc {
    private val pattern = Regex("^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$")

    /**
     * Accepts the compact ISRC form and the commonly displayed hyphenated form.
     * Raw values are intentionally not used as identity/cache keys.
     */
    fun normalize(value: String?): String? {
        val compact = value
            ?.trim()
            ?.replace(Regex("[\\s-]+"), "")
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return compact.takeIf { pattern.matches(it) }
    }
}
