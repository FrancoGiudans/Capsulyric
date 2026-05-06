package com.example.islandlyrics.data.lyric

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.data.ParserRuleHelper
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo

/**
 * Receives lyric data from Lyricon's active-player subscriber bridge.
 *
 * This source imports plain text, timed lines, word timing, translation, and
 * romanization from Lyricon's active player feed.
 */
class LyriconSource(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var subscriber: LyriconSubscriber? = null
    private var started = false
    private var subscribed = false
    private var activeProviderInfo: ProviderInfo? = null
    private var lastLyric = ""

    private val appNameCache = HashMap<String, String>()

    private val connectionListener = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            AppLogger.getInstance().i(TAG, "Lyricon connected")
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            AppLogger.getInstance().i(TAG, "Lyricon reconnected")
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            AppLogger.getInstance().d(TAG, "Lyricon disconnected")
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            AppLogger.getInstance().w(TAG, "Lyricon connection timed out")
        }
    }

    private val activePlayerListener = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            activeProviderInfo = providerInfo
            if (providerInfo == null) {
                lastLyric = ""
                AppLogger.getInstance().d(TAG, "No active Lyricon provider")
            } else {
                AppLogger.getInstance().d(
                    TAG,
                    "Active Lyricon provider: provider=${providerInfo.providerPackageName}, player=${providerInfo.playerPackageName}"
                )
            }
        }

        override fun onSongChanged(song: Song?) {
            if (song == null) {
                lastLyric = ""
                return
            }

            val pkg = resolvePackageName() ?: resolveFallbackPackageName() ?: run {
                AppLogger.getInstance().d(TAG, "onSongChanged: no active player package — skipped")
                return
            }
            val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
                ?: ParserRuleHelper.createDefaultRule(pkg)
            if (!rule.useLyriconApi) {
                AppLogger.getInstance().d(TAG, "[$pkg] Lyricon disabled by rule — skipped")
                return
            }
            if (!matchesCurrentSession(pkg, song)) return

            val parsedLines = convertSong(song)
            if (parsedLines.isEmpty()) {
                AppLogger.getInstance().d(TAG, "[$pkg] Lyricon song has no structured lyric lines")
                return
            }

            val sidecars = buildSidecars(
                song = song,
                receiveTranslation = rule.receiveLyriconTranslation,
                receiveRomanization = rule.receiveLyriconRomanization
            )
            val linesWithSidecars = if (sidecars.isEmpty()) {
                parsedLines
            } else {
                parsedLines.map { line ->
                    val values = sidecars[line.text]
                    line.copy(
                        translation = values?.first ?: line.translation,
                        roma = values?.second ?: line.roma
                    )
                }
            }

            val firstLine = parsedLines.firstOrNull { it.text.isNotBlank() } ?: return
            val firstSidecar = sidecars[firstLine.text]
            val appName = getAppName(pkg)
            val hasSyllable = parsedLines.any { !it.syllables.isNullOrEmpty() }

            mainHandler.post {
                LyricRepository.getInstance().updateLyric(
                    lyric = firstLine.text,
                    app = appName,
                    apiPath = "Lyricon",
                    translation = firstSidecar?.first,
                    roma = firstSidecar?.second
                )
                LyricRepository.getInstance().updateParsedLyrics(
                    lines = linesWithSidecars,
                    hasSyllable = hasSyllable,
                    sourceLabel = appName,
                    apiPath = "Lyricon"
                )
                AppLogger.getInstance().d(
                    TAG,
                    "Lyricon structured lyrics imported: ${parsedLines.size} lines, syllable=$hasSyllable"
                )
            }
        }

        override fun onReceiveText(text: String?) {
            val lyric = text.orEmpty().trim()
            if (lyric.isBlank() || lyric == lastLyric) return

            val pkg = resolvePackageName()
                ?: resolveFallbackPackageName()
                ?: LyricRepository.getInstance().liveMetadata.value?.packageName
                ?: run {
                    AppLogger.getInstance().d(TAG, "onReceiveText: no package — skipped")
                    return
                }
            val rule = ParserRuleHelper.getRuleForPackage(context, pkg)
                ?: ParserRuleHelper.createDefaultRule(pkg)
            if (!rule.useLyriconApi) {
                AppLogger.getInstance().d(TAG, "[$pkg] Lyricon disabled by rule — skipped")
                return
            }
            if (!matchesCurrentPackage(pkg)) return

            lastLyric = lyric
            mainHandler.post {
                LyricRepository.getInstance().updateLyric(lyric, getAppName(pkg), "Lyricon")
            }
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            LyricRepository.getInstance().updatePlaybackStatus(isPlaying)
        }

        override fun onPositionChanged(position: Long) {
            updateProgress(position)
        }

        override fun onSeekTo(position: Long) {
            updateProgress(position)
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }

    fun start() {
        if (started) return
        if (!ParserRuleHelper.hasEnabledLyriconRule(context)) {
            AppLogger.getInstance().log(TAG, "LyriconSource skipped — no enabled parser rule uses Lyricon")
            return
        }

        val created = try {
            LyriconFactory.createSubscriber(context.applicationContext)
        } catch (t: Throwable) {
            AppLogger.getInstance().e(TAG, "Failed to create Lyricon subscriber", t)
            return
        }

        subscriber = created
        created.addConnectionListener(connectionListener)
        subscribed = created.subscribeActivePlayer(activePlayerListener)
        if (!subscribed) {
            AppLogger.getInstance().w(TAG, "Lyricon active-player subscription was not accepted")
        }

        started = true
        try {
            created.register()
            AppLogger.getInstance().log(TAG, "LyriconSource started — subscriber registered")
        } catch (t: Throwable) {
            AppLogger.getInstance().e(TAG, "Failed to register Lyricon subscriber", t)
        }
    }

    fun stop() {
        val current = subscriber ?: return
        try {
            if (subscribed) {
                current.unsubscribeActivePlayer(activePlayerListener)
            }
            current.unregister()
            current.removeConnectionListener(connectionListener)
            current.destroy()
        } catch (t: Throwable) {
            AppLogger.getInstance().e(TAG, "Failed to stop Lyricon subscriber", t)
        } finally {
            subscriber = null
            started = false
            subscribed = false
            activeProviderInfo = null
            lastLyric = ""
            AppLogger.getInstance().log(TAG, "LyriconSource stopped")
        }
    }

    private fun convertSong(song: Song): List<OnlineLyricFetcher.LyricLine> {
        return song.lyrics.orEmpty()
            .mapNotNull(::convertLine)
            .sortedBy { it.startTime }
    }

    private fun convertLine(line: RichLyricLine): OnlineLyricFetcher.LyricLine? {
        val text = line.text.orEmpty().ifBlank {
            line.words.orEmpty().joinToString("") { it.text.orEmpty() }
        }.trim()
        if (text.isBlank()) return null

        val syllables = line.words.orEmpty()
            .mapNotNull { word ->
                val wordText = word.text.orEmpty()
                if (wordText.isBlank() || word.end <= word.begin) {
                    null
                } else {
                    OnlineLyricFetcher.SyllableInfo(
                        startTime = word.begin,
                        endTime = word.end,
                        text = wordText
                    )
                }
            }
            .takeIf { it.isNotEmpty() }

        val start = when {
            line.begin > 0L -> line.begin
            !syllables.isNullOrEmpty() -> syllables.first().startTime
            else -> 0L
        }
        val end = when {
            line.end > start -> line.end
            !syllables.isNullOrEmpty() -> syllables.last().endTime
            else -> start + line.duration.coerceAtLeast(DEFAULT_LINE_DURATION_MS)
        }

        return OnlineLyricFetcher.LyricLine(
            startTime = start,
            endTime = maxOf(end, start + 1L),
            text = text,
            syllables = syllables
        )
    }

    private fun buildSidecars(
        song: Song,
        receiveTranslation: Boolean,
        receiveRomanization: Boolean
    ): Map<String, Pair<String?, String?>> {
        if (!receiveTranslation && !receiveRomanization) return emptyMap()
        return song.lyrics.orEmpty()
            .mapNotNull { line ->
                val text = richText(line).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val translation = if (receiveTranslation) {
                    line.translation.orEmpty().ifBlank {
                        line.translationWords.orEmpty().joinToString("") { it.text.orEmpty() }
                    }.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                val roma = if (receiveRomanization) {
                    line.roma.orEmpty().ifBlank {
                        line.secondary.orEmpty().ifBlank {
                            line.secondaryWords.orEmpty().joinToString("") { it.text.orEmpty() }
                        }
                    }.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                if (translation == null && roma == null) null else text to (translation to roma)
            }
            .toMap()
    }

    private fun richText(line: RichLyricLine): String {
        return line.text.orEmpty().ifBlank {
            line.words.orEmpty().joinToString("") { it.text.orEmpty() }
        }.trim()
    }

    private fun matchesCurrentSession(pkg: String, song: Song): Boolean {
        if (!matchesCurrentPackage(pkg)) return false

        val liveMeta = LyricRepository.getInstance().liveMetadata.value ?: return true
        val songTitle = song.name.orEmpty()
        val songArtist = song.artist.orEmpty()
        val titleMatches = songTitle.isBlank() || liveMeta.title.isBlank() ||
                songTitle.equals(liveMeta.title, ignoreCase = true)
        val artistMatches = songArtist.isBlank() || liveMeta.artist.isBlank() ||
                songArtist.equals(liveMeta.artist, ignoreCase = true)

        if (!titleMatches || !artistMatches) {
            AppLogger.getInstance().d(
                TAG,
                "[$pkg] Lyricon song ignored — mismatch with current session (${liveMeta.title} - ${liveMeta.artist}) vs ($songTitle - $songArtist)"
            )
            return false
        }
        return true
    }

    private fun matchesCurrentPackage(pkg: String): Boolean {
        val livePkg = LyricRepository.getInstance().liveMetadata.value?.packageName
        if (!livePkg.isNullOrBlank() && pkg != livePkg) {
            AppLogger.getInstance().d(TAG, "[$pkg] Lyricon ignored — mismatch with current session [$livePkg]")
            return false
        }
        return true
    }

    private fun resolvePackageName(): String? {
        val livePkg = LyricRepository.getInstance().liveMetadata.value?.packageName
        val candidates = listOf(
            activeProviderInfo?.playerPackageName,
            activeProviderInfo?.providerPackageName,
            livePkg
        ).filterNotNull().filter { it.isNotBlank() }.distinct()

        return candidates.firstOrNull { pkg ->
            ParserRuleHelper.getRuleForPackage(context, pkg)?.useLyriconApi == true
        } ?: candidates.firstOrNull()
    }

    private fun resolveFallbackPackageName(): String? {
        val livePkg = LyricRepository.getInstance().liveMetadata.value?.packageName
        if (!livePkg.isNullOrBlank()) return livePkg

        return ParserRuleHelper.loadRules(context)
            .firstOrNull { it.enabled && it.useLyriconApi }
            ?.packageName
    }

    private fun updateProgress(position: Long) {
        val duration = LyricRepository.getInstance().liveMetadata.value?.duration ?: 0L
        LyricRepository.getInstance().updateProgress(position.coerceAtLeast(0L), duration)
    }

    private fun getAppName(pkg: String): String {
        appNameCache[pkg]?.let { return it }
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val name = pm.getApplicationLabel(info).toString()
            if (name.isNotEmpty()) appNameCache[pkg] = name
            name
        } catch (_: Exception) {
            pkg
        }
    }

    companion object {
        private const val TAG = "LyriconSource"
        private const val DEFAULT_LINE_DURATION_MS = 5_000L
    }
}
