@file:Suppress("DEPRECATION")

package com.example.islandlyrics.lyrics.source

import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SuperLyricTraceEntry(
    val id: Long,
    val timestamp: Long,
    val mode: String,
    val event: String,
    val raw: String,
    val decoded: String = "等待应用转换",
    val decision: String = "等待处理"
) {
    fun export(): String = """
        |SuperLyric #$id $event ($mode) timestamp=$timestamp
        |处理结果: $decision
        |
        |=== API 回调原始字段（SDK 已解码，应用尚未转换）===
        |$raw
        |
        |=== 应用转换结果 ===
        |$decoded
    """.trimMargin()
}

/** Opt-in, in-memory Debug capture. No API objects or unbounded lyric history are retained. */
internal object SuperLyricTrace {
    const val MAX_EVENTS = 20
    private val clients = mutableSetOf<Any>()
    private var nextId = 0L
    private val mutableEntries = MutableStateFlow<List<SuperLyricTraceEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    @Synchronized fun capture(client: Any, enabled: Boolean) {
        if (enabled && BuildConfig.DEBUG) clients.add(client) else clients.remove(client)
    }

    @Synchronized fun begin(mode: String, event: String, raw: () -> String): Long? {
        if (!BuildConfig.DEBUG || clients.isEmpty()) return null
        val id = ++nextId
        val text = runCatching(raw).getOrElse { "原始字段读取失败: ${it.javaClass.simpleName}: ${it.message}" }
        mutableEntries.value = (listOf(SuperLyricTraceEntry(id, System.currentTimeMillis(), mode, event, text)) +
            mutableEntries.value).take(MAX_EVENTS)
        return id
    }

    @Synchronized fun decoded(id: Long?, text: () -> String) {
        if (id == null || mutableEntries.value.none { it.id == id }) return
        val result = runCatching(text).getOrElse { "转换结果格式化失败: ${it.message}" }
        change(id) { it.copy(decoded = result) }
    }

    @Synchronized fun decision(id: Long?, text: String) = change(id) { it.copy(decision = text) }

    @Synchronized fun failed(id: Long?, error: Exception) {
        val text = "转换失败: ${error.javaClass.simpleName}: ${error.message}"
        change(id) { it.copy(decoded = text, decision = text) }
    }

    @Synchronized fun clear() { mutableEntries.value = emptyList() }

    private fun change(id: Long?, transform: (SuperLyricTraceEntry) -> SuperLyricTraceEntry) {
        if (id != null) mutableEntries.value = mutableEntries.value.map { if (it.id == id) transform(it) else it }
    }
}

/** Explicit fields preserve nulls, negative times and original row/word order before normalization. */
internal object SuperLyricTraceFormat {
    const val MAX_CHARS = 32_768

    fun modern(view: LyricView): String = dump {
        field("publisher", view.publisher)
        field("index", view.index)
        field("position", view.position)
        field("speed", view.speed)
        field("state", view.state?.name)
        field("lyric.id", view.lyric.id)
        field("lyric.title", view.lyric.title)
        field("lyric.artist", view.lyric.artist)
        field("lyric.album", view.lyric.album)
        field("lyric.duration", view.lyric.duration)
        field("lyric.lines.count", view.lyric.lines.size)
        view.lyric.lines.forEachIndexed { i, row ->
            if (full) return@dump
            val path = "lyric.lines[$i]"
            field("$path.text", row.text)
            field("$path.start", row.start)
            field("$path.end", row.end)
            field("$path.translation", row.translation)
            field("$path.phonetic", row.phonetic)
            field("$path.words.count", row.words?.size)
            row.words?.forEachIndexed { j, word ->
                if (full) return@dump
                field("$path.words[$j]", "${word.text} @ ${word.start}..${word.end}")
            }
        }
    }

    fun legacy(publisher: String?, data: SuperLyricData?): String = dump {
        field("publisher", publisher)
        if (data == null) { field("data", null); return@dump }
        field("title", data.title)
        field("artist", data.artist)
        field("album", data.album)
        field("lyricId", data.lyricId)
        field("duration", data.duration)
        field("hasDuration", data.hasDuration())
        field("position", data.position)
        field("hasPosition", data.hasPosition())
        field("currentLyricIndex", data.currentLyricIndex)
        field("hasCurrentLyricIndex", data.hasCurrentLyricIndex())
        legacyLine("lyric", data.lyric)
        legacyLine("translation", data.translation)
        legacyLine("secondary", data.secondary)
        field("allLyrics.count", data.allLyrics?.size)
        data.allLyrics?.forEachIndexed { i, row ->
            if (full) return@dump
            legacyLine("allLyrics[$i]", row)
        }
        // Binary artwork is not useful for lyric decoding; report its presence and size.
        field("base64Icon.length", data.base64Icon?.length)
        data.extra?.let { extra ->
            extra.keySet().sorted().forEach { key ->
                if (full) return@dump
                field("extra[$key]", runCatching { extra.get(key) }.getOrElse { "<unreadable>" })
            }
        } ?: field("extra", null)
    }

    fun stop(publisher: String?, id: String?, reason: String?): String = dump {
        field("publisher", publisher)
        field("id", id)
        field("reason", reason)
    }

    fun decoded(update: SuperLyricUpdate): String = dump {
        field("mode", update.mode)
        field("publisher", update.publisher)
        field("title", update.title)
        field("artist", update.artist)
        field("documentId", update.documentId)
        field("hasTimeline", update.hasTimeline)
        field("index", update.index)
        field("position", update.position)
        field("speed", update.speed)
        field("playbackState", update.playbackState)
        field("translation", update.translation)
        field("roma", update.roma)
        line("currentLine", update.currentLine)
        field("lines.count", update.lines?.size)
        update.lines?.forEachIndexed { i, row ->
            if (full) return@dump
            line("lines[$i]", row)
        }
    }

    private inline fun dump(block: Dump.() -> Unit): String = Dump().apply(block).finish()

    private class Dump {
        private val text = StringBuilder()
        var full = false
            private set

        fun field(name: String, value: Any?) {
            if (full) return
            val rendered = if (value is String) {
                "\"${value.take(MAX_CHARS).replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\"", "\\\"")}\""
            } else value?.toString() ?: "null"
            val row = "$name = $rendered\n"
            val available = MAX_CHARS - text.length
            text.append(row.take(available))
            full = row.length > available || (value is String && value.length > MAX_CHARS)
        }

        fun legacyLine(path: String, row: SuperLyricLine?) {
            if (row == null) { field(path, null); return }
            field("$path.text", row.text)
            field("$path.startTime", row.startTime)
            field("$path.endTime", row.endTime)
            field("$path.translation", row.translation)
            field("$path.secondary", row.secondary)
            field("$path.words.count", row.words?.size)
            row.words?.forEachIndexed { i, word ->
                if (full) return
                field("$path.words[$i]", word?.let { "${it.word} @ ${it.startTime}..${it.endTime}" })
            }
        }

        fun line(path: String, row: LyricLine?) {
            if (row == null) { field(path, null); return }
            field("$path.text", row.text)
            field("$path.startTime", row.startTime)
            field("$path.endTime", row.endTime)
            field("$path.translation", row.translation)
            field("$path.roma", row.roma)
            field("$path.syllables.count", row.syllables?.size)
            row.syllables?.forEachIndexed { i, word ->
                if (full) return
                field("$path.syllables[$i]", "${word.text} @ ${word.startTime}..${word.endTime}")
            }
        }

        fun finish(): String = if (full) text.take(MAX_CHARS - 28).toString() + "\n[内容超过上限，已截断]\n" else text.toString()
    }
}
