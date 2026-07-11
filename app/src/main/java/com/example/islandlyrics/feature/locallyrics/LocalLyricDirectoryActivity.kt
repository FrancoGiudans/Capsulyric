/*
 *
 *  * Copyright (c) 2026 Franco Giudance
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

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


