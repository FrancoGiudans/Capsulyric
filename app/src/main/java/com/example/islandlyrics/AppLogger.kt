package com.example.islandlyrics

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogger private constructor() {

    val logs = MutableLiveData("")
    private val logBuffer = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Check Debug Flag (Static build config)
    private val isDebug = BuildConfig.DEBUG
    
    // Runtime Logging Flag (Default to Debug status, but can be enabled in Release)
    var isLogEnabled = isDebug

    fun enableLogging(enable: Boolean) {
        isLogEnabled = enable
        if (enable) {
            i("AppLogger", "Logging Enabled by User")
        }
    }

    // DEBUG: Only if Enabled
    fun d(tag: String, message: String) {
        if (!isLogEnabled) return
        
        Log.d(tag, message)
        appendToBuffer("D", tag, message)
    }

    // INFO: Only if Enabled (Release builds default to OFF)
    fun i(tag: String, message: String) {
        if (!isLogEnabled) return
        
        Log.i(tag, message)
        appendToBuffer("I", tag, message)
    }

    // ERROR: Always to Logcat (for Crashlytics), but Buffer only if enabled
    fun e(tag: String, message: String) {
        // Always log error to system for crash reporting
        Log.e(tag, message)
        
        // Only show in UI if enabled
        if (isLogEnabled) {
            appendToBuffer("E", tag, message)
        }
    }

    // Legacy support: map 'log' to 'd' (Debug) or 'i' (Info) depending on importance
    // For now, map to Debug to hide spam in release.
    fun log(tag: String, message: String) {
        d(tag, message)
    }

    private fun appendToBuffer(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = String.format(Locale.US, "%s [%s/%s] %s\n", timestamp, level, tag, message)

        mainHandler.post {
            logBuffer.append(logLine)
            if (logBuffer.length > 12000) {
                val index = logBuffer.indexOf("\n", 4000)
                if (index != -1) {
                    logBuffer.delete(0, index + 1)
                }
            }
            logs.value = logBuffer.toString()
        }
    }

    companion object {
        private var instance: AppLogger? = null

        @Synchronized
        fun getInstance(): AppLogger {
            if (instance == null) {
                instance = AppLogger()
            }
            return instance!!
        }
    }
}
