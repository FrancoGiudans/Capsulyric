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

    private var logFile: File? = null

    class LogEntry(val timestamp: String, val level: String, val tag: String, val message: String)

    private fun init(context: Context) {
        if (logFile == null) {
            logFile = File(context.filesDir, FILE_NAME)
        }
    }

    @Synchronized
    fun d(context: Context, tag: String, msg: String) {
        init(context)
        val logLine = String.format("%s D/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        Log.d(tag, msg)
        appendToFile(logLine)
    }

    @Synchronized
    fun e(context: Context, tag: String, msg: String) {
        init(context)
        val logLine = String.format("%s E/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        Log.e(tag, msg)
        appendToFile(logLine)
    }

    @Synchronized
    fun w(context: Context, tag: String, msg: String) {
        init(context)
        val logLine = String.format("%s W/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        Log.w(tag, msg)
        appendToFile(logLine)
    }

    @Synchronized
    fun i(context: Context, tag: String, msg: String) {
        init(context)
        val logLine = String.format("%s I/%s: %s", DATE_FORMAT.format(Date()), tag, msg)
        Log.i(tag, msg)
        appendToFile(logLine)
    }

    private fun appendToFile(line: String) {
        if (logFile == null) return
        try {
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
                        val matcher = LOG_PATTERN.matcher(line)
                        if (matcher.find()) {
                            entries.add(LogEntry(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)))
                        } else {
                            entries.add(LogEntry("", "V", "System", line!!))
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
        private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        private val LOG_PATTERN = Pattern.compile("^(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s([A-Z])/(.+?):\\s(.*)$")

        @Synchronized
        fun getInstance(): LogManager {
            if (instance == null) {
                instance = LogManager()
            }
            return instance!!
        }
    }
}
