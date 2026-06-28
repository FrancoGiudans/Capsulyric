package com.example.islandlyrics.feature.lastfm

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.lastfm.material.LastFmSettingsScreen
import com.example.islandlyrics.feature.lastfm.miuix.MiuixLastFmSettingsScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.theme.material.AppTheme

class LastFmSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixLastFmSettingsScreen(onBack = { finish() })
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        LastFmSettingsScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}
