package com.example.islandlyrics

import android.content.Context
import androidx.activity.compose.setContent
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme

class ParserRuleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            val followSystem = prefs.getBoolean("theme_follow_system", true)
            val darkMode = prefs.getBoolean("theme_dark_mode", false)
            val pureBlack = prefs.getBoolean("theme_pure_black", false)
            val dynamicColor = prefs.getBoolean("theme_dynamic_color", true)
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = if (followSystem) isSystemDark else darkMode

            AppTheme(
                darkTheme = useDarkTheme,
                dynamicColor = dynamicColor,
                pureBlack = pureBlack && useDarkTheme
            ) {
                ParserRuleScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
