package com.example.islandlyrics.feature.logviewer

import android.content.Context
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.feature.logviewer.miuix.MiuixLogViewerScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.feature.logviewer.material.LogViewerScreen
import com.example.islandlyrics.ui.theme.material.AppTheme
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            if (isMiuixEnabled(this@LogViewerActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixLogViewerScreen(
                            onBack = { finish() }
                        )
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        LogViewerScreen(
                            onBack = { finish() }
                        )
                    }
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


