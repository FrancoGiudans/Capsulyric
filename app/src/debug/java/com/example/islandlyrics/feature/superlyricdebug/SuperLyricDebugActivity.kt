package com.example.islandlyrics.feature.superlyricdebug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import com.example.islandlyrics.lyrics.source.SuperLyricTrace
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.theme.material.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SuperLyricDebugActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme { MiuixSuperLyricDebugScreen(onBack = { finish() }) }
            } else {
                AppTheme { SuperLyricDebugScreen(onBack = { finish() }) }
            }
        }
    }
}

internal class SuperLyricDebugViewModel : ViewModel() {
    val entries = SuperLyricTrace.entries
    private val mutableCapturing = MutableStateFlow(true)
    val capturing = mutableCapturing.asStateFlow()

    init { SuperLyricTrace.capture(this, true) }

    fun capture(enabled: Boolean) {
        mutableCapturing.value = enabled
        SuperLyricTrace.capture(this, enabled)
    }

    fun clear() = SuperLyricTrace.clear()

    override fun onCleared() { SuperLyricTrace.capture(this, false) }
}
