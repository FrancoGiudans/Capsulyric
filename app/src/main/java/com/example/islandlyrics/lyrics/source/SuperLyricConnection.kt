@file:Suppress("DEPRECATION")

package com.example.islandlyrics.lyrics.source

import android.os.IBinder
import com.hchen.superlyricapi.ISuperLyricManager
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.LyricListener
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.Receiver
import com.hchen.superlyricapi.SuperLyric
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.model.Stop

/** One mutually exclusive connection: v4 Receiver or the frozen 3.x AIDL. */
internal class SuperLyricConnection(
    private val findService: (String) -> IBinder? = ::findSuperLyricService,
    private val receiver: Receiver = SuperLyric.receiver()
) {
    private var binder: IBinder? = null
    private var manager: ISuperLyricManager? = null
    private var modernListener: LyricListener? = null
    private var legacyListener: ISuperLyricReceiver? = null
    private val mapper = SuperLyricDocumentMapper()
    @Volatile private var closed = false
    @Volatile var latestUpdate: SuperLyricUpdate? = null
        private set
    var mode: String = "unavailable"
        private set
    var apiVersion: Int? = null
        private set

    fun open(
        onLyric: (SuperLyricUpdate) -> Unit,
        onStop: (publisher: String?, documentId: String?, reason: String, traceId: Long?) -> Unit
    ): Receiver.RegisterResult {
        check(binder == null) { "Connection already opened" }
        val modern = findService(V4_SERVICE)?.takeIf { it.isBinderAlive }
        if (modern != null) {
            mode = "v4"
            binder = modern
            val listener = object : LyricListener {
                override fun onLyric(view: LyricView) {
                    deliver({ SuperLyricTraceFormat.modern(view) }, { mapper.map(view) }, onLyric)
                }
                override fun onStop(stop: Stop) {
                    stopped(stop.publisher, stop.id, stop.reason?.name ?: "UNKNOWN",
                        { SuperLyricTraceFormat.stop(stop.publisher, stop.id, stop.reason?.name) }, onStop)
                }
            }
            modernListener = listener
            val result = receiver.register(listener)
            if (result == Receiver.RegisterResult.OK) {
                apiVersion = runCatching { SuperLyric.serverApiVersion() }.getOrNull()
            }
            return result
        }
        val legacy = findService(LEGACY_SERVICE)?.takeIf { it.isBinderAlive }
            ?: return Receiver.RegisterResult.SERVICE_UNAVAILABLE
        mode = "legacy"
        apiVersion = 0
        binder = legacy
        val api = ISuperLyricManager.Stub.asInterface(legacy)
        manager = api
        val listener = object : ISuperLyricReceiver.Stub() {
            override fun onLyric(publisher: String?, data: SuperLyricData?) {
                if (!closed && data != null) {
                    deliver({ SuperLyricTraceFormat.legacy(publisher, data) },
                        { SuperLyricMapper.legacy(publisher, data, latestUpdate) }, onLyric)
                }
            }
            override fun onStop(publisher: String?, data: SuperLyricData?) {
                stopped(publisher, data?.lyricId, "STOPPED", { SuperLyricTraceFormat.legacy(publisher, data) }, onStop)
            }
        }
        legacyListener = listener
        // Do not call getLatestLyric/getServerApiVersion: neither exists in 3.4.
        api.registerReceiver(listener)
        return Receiver.RegisterResult.OK
    }

    private fun deliver(raw: () -> String, build: () -> SuperLyricUpdate, listener: (SuperLyricUpdate) -> Unit) {
        synchronized(mapper) {
            if (closed) return
            val trace = SuperLyricTrace.begin(mode, "LYRIC", raw)
            val update = try { build().copy(traceId = trace) } catch (error: Exception) {
                SuperLyricTrace.failed(trace, error)
                throw error
            }
            SuperLyricTrace.decoded(trace) { SuperLyricTraceFormat.decoded(update) }
            latestUpdate = update
            listener(update)
        }
    }

    private fun stopped(publisher: String?, id: String?, reason: String, raw: () -> String,
                        listener: (String?, String?, String, Long?) -> Unit) {
        synchronized(mapper) {
            if (closed) return
            latestUpdate = null
            val trace = SuperLyricTrace.begin(mode, "STOP", raw)
            SuperLyricTrace.decoded(trace) { SuperLyricTraceFormat.stop(publisher, id, reason) }
            listener(publisher, id, reason, trace)
        }
    }

    fun isAlive(): Boolean {
        val current = binder ?: return false
        if (!current.isBinderAlive) return false
        val modern = findService(V4_SERVICE)?.takeIf { it.isBinderAlive }
        return if (mode == "v4") modern == current
        else modern == null && findService(LEGACY_SERVICE) == current
    }

    fun close() {
        closed = true
        try {
            modernListener?.let { receiver.unregister(it) }
            legacyListener?.let { manager?.unregisterReceiver(it) }
        } finally {
            modernListener = null
            legacyListener = null
            manager = null
            binder = null
            synchronized(mapper) { latestUpdate = null }
        }
    }

    companion object {
        const val V4_SERVICE = "super_lyric.v4"
        const val LEGACY_SERVICE = "super_lyric"
    }
}

internal fun findSuperLyricService(name: String): IBinder? =
    Class.forName("android.os.ServiceManager")
        .getDeclaredMethod("getService", String::class.java)
        .invoke(null, name) as? IBinder
