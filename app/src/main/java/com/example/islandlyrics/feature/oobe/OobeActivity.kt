package com.example.islandlyrics.feature.oobe

import android.content.Context
import com.example.islandlyrics.feature.oobe.material.OobeScreen
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.feature.main.MainActivity

import android.content.Intent

class OobeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
            val onFinish: () -> Unit = {
                // Mark OOBE as completed
                prefs.edit().putBoolean("is_setup_complete", true).apply()
                
                // Navigate to Main
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

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
                OobeScreen(onFinish = onFinish)
            }
        }
    }
}
