package com.example.islandlyrics.ui.overlay.superisland.render

/** Short, nonempty values for the native template's required highlight row. */
internal object SuperIslandTemplate18PlaybackInfo {
    fun elapsed(positionMs: Long, durationMs: Long): String =
        format(if (durationMs > 0) positionMs.coerceIn(0, durationMs) else positionMs.coerceAtLeast(0))

    fun total(durationMs: Long): String =
        "/ " + if (durationMs > 0) format(durationMs) else "--:--"

    private fun format(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}
