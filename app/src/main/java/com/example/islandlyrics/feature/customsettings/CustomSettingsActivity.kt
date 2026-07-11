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

package com.example.islandlyrics.feature.customsettings

import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import android.os.Bundle
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.core.update.UpdateChecker
import com.example.islandlyrics.feature.customsettings.miuix.MiuixCustomSettingsScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.feature.customsettings.material.CustomSettingsScreen
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource

class CustomSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this@CustomSettingsActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixCustomSettingsScreen(
                            onBack = { finish() },
                            onCheckUpdate = { /* No-op */ },
                            onShowLogs = { /* No-op */ },
                            updateVersionText = "",
                            updateBuildText = ""
                        )
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    PredictiveBackActivity {
                        CustomSettingsScreen(
                            onBack = { finish() },
                            onCheckUpdate = { /* No-op or reuse UpdateChecker if needed */ },
                            onShowLogs = { /* No-op */ },
                            updateVersionText = "", // Not used in this screen
                            updateBuildText = "" // Not used in this screen
                        )
                    }
                }
            }
        }
    }
}


