package com.example.islandlyrics.feature.cache

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.cache.material.CacheManagementScreen
import com.example.islandlyrics.feature.cache.miuix.MiuixCacheManagementScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class CacheManagementActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    MiuixCacheManagementScreen(onBack = { finish() })
                }
            } else {
                AppTheme {
                    CacheManagementScreen(onBack = { finish() })
                }
            }
        }
    }
}
