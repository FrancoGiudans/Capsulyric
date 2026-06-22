package com.example.islandlyrics.feature.mediacontrol

import android.content.Intent
import android.os.Bundle
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.feature.mediacontrol.miuix.MiuixMediaControlDialog
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.feature.mediacontrol.material.MediaControlDialog
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.islandlyrics.ui.theme.material.AppTheme
import androidx.compose.runtime.mutableStateOf

class MediaControlActivity : AppCompatActivity() {
    private val showDialog = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure transparent system bars
        enableEdgeToEdge()
        
        setContent {
            val useMiuix = isMiuixEnabled(this)

            if (useMiuix) {
                MiuixAppTheme {
                    if (showDialog.value) {
                        MiuixMediaControlDialog(
                            show = showDialog.value,
                            onDismiss = {
                                showDialog.value = false
                                finish()
                            }
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
                        onDismiss = {
                            showDialog.value = false
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showDialog.value = true
    }
    
    private fun enableEdgeToEdge() {
        // Basic edge-to-edge logic if needed, but the Theme.Transparent handles most.
    }
}


