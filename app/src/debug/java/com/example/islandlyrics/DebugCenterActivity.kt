package com.example.islandlyrics

import android.os.Bundle
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.ui.common.BaseActivity
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
