package com.example.islandlyrics.ui

import android.os.Bundle
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.utils.UpdateChecker
import com.example.islandlyrics.ui.miuix.MiuixCustomSettingsScreen
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.material.CustomSettingsScreen
import com.example.islandlyrics.ui.material.AppTheme
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource

class CustomSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this@CustomSettingsActivity)) {
                MiuixAppTheme {
                    MiuixCustomSettingsScreen(
                        onBack = { finish() },
                        onCheckUpdate = { /* No-op */ },
                        onShowLogs = { /* No-op */ },
                        updateVersionText = "",
                        updateBuildText = ""
                    )
                }
            } else {
                AppTheme {
                    CustomSettingsScreen(
                        onBack = { finish() },
                        onCheckUpdate = { /* No-op or reuse UpdateChecker if needed */ },
                        onShowLogs = { /* No-op */ },
                        updateVersionText = "", // Not used in this screen
                        updateBuildText = "" // Not used in this screen
                    )
                }
            }
        }
    }
}
