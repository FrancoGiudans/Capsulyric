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

    // If true, Debug and Info levels may be recorded based on the selected minimum level.
    var isLogEnabled = BuildConfig.DEBUG
    private var minimumLevel = LogLevel.ERROR

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
        
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        val isDevMode = prefs.getBoolean("dev_mode_enabled", false)
        isLogEnabled = BuildConfig.DEBUG || isDevMode
        minimumLevel = LogLevel.fromPreference(prefs.getString(PREF_LOG_RECORD_LEVEL, null))
    }

    fun enableLogging(enable: Boolean) {
        isLogEnabled = enable
        if (enable) i("AppLogger", "Logging Enabled by User")
    }

    fun setMinimumLevel(level: LogLevel) {
        minimumLevel = level
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun d(tag: String, message: String) {
        if (!shouldRecord(LogLevel.DEBUG)) return
        Log.d(tag, message)
        writeToFile("D", tag, message)
    }

    fun i(tag: String, message: String) {
        if (!shouldRecord(LogLevel.INFO)) return
        Log.i(tag, message)
        writeToFile("I", tag, message)
    }

    fun w(tag: String, message: String) {
        if (!shouldRecord(LogLevel.WARN)) return
        Log.w(tag, message)
        writeToFile("W", tag, message)
    }

    fun e(tag: String, message: String) {
        e(tag, message, null)
    }

    fun e(tag: String, message: String, tr: Throwable?) {
        if (!shouldRecord(LogLevel.ERROR)) return
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

    private fun shouldRecord(level: LogLevel): Boolean {
        return level.priority >= minimumLevel.priority
    }

    enum class LogLevel(val preferenceValue: String, val priority: Int) {
        DEBUG("D", 10),
        INFO("I", 20),
        WARN("W", 30),
        ERROR("E", 40);

        companion object {
            fun fromPreference(value: String?): LogLevel {
                return entries.firstOrNull { it.preferenceValue == value } ?: ERROR
            }
        }
    }

    companion object {
        const val PREF_LOG_RECORD_LEVEL = "log_record_level"
        @Volatile private var instance: AppLogger? = null

        @Synchronized
        fun getInstance(): AppLogger {
            if (instance == null) instance = AppLogger()
            return instance!!
        }
    }
}
