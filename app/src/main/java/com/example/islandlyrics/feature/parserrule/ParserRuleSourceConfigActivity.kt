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

package com.example.islandlyrics.feature.parserrule

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import com.example.islandlyrics.R
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.feature.parserrule.material.ParserRuleSourceConfigScreen
import com.example.islandlyrics.feature.parserrule.miuix.MiuixParserRuleSourceConfigScreen
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme

class ParserRuleSourceConfigActivity : BaseActivity() {
    private lateinit var packageName: String
    private lateinit var configType: ParserRuleSourceConfigType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        configType = intent.getStringExtra(EXTRA_CONFIG_TYPE)
            ?.let { runCatching { ParserRuleSourceConfigType.valueOf(it) }.getOrNull() }
            ?: ParserRuleSourceConfigType.ONLINE

        if (packageName.isBlank()) {
            Toast.makeText(this, getString(R.string.dialog_enter_pkg), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val initialRule = ParserRuleHelper.getRuleForPackage(this, packageName)
            ?: ParserRuleHelper.loadRules(this).firstOrNull { it.packageName == packageName }
            ?: ParserRuleHelper.createDefaultRule(this, packageName)

        setContent {
            if (isMiuixEnabled(this@ParserRuleSourceConfigActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity {
                        MiuixParserRuleSourceConfigScreen(
                            configType = configType,
                            initialRule = initialRule,
                            onBack = { finish() },
                            onStateChange = ::saveSourceState
                        )
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    PredictiveBackActivity {
                        ParserRuleSourceConfigScreen(
                            configType = configType,
                            initialRule = initialRule,
                            onBack = { finish() },
                            onStateChange = ::saveSourceState
                        )
                    }
                }
            }
        }
    }

    private fun saveSourceState(state: ParserRuleEditorState) {
        ParserRuleHelper.updateRule(this, packageName) { current ->
            current.copy(
                usesCarProtocol = state.usesCarProtocol,
                separatorPattern = state.separator,
                fieldOrder = state.fieldOrder,
                useOnlineLyrics = state.useOnlineLyrics,
                useSmartOnlineLyricSelection = state.useSmartOnlineLyricSelection,
                useRawMetadataForOnlineMatching = state.useRawMetadataForOnlineMatching,
                receiveOnlineTranslation = state.receiveOnlineTranslation,
                receiveOnlineRomanization = state.receiveOnlineRomanization,
                onlineLyricProviderOrder = state.onlineLyricProviderOrder.map { it.id },
                receiveLyriconTranslation = state.receiveLyriconTranslation,
                receiveLyriconRomanization = state.receiveLyriconRomanization
            )
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_CONFIG_TYPE = "config_type"
    }
}


