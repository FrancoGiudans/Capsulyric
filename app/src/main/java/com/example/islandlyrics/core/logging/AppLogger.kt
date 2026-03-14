package com.example.islandlyrics.core.logging

import android.content.Context
import android.util.Log
import com.example.islandlyrics.BuildConfig

/**
 * Unified logging facade for the app.
 *
 * Call [init] once in Application.onCreate() to enable file-based logging.
 * After that, every d/i/w/e call both prints to logcat AND writes to the
 * persistent log file managed by [LogManager].
 */
class AppLogger private constructor() {

    private val isDebug = BuildConfig.DEBUG

    // Runtime logging flag (defaults to debug flag, can be enabled in release)
    var isLogEnabled = isDebug

    /** Application context used for file I/O – set by [init]. */
    @Volatile
    private var appContext: Context? = null

    /**
     * Initialise the file-writing backend.  Must be called once from
     * [android.app.Application.onCreate] so that Context is available for
     * all subsequent log calls.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun enableLogging(enable: Boolean) {
        isLogEnabled = enable
        if (enable) i("AppLogger", "Logging Enabled by User")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun d(tag: String, message: String) {
        if (!isLogEnabled) return
        Log.d(tag, message)
        writeToFile("D", tag, message)
    }

    fun i(tag: String, message: String) {
        if (!isLogEnabled) return
        Log.i(tag, message)
        writeToFile("I", tag, message)
    }

    fun w(tag: String, message: String) {
        if (!isLogEnabled) return
        Log.w(tag, message)
        writeToFile("W", tag, message)
    }

    fun e(tag: String, message: String) {
        e(tag, message, null)
    }

    fun e(tag: String, message: String, tr: Throwable?) {
        // Errors always go to logcat (release crash reporting)
        if (tr != null) Log.e(tag, message, tr) else Log.e(tag, message)
        val fullMsg = if (tr != null) "$message\n${Log.getStackTraceString(tr)}" else message
        writeToFile("E", tag, fullMsg)
    }

    /** Legacy alias: maps to [d]. */
    fun log(tag: String, message: String) = d(tag, message)

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun writeToFile(level: String, tag: String, message: String) {
        val ctx = appContext ?: return          // Not yet initialised – skip
        try {
            LogManager.getInstance().writeRaw(ctx, level, tag, message)
        } catch (ignored: Exception) {
            // Never let logging itself crash the app
        }
    }

    companion object {
        @Volatile private var instance: AppLogger? = null

        @Synchronized
        fun getInstance(): AppLogger {
            if (instance == null) instance = AppLogger()
            return instance!!
        }
    }
}
