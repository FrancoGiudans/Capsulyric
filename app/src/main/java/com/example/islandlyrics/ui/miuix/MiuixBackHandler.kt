package com.example.islandlyrics.ui.miuix

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun MiuixBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val context = LocalContext.current
    val activityDispatcher = (context as? ComponentActivity)?.onBackPressedDispatcher
    
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }
    
    SideEffect {
        callback.isEnabled = enabled
    }
    
    DisposableEffect(activityDispatcher) {
        activityDispatcher?.addCallback(callback)
        onDispose {
            callback.remove()
        }
    }
}
