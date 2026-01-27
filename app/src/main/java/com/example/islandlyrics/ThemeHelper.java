package com.example.islandlyrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.color.DynamicColors;

public class ThemeHelper {

    private static final String PREFS_NAME = "IslandLyricsPrefs";
    private static final String KEY_LANGUAGE = "language_code"; // "" (system), "en", "zh-CN"
    private static final String KEY_FOLLOW_SYSTEM = "theme_follow_system";
    private static final String KEY_DARK_MODE = "theme_dark_mode";
    private static final String KEY_PURE_BLACK = "theme_pure_black";
    private static final String KEY_DYNAMIC_COLOR = "theme_dynamic_color";

    public static void applyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 1. Language
        String lang = prefs.getString(KEY_LANGUAGE, "");
        if (lang.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));
        }

        // 2. Dark Mode
        boolean followSystem = prefs.getBoolean(KEY_FOLLOW_SYSTEM, true);
        if (followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
            AppCompatDelegate.setDefaultNightMode(isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        // Dynamic Colors handled in Application/Activity onCreate
    }

    public static boolean isDynamicColorEnabled(Context context) {
         return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DYNAMIC_COLOR, true);
    }
    
    public static boolean isPureBlackEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .getBoolean(KEY_PURE_BLACK, false);
   }

    // Setters
    public static void setLanguage(Context context, String langCode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, langCode).apply();
        if (langCode.isEmpty()) {
             AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
             AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode));
        }
    }

    public static void setFollowSystem(Context context, boolean follow) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_FOLLOW_SYSTEM, follow).apply();
        // Re-apply immediately
        if (follow) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean isDarkMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, false);
            AppCompatDelegate.setDefaultNightMode(isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    public static void setDarkMode(Context context, boolean enable) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK_MODE, enable).apply();
        if (!context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_FOLLOW_SYSTEM, true)) {
             AppCompatDelegate.setDefaultNightMode(enable ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    public static void setPureBlack(Context context, boolean enable) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PURE_BLACK, enable).apply();
        // Activity needs recreation to pick this up in onCreate
    }

    public static void setDynamicColor(Context context, boolean enable) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DYNAMIC_COLOR, enable).apply();
        // Requires App restart or Activity recreation logic
    }
}
