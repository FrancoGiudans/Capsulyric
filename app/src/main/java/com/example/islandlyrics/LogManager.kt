package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class LogManager private constructor() {

    private var logHandler: android.os.Handler? = null

    private fun init(context: Context) {
        if (logFile == null) {
            logFile = File(context.filesDir, FILE_NAME)
            
            // Initialize background thread for file I/O
            val thread = android.os.HandlerThread("LogWorker")
            thread.start()
            logHandler = android.os.Handler(thread.looper)
            
            // cleanup on background thread
            logHandler?.post {
                cleanOldLogs(context)
            }
        }
    }

    private fun cleanOldLogs(context: Context) {
        try {
            val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            val lastCleanup = prefs.getLong("last_log_cleanup_time", 0)
            val now = System.currentTimeMillis()

            // Run once every 24 hours
            if (now - lastCleanup < 24 * 60 * 60 * 1000) {
                return
            }

            // Update cleanup time immediately
            prefs.edit().putLong("last_log_cleanup_time", now).apply()

            if (logFile == null || !logFile!!.exists()) return

            val cutoffTime = now - 48 * 60 * 60 * 1000 // 48 hours ago
            val tempFile = File(context.filesDir, "app_log.tmp")
            val writer = java.io.BufferedWriter(java.io.FileWriter(tempFile))
            val reader = java.io.BufferedReader(java.io.FileReader(logFile!!))

            var keepCurrentEntry = false
            var line: String? = reader.readLine()
            
            while (line != null) {
                val matcher = LOG_PATTERN.matcher(line)
                if (matcher.find()) {
                    // New Log Entry
                    val dateStr = matcher.group(1)
                    if (dateStr != null) {
                        try {
                            val date = DATE_FORMAT.parse(dateStr)
                            if (date != null && date.time >= cutoffTime) {
                                keepCurrentEntry = true
                                writer.write(line)
                                writer.newLine()
                            } else {
                                keepCurrentEntry = false
                            }
                        } catch (e: Exception) {
                            // Date parse error, keep safely if we kept previous
                            if (keepCurrentEntry) {
                                writer.write(line)
                                writer.newLine()
                            }
                        }
                    }
                } else {
                    // Continuation line (stack trace etc)
                    if (keepCurrentEntry) {
                        writer.write(line)
                        writer.newLine()
                    }
                }
                line = reader.readLine()
            }

            reader.close()
            writer.close()

            if (logFile!!.delete()) {
                tempFile.renameTo(logFile!!)
            } else {
                tempFile.delete()
            }
            
            Log.d("LogManager", "Log cleanup completed. Retained logs < 48h.")

        } catch (e: Exception) {
            Log.e("LogManager", "Failed to clean logs: ${e.message}")
        }
    }

    @Synchronized
    fun d(context: Context, tag: String, msg: String) {
        if (!isDebug) return
        init(context)
        Log.d(tag, msg)
        
        val logLine = String.format("%s D/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        logHandler?.post {
            appendToFile(logLine)
        }
    }

    @Synchronized
    fun e(context: Context, tag: String, msg: String) {
        // ERROR level always writes, even in release (for crash tracking)
        init(context)
        Log.e(tag, msg)
        
        val logLine = String.format("%s E/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        logHandler?.post {
            appendToFile(logLine)
        }
    }

    @Synchronized
    fun w(context: Context, tag: String, msg: String) {
        if (!isDebug) return
        init(context)
        Log.w(tag, msg)
        
        val logLine = String.format("%s W/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        logHandler?.post {
            appendToFile(logLine)
        }
    }

    @Synchronized
    fun i(context: Context, tag: String, msg: String) {
        if (!isDebug) return
        init(context)
        Log.i(tag, msg)
        
        val logLine = String.format("%s I/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        logHandler?.post {
            appendToFile(logLine)
        }
    }

    private fun appendToFile(line: String) {
        if (logFile == null) return
        try {
            // This now runs on the background thread
            FileWriter(logFile, true).use { fw ->
                fw.append(line).append("\n")
            }
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to write log: ${e.message}")
        }
    }

    @Synchronized
    fun getLogEntries(context: Context): List<LogEntry> {
        init(context)
        val entries = ArrayList<LogEntry>()
        if (logFile?.exists() == true) {
            try {
                BufferedReader(FileReader(logFile)).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        val matcher = LOG_PATTERN.matcher(line ?: "")
                        if (matcher.find()) {
                            entries.add(LogEntry(matcher.group(1) ?: "", matcher.group(2) ?: "", matcher.group(3) ?: "", matcher.group(4) ?: ""))
                        } else {
                            entries.add(LogEntry("", "V", "System", line ?: ""))
                        }
                    }
                }
            } catch (e: Exception) {
                entries.add(LogEntry("", "E", "LogManager", "Error reading log: ${e.message}"))
            }
        }
        return entries
    }

    @Synchronized
    fun clearLog(context: Context) {
        init(context)
        if (logFile?.exists() == true) {
            logFile?.delete()
        }
        try {
            logFile?.createNewFile()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun exportLog(context: Context) {
        init(context)
        if (logFile == null || logFile?.exists() == false) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile!!)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(intent, "Export Logs")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    companion object {
        private var instance: LogManager? = null
        private const val FILE_NAME = "app_log.txt"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private val LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s([A-Z])/(.+?):\\s(.*)$")

        @Synchronized
        fun getInstance(): LogManager {
            if (instance == null) {
                instance = LogManager()
            }
            return instance!!
        }
    }
}
