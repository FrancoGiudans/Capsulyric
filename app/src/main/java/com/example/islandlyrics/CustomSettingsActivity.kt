package com.example.islandlyrics

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource

class CustomSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
