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

package com.example.islandlyrics.runtime.memory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import com.example.islandlyrics.core.cache.AppImageCacheManager
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRuleHelper
import org.json.JSONObject
import java.io.File

/**
 * Handles the cross-OEM Fair Memory Mechanism (公平运行内存机制).
 *
 * Supported ROMs: HyperOS, ColorOS, OriginOS/FuntouchOS, MagicOS, RealmeUI
 * System requirement: Android 16+ (API 36)
 *
 * Listens for [ACTION_TRIM] broadcasts. On TRIM, releases caches.
 * On KILL, backs up lyric state and replies to the system via Binder callback
 * within the 3-second deadline.
 */
class FairMemoryManager private constructor() : IBinder.DeathRecipient {

    // ── Broadcast action (cross-OEM standard) ──────────────────────────────
    companion object {
        const val TAG = "FairMemoryManager"
        const val ACTION_TRIM = "itgsa.intent.action.TRIM"

        // Bundle keys — common
        const val BUNDLE_KEY_COMMON = "common"
        const val BUNDLE_KEY_EXTRA = "extra"
        const val BUNDLE_KEY_NOTIFY_TYPE = "notifyType"
        const val BUNDLE_KEY_NOTIFY_ID = "notifyId"
        const val BUNDLE_KEY_REASON = "reason"
        const val BUNDLE_KEY_ACTION = "action"
        const val BUNDLE_KEY_CALLBACK = "callback"

        // Bundle keys — extra (physical memory)
        const val BUNDLE_KEY_PSS = "pss"
        const val BUNDLE_KEY_PSS_LIMIT = "pssLimit"

        // Bundle keys — extra (Java heap)
        const val BUNDLE_KEY_HEAP_ALLOC = "heapAlloc"
        const val BUNDLE_KEY_HEAP_CAPACITY = "heapCapacity"

        // Notify types
        const val NOTIFY_TYPE_PHYSICAL = 1000
        const val NOTIFY_TYPE_JAVA_HEAP = 2000

        // Action values in the bundle
        const val ACTION_KILL = "kill"
        const val ACTION_TRIM_VAL = "trim"

        // Reply result codes
        const val RESULT_SUCCESS = 0
        const val RESULT_FAILURE = 1

        // Binder transaction code
        const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION

        // Backup file
        const val BACKUP_FILE = "fair_memory_backup.json"

        // Target ROMs that support this mechanism
        val TARGET_ROMS = setOf("HyperOS", "ColorOS", "OriginOS/FuntouchOS", "MagicOS", "RealmeUI")

        fun getInstance(): FairMemoryManager = Instance.INSTANCE
    }

    // ── Singleton ──────────────────────────────────────────────────────────
    @Volatile
    private var mRemote: IBinder? = null
    private var mInitialized = false
    private var mHandler: Handler? = null
    private var mHandlerThread: HandlerThread? = null
    private var mContext: Context? = null

    private object Instance {
        val INSTANCE = FairMemoryManager()
    }

    fun initialize(context: Context) {
        synchronized(this) {
            if (mInitialized) return

            // Guard 1: must be a target ROM
            val romType = RomUtils.getRomType()
            if (romType !in TARGET_ROMS) {
                AppLogger.getInstance().log(TAG, "ROM '$romType' not in target list — skipping")
                return
            }

            // Guard 2: must be Android 16+
            if (Build.VERSION.SDK_INT < 36) {
                AppLogger.getInstance().log(TAG, "Android ${Build.VERSION.SDK_INT} < 36 — skipping")
                return
            }

            mContext = context.applicationContext

            // Background thread for broadcast handling
            mHandlerThread = HandlerThread("FairMemoryManager").apply { start() }
            mHandler = Handler(mHandlerThread!!.looper)

            val filter = IntentFilter(ACTION_TRIM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(mReceiver, filter, null, mHandler)
            }

            mInitialized = true
            AppLogger.getInstance().log(TAG, "Initialized on $romType / API ${Build.VERSION.SDK_INT}")
        }
    }

    // ── BroadcastReceiver ───────────────────────────────────────────────────
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_TRIM != intent.action) return

            val data = intent.extras ?: return
            val bundle = data.getBundle(BUNDLE_KEY_COMMON) ?: return

            val notifyType = bundle.getInt(BUNDLE_KEY_NOTIFY_TYPE)
            val notifyId = bundle.getInt(BUNDLE_KEY_NOTIFY_ID)
            val reason = bundle.getString(BUNDLE_KEY_REASON) ?: "Unknown"
            val action = bundle.getString(BUNDLE_KEY_ACTION) ?: ""
            val callbackBinder = bundle.getBinder(BUNDLE_KEY_CALLBACK)

            val extraData = data.getBundle(BUNDLE_KEY_EXTRA)

            // Log memory diagnostics
            val pss = extraData?.getInt(BUNDLE_KEY_PSS) ?: 0
            val pssLimit = extraData?.getInt(BUNDLE_KEY_PSS_LIMIT) ?: 0
            val heapAlloc = extraData?.getInt(BUNDLE_KEY_HEAP_ALLOC) ?: 0
            val heapCapacity = extraData?.getInt(BUNDLE_KEY_HEAP_CAPACITY) ?: 0

            AppLogger.getInstance().log(TAG,
                "Received action=$action notifyType=$notifyType notifyId=$notifyId reason=$reason " +
                "pss=${pss}KB/${pssLimit}KB heap=${heapAlloc}KB/${heapCapacity}KB"
            )

            when (action) {
                ACTION_TRIM_VAL -> handleTrim(notifyType, notifyId, callbackBinder, reason)
                ACTION_KILL -> handleKill(notifyType, notifyId, callbackBinder, reason)
                else -> {
                    AppLogger.getInstance().log(TAG, "Unknown action: $action")
                    // Still reply to avoid system-side timeout
                    if (callbackBinder != null && checkRemote(callbackBinder)) {
                        reply(notifyType, notifyId, RESULT_SUCCESS, null)
                    }
                }
            }
        }
    }

    // ── TRIM: Release memory ────────────────────────────────────────────────
    private fun handleTrim(notifyType: Int, notifyId: Int, callback: IBinder?, reason: String) {
        AppLogger.getInstance().log(TAG, "TRIM: releasing memory (reason=$reason)")

        val ctx = mContext
        if (ctx == null || callback == null) return

        if (!checkRemote(callback)) {
            AppLogger.getInstance().log(TAG, "TRIM: failed to link remote binder")
            return
        }

        try {
            // 1. Clear image cache (Coil memory + disk)
            AppImageCacheManager.clear(ctx)

            // 2. Clear parser rule in-memory cache
            ParserRuleHelper.invalidateCache()

            // 3. Best-effort GC
            Runtime.getRuntime().gc()

            AppLogger.getInstance().log(TAG, "TRIM: memory released successfully")
            reply(notifyType, notifyId, RESULT_SUCCESS, null)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "TRIM: release failed: ${e.message}")
            reply(notifyType, notifyId, RESULT_FAILURE, null)
        }
    }

    // ── KILL: Backup state ──────────────────────────────────────────────────
    private fun handleKill(notifyType: Int, notifyId: Int, callback: IBinder?, reason: String) {
        AppLogger.getInstance().log(TAG, "KILL: backing up state (reason=$reason)")

        val ctx = mContext
        if (ctx == null || callback == null) return

        if (!checkRemote(callback)) {
            AppLogger.getInstance().log(TAG, "KILL: failed to link remote binder")
            return
        }

        try {
            backupState(ctx)
            AppLogger.getInstance().log(TAG, "KILL: state backed up successfully")
            reply(notifyType, notifyId, RESULT_SUCCESS, null)
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "KILL: backup failed: ${e.message}")
            reply(notifyType, notifyId, RESULT_FAILURE, null)
        }
    }

    // ── State Backup ────────────────────────────────────────────────────────
    fun backupState(context: Context) {
        val repo = LyricRepository.getInstance()
        val json = JSONObject()

        // Lyric
        repo.liveLyric.value?.let { lyric ->
            val lyricObj = JSONObject()
            lyricObj.put("lyric", lyric.lyric)
            lyricObj.put("sourceApp", lyric.sourceApp)
            lyricObj.put("apiPath", lyric.apiPath)
            lyric.translation?.let { lyricObj.put("translation", it) }
            lyric.roma?.let { lyricObj.put("roma", it) }
            json.put("lyric", lyricObj)
        }

        // Metadata
        repo.liveMetadata.value?.let { meta ->
            val metaObj = JSONObject()
            metaObj.put("title", meta.title)
            metaObj.put("artist", meta.artist)
            metaObj.put("packageName", meta.packageName)
            metaObj.put("duration", meta.duration)
            json.put("metadata", metaObj)
        }

        // Progress
        repo.liveProgress.value?.let { progress ->
            val progressObj = JSONObject()
            progressObj.put("position", progress.position)
            progressObj.put("duration", progress.duration)
            json.put("progress", progressObj)
        }

        // Playing state
        json.put("isPlaying", repo.isPlaying.value ?: false)
        json.put("timestamp", System.currentTimeMillis())

        val file = File(context.filesDir, BACKUP_FILE)
        file.writeText(json.toString())
    }

    // ── State Recovery ──────────────────────────────────────────────────────
    /**
     * Attempts to restore lyric state from a previous KILL backup.
     * Should be called early in LyricService.onCreate().
     * Returns true if state was restored.
     */
    fun tryRestoreState(context: Context): Boolean {
        val file = File(context.filesDir, BACKUP_FILE)
        if (!file.exists()) return false

        return try {
            val json = JSONObject(file.readText())
            val timestamp = json.optLong("timestamp", 0L)
            val ageMs = System.currentTimeMillis() - timestamp

            // Only restore if backup is recent (< 5 minutes old)
            if (ageMs > 5 * 60 * 1000) {
                AppLogger.getInstance().log(TAG, "Backup too old (${ageMs / 1000}s) — discarding")
                file.delete()
                return false
            }

            val repo = LyricRepository.getInstance()

            // Restore metadata first (lyric depends on it)
            val metaObj = json.optJSONObject("metadata")
            if (metaObj != null) {
                repo.updateMediaMetadata(
                    title = metaObj.optString("title"),
                    artist = metaObj.optString("artist"),
                    packageName = metaObj.optString("packageName"),
                    duration = metaObj.optLong("duration", 0L),
                    rawTitle = metaObj.optString("title"),
                    rawArtist = metaObj.optString("artist")
                )
            }

            // Restore lyric
            val lyricObj = json.optJSONObject("lyric")
            if (lyricObj != null) {
                val translation = lyricObj.optString("translation", "").takeIf { it.isNotBlank() }
                val roma = lyricObj.optString("roma", "").takeIf { it.isNotBlank() }
                repo.updateLyric(
                    lyric = lyricObj.optString("lyric"),
                    app = lyricObj.optString("sourceApp"),
                    apiPath = lyricObj.optString("apiPath", "Restored"),
                    translation = translation,
                    roma = roma
                )
            }

            // Restore progress
            val progressObj = json.optJSONObject("progress")
            if (progressObj != null) {
                repo.updateProgress(
                    position = progressObj.optLong("position", 0L),
                    duration = progressObj.optLong("duration", 0L)
                )
            }

            // Restore playing state
            val wasPlaying = json.optBoolean("isPlaying", false)
            repo.updatePlaybackStatus(wasPlaying)

            AppLogger.getInstance().log(TAG, "State restored from backup (${ageMs / 1000}s old)")
            file.delete()
            true
        } catch (e: Exception) {
            AppLogger.getInstance().e(TAG, "Failed to restore state: ${e.message}")
            file.delete()
            false
        }
    }

    // ── Binder Death Recipient ──────────────────────────────────────────────
    override fun binderDied() {
        synchronized(this) {
            mRemote?.let {
                try { it.unlinkToDeath(this, 0) } catch (_: Exception) {}
            }
            mRemote = null
        }
    }

    // ── Binder check ────────────────────────────────────────────────────────
    private fun checkRemote(callback: IBinder): Boolean {
        synchronized(this) {
            if (mRemote == null) {
                return try {
                    mRemote = callback
                    mRemote!!.linkToDeath(this, 0)
                    true
                } catch (e: RemoteException) {
                    mRemote = null
                    false
                }
            }
        }
        return true
    }

    // ── Reply callback (must be called within 3 seconds) ────────────────────
    fun reply(notifyType: Int, notifyId: Int, result: Int, extra: Bundle?) {
        synchronized(this) {
            val remote = mRemote ?: return
            var bundle = extra ?: Bundle()

            var data: Parcel? = null
            var reply: Parcel? = null
            try {
                data = Parcel.obtain()
                reply = Parcel.obtain()
                data.writeInt(notifyType)
                data.writeInt(notifyId)
                data.writeInt(result)
                data.writeBundle(bundle)
                remote.transact(TRANSACTION_EXCEPTION_REPLY, data, reply, IBinder.FLAG_ONEWAY)
                reply.readException()
            } catch (e: Exception) {
                AppLogger.getInstance().e(TAG, "reply failed: ${e.message}")
            } finally {
                reply?.recycle()
                data?.recycle()
            }
        }
    }
}
