package com.example.islandlyrics.feature.cache

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.cache.material.CacheManagementScreen
import com.example.islandlyrics.feature.cache.miuix.MiuixCacheManagementScreen
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class CacheManagementActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixCacheManagementScreen(onBack = { finish() })
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        CacheManagementScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}


