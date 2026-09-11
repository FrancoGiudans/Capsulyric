package com.example.islandlyrics.lyrics.online.selection

/** Recording/version labels that should participate in identity matching. */
internal enum class VersionTag {
    LIVE,
    REMIX,
    COVER,
    INSTRUMENTAL,
    KARAOKE,
    ACOUSTIC,
    RADIO_EDIT
}

internal object VersionTagDetector {
    fun detect(title: String?, album: String?): Set<VersionTag> {
        val text = listOf(title.orEmpty(), album.orEmpty())
            .joinToString(" ")
            .lowercase()
        return buildSet {
            if (containsAny(text, "live", "现场", "现场版", "演唱会", "演唱會")) add(VersionTag.LIVE)
            if (containsAny(text, "remix", "混音", "重混", "リミックス")) add(VersionTag.REMIX)
            if (containsAny(text, "cover", "翻唱", "カバー")) add(VersionTag.COVER)
            if (containsAny(text, "instrumental", "纯音乐", "純音樂", "伴奏", "off-vocal", "off vocal", "offvocal", "オフボーカル")) add(VersionTag.INSTRUMENTAL)
            if (containsAny(text, "karaoke", "卡拉ok", "カラオケ")) add(VersionTag.KARAOKE)
            if (containsAny(text, "acoustic", "不插电", "不插電", "アコースティック")) add(VersionTag.ACOUSTIC)
            if (containsAny(text, "radio edit", "radio version", "电台版", "電台版")) add(VersionTag.RADIO_EDIT)
        }
    }

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any { text.contains(it) }
}
