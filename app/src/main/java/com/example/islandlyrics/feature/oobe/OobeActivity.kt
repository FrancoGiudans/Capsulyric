package com.example.islandlyrics.feature.oobe

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.edit
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.oobe.miuix.MiuixOobeScreen
import com.example.islandlyrics.ui.miuix.MiuixAppTheme

import android.content.Intent

class OobeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val prefs = getSharedPreferences("IslandLyricsPrefs", MODE_PRIVATE)
            val onFinish: () -> Unit = {
                // Mark OOBE as completed
                prefs.edit { putBoolean("is_setup_complete", true) }
                
                // Navigate to Main
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            val onImportAndFinish: (String) -> Unit = { snackbarMessage ->
                prefs.edit { putBoolean("is_setup_complete", true) }

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_STARTUP_SNACKBAR_MESSAGE, snackbarMessage)
                }
                startActivity(intent)
                finish()
            }

            if (!prefs.contains("ui_use_miuix")) {
                prefs.edit { putBoolean("ui_use_miuix", true) }
            }

            MiuixAppTheme {
                MiuixOobeScreen(
                    onFinish = onFinish,
                    onImportAndFinish = onImportAndFinish
                )
            }
        }
    }
}
