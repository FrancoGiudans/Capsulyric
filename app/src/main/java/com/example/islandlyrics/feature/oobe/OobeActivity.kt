package com.example.islandlyrics.feature.oobe

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.oobe.miuix.MiuixOobeScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme

import android.content.Intent

class OobeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val prefs = AppPreferences.of(this)
            val onFinish: () -> Unit = {
                // Mark OOBE as completed
                prefs.edit { putBoolean(AppPreferences.Keys.IS_SETUP_COMPLETE, true) }
                
                // Navigate to Main
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            val onImportAndFinish: (String) -> Unit = { snackbarMessage ->
                prefs.edit { putBoolean(AppPreferences.Keys.IS_SETUP_COMPLETE, true) }

                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_STARTUP_SNACKBAR_MESSAGE, snackbarMessage)
                }
                startActivity(intent)
                finish()
            }

            if (!prefs.contains(AppPreferences.Keys.UI_USE_MIUIX)) {
                prefs.edit { putBoolean(AppPreferences.Keys.UI_USE_MIUIX, true) }
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

