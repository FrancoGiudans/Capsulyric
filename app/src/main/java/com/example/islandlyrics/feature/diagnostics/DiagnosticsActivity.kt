package com.example.islandlyrics.feature.diagnostics

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.feature.diagnostics.material.DiagnosticsScreen
import com.example.islandlyrics.feature.diagnostics.miuix.MiuixDiagnosticsScreen

class DiagnosticsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    MiuixDiagnosticsScreen(onBack = { finish() })
                }
            } else {
                AppTheme {
                    DiagnosticsScreen(onBack = { finish() })
                }
            }
        }
    }
}
