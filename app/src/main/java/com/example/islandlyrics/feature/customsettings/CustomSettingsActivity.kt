package com.example.islandlyrics.feature.customsettings

import com.example.islandlyrics.ui.common.BaseActivity
import android.os.Bundle
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.feature.customsettings.miuix.MiuixCustomSettingsScreen
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.feature.customsettings.material.CustomSettingsScreen
import com.example.islandlyrics.ui.theme.material.AppTheme
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
