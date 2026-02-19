package com.example.islandlyrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.islandlyrics.AppTheme

class MediaControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure transparent system bars
        enableEdgeToEdge()
        
        setContent {
            AppTheme(
                darkTheme = true, // Force dark mode for controls usually looks better, or follow system
                dynamicColor = true
            ) {
                // Host the dialog directly. 
                // When dismissed, finish the activity.
                MediaControlDialog(
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }
    
    private fun enableEdgeToEdge() {
        // Basic edge-to-edge logic if needed, but the Theme.Transparent handles most.
    }
}
