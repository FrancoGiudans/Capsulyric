package com.example.islandlyrics.ui

import android.content.Context
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.miuix.MiuixLogViewerScreen
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.material.LogViewerScreen
import com.example.islandlyrics.ui.material.AppTheme
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
