package com.example.islandlyrics

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class FAQActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // Re-use app theme to ensure consistent colors/fonts
            AppTheme {
                FAQScreen(onBack = { finish() })
            }
        }
    }
}
