package com.example.islandlyrics.feature.lab

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.lab.material.LabScreen
import com.example.islandlyrics.feature.lab.miuix.MiuixLabScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.common.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class LabActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixLabScreen(onBack = { finish() })
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        LabScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}
