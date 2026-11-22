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

package com.example.islandlyrics.lyrics.source

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import com.hchen.superlyricapi.Receiver

/** Receives v4 documents or frozen legacy line callbacks; MediaSession owns playback. */
class SuperLyricSource(
    private val context: Context,
    private val onlineLyricSource: OnlineLyricSource
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = SuperLyricSession()
    private val legacyLineState = SuperLyricLegacyLineState()
    private var connection: SuperLyricConnection? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private var registered = false
    private var registrationResult = "NOT_REGISTERED"
    private var activeTrack: SuperLyricTrack? = null
    private var lastLineKey: List<Any?>? = null
    private var lastTimeline: Timeline? = null
    private var lastOnlineRequest: SuperLyricTrack? = null
    private var lastDocumentId: String? = null
    private val appNameCache = HashMap<String, String>()

    private data class Timeline(
        val track: SuperLyricTrack,
        val documentId: String?,
        val lines: List<LyricLine>,
        val capability: LyricRepository.TimelineCapability,
        val receiveTranslation: Boolean,
        val receiveRomanization: Boolean
    )

    private val connectionCheck: Runnable = object : Runnable {
        override fun run() {
            if (!session.running) return
            if (!ParserRuleHelper.hasEnabledSuperLyricRule(context)) {
                stop()
                return
            }
            if (registered && runCatching { connection?.isAlive() == true }.getOrDefault(false)) {
                mainHandler.postDelayed(this, HEALTH_CHECK_MS)
                return
            }
            connect()
        }
    }

    fun start(): Unit = onMain {
        if (session.running || !ParserRuleHelper.hasEnabledSuperLyricRule(context)) return@onMain
        session.begin()
        retryDelayMs = INITIAL_RETRY_MS
        resetTrack()
        connect()
    }

    fun stop(): Unit = onMain {
        session.end()
        mainHandler.removeCallbacks(connectionCheck)
        closeConnection()
        resetTrack()
    }

    /** Metadata can arrive after the module's synchronous registration callback. */
    fun onTrackChanged(): Unit = onMain {
        resetTrack()
        if (!session.running || !registered) return@onMain
        val update = connection?.latestUpdate ?: return@onMain
        // An anonymous legacy line cannot safely be replayed across a track change.
        if (update.title.isBlank() && update.artist.isBlank()) return@onMain
        val track = currentTrack() ?: return@onMain
        receive(update, session.generation, track)
    }

    private fun connect() {
        mainHandler.removeCallbacks(connectionCheck)
        closeConnection()
        legacyLineState.reset()
        val token = session.begin()
        val client = SuperLyricConnection()
        connection = client
        val result = try {
            client.open(
                onLyric = { update ->
                    val observed = currentTrack()
                    mainHandler.post { receive(update, token, observed) }
                },
                onStop = { publisher, id, reason, traceId ->
                    val observed = currentTrack()
                    mainHandler.post { receiveStop(publisher, id, reason, token, observed, traceId) }
                }
            )
        } catch (error: SecurityException) {
            AppLogger.getInstance().e(TAG, "SuperLyric registration rejected", error)
            Receiver.RegisterResult.REJECTED
        } catch (error: Exception) {
            AppLogger.getInstance().w(TAG, "SuperLyric registration unavailable: ${error.message}")
            Receiver.RegisterResult.SERVICE_UNAVAILABLE
        }
        registrationResult = result.name
        registered = result == Receiver.RegisterResult.OK
        publishDebug(client.latestUpdate, if (registered) null else "registration: ${result.name}")
        if (registered) {
            retryDelayMs = INITIAL_RETRY_MS
            AppLogger.getInstance().log(TAG, "SuperLyric registered (${client.mode})")
            mainHandler.postDelayed(connectionCheck, HEALTH_CHECK_MS)
        } else {
            closeConnection()
            if (result == Receiver.RegisterResult.SERVICE_UNAVAILABLE) {
                mainHandler.postDelayed(connectionCheck, retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
            } else {
                // A rules/service restart may retry an explicit rejection; do not poll it.
                session.end()
            }
        }
    }

    private fun closeConnection() {
        registered = false
        val old = connection
        connection = null
        try {
            old?.close()
        } catch (error: Exception) {
            AppLogger.getInstance().w(TAG, "SuperLyric unregister failed: ${error.message}")
        }
    }

    private fun receive(incoming: SuperLyricUpdate, token: Long, observed: SuperLyricTrack?) {
        var update = incoming
        val track = currentTrack()
        if (!registered || !session.accepts(token, observed, track) || track == null) {
            SuperLyricTrace.decision(update.traceId,
                "忽略：${if (!registered) "连接未注册" else if (track == null || observed == null) "缺少 MediaSession 曲目" else "连接代次或曲目已变化"}; current=$track; observed=$observed")
            return
        }
        val rule = ParserRuleHelper.getRuleForPackage(context, track.packageName)
            ?: ParserRuleHelper.createDefaultRule(track.packageName)
        if (!rule.enabled || !rule.useSuperLyricApi) {
            SuperLyricTrace.decision(update.traceId, "忽略：当前曲目规则未启用 SuperLyric; current=$track")
            publishDebug(update, "disabled by rule")
            return
        }
        val matchBasis = SuperLyricMapper.matchBasis(update, track)
        if (matchBasis == null) {
            SuperLyricTrace.decision(update.traceId, "忽略：发布者或曲目信息与 MediaSession 不匹配; current=$track")
            publishDebug(update, "mismatch with current session")
            return
        }
        if (activeTrack != track) {
            resetTrack()
            activeTrack = track
        }
        update = legacyLineState.accept(update).withSidecarPolicy(
            rule.receiveSuperLyricTranslation, rule.receiveSuperLyricRomanization
        )
        if (update != incoming) {
            SuperLyricTrace.decoded(update.traceId) { SuperLyricTraceFormat.decoded(update) }
        }
        lastDocumentId = update.documentId
        val repo = LyricRepository.getInstance()
        val appName = getAppName(track.packageName)
        val rows = update.timelineLines()
        var timelineResult = "无可用时间轴"
        if (rows.isNotEmpty()) {
            val timeline = Timeline(
                track, update.documentId, rows,
                if (update.hasFullTimeline) LyricRepository.TimelineCapability.MULTI_LINE
                else LyricRepository.TimelineCapability.ACTIVE_LINE_ONLY,
                rule.receiveSuperLyricTranslation, rule.receiveSuperLyricRomanization
            )
            if (timeline == lastTimeline) {
                timelineResult = "时间轴重复，跳过"
            } else if (repo.updateParsedLyrics(
                    lines = rows,
                    hasSyllable = rows.any { !it.syllables.isNullOrEmpty() },
                    sourceLabel = appName,
                    apiPath = API_PATH,
                    timelineCapability = timeline.capability,
                    receiveTranslation = timeline.receiveTranslation,
                    receiveRomanization = timeline.receiveRomanization
                )) {
                lastTimeline = timeline
                timelineResult = "时间轴已写入仓库 (${timeline.capability})"
            } else {
                timelineResult = "时间轴未被仓库接受"
            }
        }

        val current = update.currentLine
        val text = current?.text.orEmpty()
        if (text.isNotBlank()) {
            val key = SuperLyricMapper.lineKey(track, update) +
                listOf(rule.receiveSuperLyricTranslation, rule.receiveSuperLyricRomanization)
            if (key != lastLineKey) {
                repo.updateLyric(text, appName, API_PATH, update.translation, update.roma,
                    receiveTranslation = rule.receiveSuperLyricTranslation,
                    receiveRomanization = rule.receiveSuperLyricRomanization)
                lastLineKey = key
            }
        }
        val instrumental = text.contains("纯音乐") || text.contains("Instrumental", true) ||
            text.contains("No lyrics", true) || text.contains("请欣赏") || text.contains("没有歌词")
        if (rows.isEmpty() && !instrumental && rule.useOnlineLyrics && lastOnlineRequest != track) {
            lastOnlineRequest = track
            onlineLyricSource.fetchFor(track.title, track.artist, track.packageName)
        }
        publishDebug(update, if (current == null) "no active line" else null)
        SuperLyricTrace.decision(update.traceId,
            "处理完成：$timelineResult；${if (current == null) "无当前行" else "当前行已处理（含去重）"}; 匹配依据=$matchBasis; 接收翻译=${rule.receiveSuperLyricTranslation}; 接收拼音歌词=${rule.receiveSuperLyricRomanization}; current=$track")
    }

    private fun receiveStop(
        publisher: String?, id: String?, reason: String, token: Long, observed: SuperLyricTrack?, traceId: Long?
    ) {
        val track = currentTrack()
        if (!registered || !session.accepts(token, observed, track) || track == null) {
            SuperLyricTrace.decision(traceId, "忽略 STOP：连接代次或 MediaSession 曲目无效; current=$track")
            return
        }
        if (!publisher.isNullOrBlank() && publisher != track.packageName) {
            SuperLyricTrace.decision(traceId, "忽略 STOP：发布者不匹配; current=$track")
            return
        }
        if (id != null && lastDocumentId != null && id != lastDocumentId) {
            SuperLyricTrace.decision(traceId, "忽略 STOP：文档不匹配; currentDocument=$lastDocumentId")
            return
        }
        lastLineKey = null
        lastTimeline = null
        legacyLineState.reset()
        // Pause/position and source arbitration remain owned by MediaSession/repository.
        publishDebug(null, null, reason)
        SuperLyricTrace.decision(traceId, "STOP 已处理：$reason; current=$track")
    }

    private fun publishDebug(update: SuperLyricUpdate?, skip: String?, stopReason: String? = null) {
        val track = currentTrack()
        LyricRepository.getInstance().updateSuperLyricDebug(LyricRepository.SuperLyricDebugInfo(
            publisher = update?.publisher,
            packageName = track?.packageName.orEmpty(),
            mode = update?.mode ?: connection?.mode ?: "unavailable",
            registrationResult = registrationResult,
            serverApiVersion = connection?.apiVersion,
            documentId = update?.documentId ?: lastDocumentId,
            lineCount = update?.lines?.size ?: 0,
            currentIndex = update?.index ?: -1,
            lyric = update?.currentLine?.text.orEmpty(),
            translation = update?.translation,
            roma = update?.roma,
            wordsPreview = update?.currentLine?.syllables.orEmpty().take(8)
                .joinToString(" | ") { "${it.text}@${it.startTime}-${it.endTime}" },
            position = update?.position,
            speed = update?.speed,
            playbackState = update?.playbackState,
            stopReason = stopReason,
            skipReason = skip
        ))
    }

    private fun resetTrack() {
        legacyLineState.reset()
        activeTrack = null
        lastLineKey = null
        lastTimeline = null
        lastOnlineRequest = null
        lastDocumentId = null
    }

    private fun currentTrack(): SuperLyricTrack? = LyricRepository.getInstance().liveMetadata.value?.let {
        SuperLyricTrack(it.packageName, it.title, it.artist, it.mediaId)
    }

    private fun getAppName(pkg: String): String = appNameCache.getOrPut(pkg) {
        runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }

    companion object {
        private const val TAG = "SuperLyricSource"
        private const val API_PATH = "SuperLyric"
        private const val INITIAL_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 60_000L
        private const val HEALTH_CHECK_MS = 30_000L
    }
}
