package com.example.islandlyrics.feature.qqroman

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.qqroman.material.QqRomanDebugScreen
import com.example.islandlyrics.feature.qqroman.miuix.MiuixQqRomanDebugScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class QqRomanDebugActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    MiuixQqRomanDebugScreen(onBack = { finish() })
                }
            } else {
                AppTheme {
                    QqRomanDebugScreen(onBack = { finish() })
                }
            }
        }
    }
}
