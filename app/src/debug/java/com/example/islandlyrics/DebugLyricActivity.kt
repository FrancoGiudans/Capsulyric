package com.example.islandlyrics

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.feature.debug.DebugLyricScreen
import com.example.islandlyrics.feature.debug.MiuixDebugLyricScreen

class DebugLyricActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            if (isMiuixEnabled(this@DebugLyricActivity)) {
                MiuixAppTheme {
                    MiuixDebugLyricScreen(
                        onBack = { finish() }
                    )
                }
            } else {
                AppTheme {
                    DebugLyricScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
