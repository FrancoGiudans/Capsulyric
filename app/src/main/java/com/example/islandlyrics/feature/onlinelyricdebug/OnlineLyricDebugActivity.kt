package com.example.islandlyrics.feature.onlinelyricdebug

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.onlinelyricdebug.material.OnlineLyricDebugScreen
import com.example.islandlyrics.feature.onlinelyricdebug.miuix.MiuixOnlineLyricDebugScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class OnlineLyricDebugActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    MiuixOnlineLyricDebugScreen(onBack = { finish() })
                }
            } else {
                AppTheme {
                    OnlineLyricDebugScreen(onBack = { finish() })
                }
            }
        }
    }
}
