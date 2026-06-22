package com.example.islandlyrics.feature.locallyrics

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.locallyrics.material.LocalLyricDirectoryScreen
import com.example.islandlyrics.feature.locallyrics.miuix.MiuixLocalLyricDirectoryScreen
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

class LocalLyricDirectoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val directoryUri = intent.getStringExtra(EXTRA_DIRECTORY_URI)?.let { Uri.parse(it) }
        val directoryName = intent.getStringExtra(EXTRA_DIRECTORY_NAME) ?: "Lyrics"

        if (directoryUri == null) {
            finish()
            return
        }

        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixLocalLyricDirectoryScreen(
                            directoryUri = directoryUri,
                            directoryName = directoryName,
                            onBack = { finish() }
                        )
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        LocalLyricDirectoryScreen(
                            directoryUri = directoryUri,
                            directoryName = directoryName,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_DIRECTORY_URI = "directory_uri"
        const val EXTRA_DIRECTORY_NAME = "directory_name"
    }
}


