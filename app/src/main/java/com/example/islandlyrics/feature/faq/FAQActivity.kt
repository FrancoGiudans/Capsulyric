package com.example.islandlyrics.feature.faq

import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.feature.faq.miuix.MiuixFAQScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.feature.faq.material.FAQScreen
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import android.os.Bundle
import androidx.activity.compose.setContent

class FAQActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            if (isMiuixEnabled(this@FAQActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixFAQScreen(onBack = { finish() })
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    PredictiveBackActivity {
                        FAQScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}


