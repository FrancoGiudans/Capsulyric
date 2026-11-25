package com.example.islandlyrics.lyrics.online.selection

/** Recording/version labels that should participate in identity matching. */
internal enum class VersionTag {
    THE_FIRST_TAKE,
    LIVE,
    REMIX,
    COVER,
    INSTRUMENTAL,
    KARAOKE,
    ACOUSTIC,
    RADIO_EDIT
}

internal object VersionTagDetector {

    private val THE_FIRST_TAKE_REGEX = Regex("""(?<![a-z0-9])(?:from\s+)?the\s+first\s+take(?![a-z0-9])""")
    private val LIVE_REGEX = Regex("""(?<![a-z0-9])live(?![a-z0-9])""")
    private val REMIX_REGEX = Regex("""(?<![a-z0-9])remix(?:ed)?(?![a-z0-9])""")
    private val COVER_REGEX = Regex("""(?<![a-z0-9])cover(?:ed)?(?![a-z0-9])""")
    private val INSTRUMENTAL_REGEX = Regex("""(?<![a-z0-9])(?:instrumental|off[\s-]?vocal)(?![a-z0-9])""")
    private val KARAOKE_REGEX = Regex("""(?<![a-z0-9])karaoke(?![a-z0-9])""")
    private val ACOUSTIC_REGEX = Regex("""(?<![a-z0-9])acoustic(?![a-z0-9])""")
    private val RADIO_EDIT_REGEX = Regex("""(?<![a-z0-9])radio\s+(?:edit|version)(?![a-z0-9])""")

    fun detect(title: String?, album: String?): Set<VersionTag> {
        val text = MatchNormalizer.normalizeText(
            listOf(title.orEmpty(), album.orEmpty()).joinToString(" ")
        )
        if (text.isBlank()) return emptySet()
        return buildSet {
            if (THE_FIRST_TAKE_REGEX.containsMatchIn(text)) {
                add(VersionTag.THE_FIRST_TAKE)
            }
            if (LIVE_REGEX.containsMatchIn(text) || containsAny(text, "现场", "现场版", "演唱会", "ライブ")) {
                add(VersionTag.LIVE)
            }
            if (REMIX_REGEX.containsMatchIn(text) || containsAny(text, "混音", "重混", "リミックス")) {
                add(VersionTag.REMIX)
            }
            if (COVER_REGEX.containsMatchIn(text) || containsAny(text, "翻唱", "カバー", "歌ってみた")) {
                add(VersionTag.COVER)
            }
            if (INSTRUMENTAL_REGEX.containsMatchIn(text) || containsAny(text, "纯音乐", "伴奏", "オフボーカル")) {
                add(VersionTag.INSTRUMENTAL)
            }
            if (KARAOKE_REGEX.containsMatchIn(text) || containsAny(text, "卡拉ok", "カラオケ")) {
                add(VersionTag.KARAOKE)
            }
            if (ACOUSTIC_REGEX.containsMatchIn(text) || containsAny(text, "不插电", "アコースティック")) {
                add(VersionTag.ACOUSTIC)
            }
            if (RADIO_EDIT_REGEX.containsMatchIn(text) || containsAny(text, "电台版")) {
                add(VersionTag.RADIO_EDIT)
            }
        }
    }

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any { text.contains(it) }
}
