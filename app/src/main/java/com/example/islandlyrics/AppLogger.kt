package com.example.islandlyrics

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogger private constructor() {

    val logs = MutableLiveData("")
    private val logBuffer = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = String.format("[%s] [%s] %s\n", timestamp, tag, message)

        // Ensure UI updates happen on Main Thread
        mainHandler.post {
            logBuffer.append(logLine)
            // Keep buffer size for Deep Trace (approx last 100+ lines / 12KB)
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
