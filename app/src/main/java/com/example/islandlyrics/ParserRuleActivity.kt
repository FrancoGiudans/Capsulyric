package com.example.islandlyrics

import androidx.activity.compose.setContent
import android.os.Bundle

class ParserRuleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use Compose
        setContent {
            AppTheme {
                ParserRuleScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}


// Adapter
