package com.example.islandlyrics

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogger private constructor() {

    // Simplified: No buffering, no LiveData
    // val logs = MutableLiveData("") 
    // private val logBuffer = StringBuilder()
    
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
    }

    // INFO: Only if Enabled (Release builds default to OFF)
    fun i(tag: String, message: String) {
        if (!isLogEnabled) return
        Log.i(tag, message)
    }

    // ERROR: Always to Logcat (for Crashlytics)
    fun e(tag: String, message: String) {
        e(tag, message, null)
    }

    fun e(tag: String, message: String, tr: Throwable?) {
        // Always log error to system for crash reporting
        if (tr != null) {
            Log.e(tag, message, tr)
        } else {
            Log.e(tag, message)
        }
    }

    // Legacy support: map 'log' to 'd' (Debug) or 'i' (Info) depending on importance
    fun log(tag: String, message: String) {
        d(tag, message)
    }
    
    // Legacy: No-op for buffer
    private fun appendToBuffer(level: String, tag: String, message: String) {
        // No-op
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
