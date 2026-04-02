package com.example.islandlyrics.feature.mediacontrol

import android.os.Bundle
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.feature.mediacontrol.miuix.MiuixMediaControlDialog
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.feature.mediacontrol.material.MediaControlDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.islandlyrics.ui.theme.material.AppTheme

class MediaControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure transparent system bars
        enableEdgeToEdge()
        
        setContent {
            val useMiuix = isMiuixEnabled(this)
            val showDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

            if (useMiuix) {
                MiuixAppTheme {
                    if (showDialog.value) {
                        MiuixMediaControlDialog(
                            show = showDialog.value,
                            onDismiss = { finish() }
                        )
                    } else {
                        finish()
                    }
                }
            } else {
                AppTheme(
                    darkTheme = true,
                    dynamicColor = true
                ) {
                    MediaControlDialog(
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
    
    private fun enableEdgeToEdge() {
        // Basic edge-to-edge logic if needed, but the Theme.Transparent handles most.
    }
}
