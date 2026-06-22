package com.example.islandlyrics.feature.parserrule

import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import androidx.activity.compose.setContent
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.feature.parserrule.miuix.MiuixParserRuleScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.feature.parserrule.material.ParserRuleScreen
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import android.os.Bundle

class ParserRuleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            if (isMiuixEnabled(this@ParserRuleActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixParserRuleScreen(onBack = { finish() })
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    PredictiveBackActivity {
                        ParserRuleScreen(onBack = { finish() })
                    }
                }
            }
        }
    }
}


