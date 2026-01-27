package com.example.islandlyrics;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.MutableLiveData;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLogger {
    private static AppLogger sInstance;
    private final MutableLiveData<String> logs = new MutableLiveData<>("");
    private final StringBuilder logBuffer = new StringBuilder();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppLogger() {}

    public static synchronized AppLogger getInstance() {
        if (sInstance == null) {
            sInstance = new AppLogger();
        }
        return sInstance;
    }

    public MutableLiveData<String> getLogs() {
        return logs;
    }

    public void log(String tag, String message) {
        String timestamp = dateFormat.format(new Date());
        String logLine = String.format("[%s] [%s] %s\n", timestamp, tag, message);

        // Ensure UI updates happen on Main Thread
        mainHandler.post(() -> {
            logBuffer.append(logLine);
            // Keep buffer size for Deep Trace (approx last 100+ lines / 12KB)
            if (logBuffer.length() > 12000) {
                logBuffer.delete(0, logBuffer.indexOf("\n", 4000) + 1);
            }
            logs.setValue(logBuffer.toString());
        });
    }
}
