package com.example.islandlyrics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class LogViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            if (isMiuixEnabled(this@LogViewerActivity)) {
                MiuixAppTheme {
                    MiuixLogViewerScreen(
                        onBack = { finish() }
                    )
                }
            } else {
                AppTheme {
                    LogViewerScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LogViewerActivity::class.java))
        }
    }
}
