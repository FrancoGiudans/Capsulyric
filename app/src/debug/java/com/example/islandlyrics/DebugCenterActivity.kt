package com.example.islandlyrics

import android.os.Bundle
import androidx.activity.compose.setContent

class DebugCenterActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this@DebugCenterActivity)) {
                MiuixAppTheme {
                    MiuixDebugCenterScreen(
                        onBack = { finish() }
                    )
                }
            } else {
                AppTheme {
                    DebugCenterScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
