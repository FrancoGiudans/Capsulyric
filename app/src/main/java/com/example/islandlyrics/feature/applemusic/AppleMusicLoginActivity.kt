/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
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
 *  *
 *  *
 */

package com.example.islandlyrics.feature.applemusic

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.feature.applemusic.material.AppleMusicWebLoginScreen
import com.example.islandlyrics.feature.applemusic.miuix.MiuixAppleMusicWebLoginScreen
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.theme.material.AppTheme

/**
 * 应用内 WebView 登录 Apple Music，捕获 media-user-token。
 * 登录成功后由 [AppleMusicWebLoginHelper] 自动保存，无需手动粘贴。
 */
class AppleMusicLoginActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (isMiuixEnabled(this)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixAppleMusicWebLoginScreen(onBack = { finish() }, onDone = { finish() })
                    }
                }
            } else {
                AppTheme {
                    PredictiveBackActivity {
                        AppleMusicWebLoginScreen(onBack = { finish() }, onDone = { finish() })
                    }
                }
            }
        }
    }
}
