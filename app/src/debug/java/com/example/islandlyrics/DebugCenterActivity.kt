package com.example.islandlyrics

import android.os.Bundle
import androidx.activity.compose.setContent

class DebugCenterActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                DebugCenterScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
