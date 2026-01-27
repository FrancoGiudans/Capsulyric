package com.example.islandlyrics;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class IslandLyricsApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Apply saved theme preferences (Mode, Language)
        ThemeHelper.applyTheme(this);

        // Enable Dynamic Colors if allowed
        if (ThemeHelper.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }
}
