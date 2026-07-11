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
import androidx.activity.compose.setContent
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.feature.parserrule.material.ParserRuleEditorScreen
import com.example.islandlyrics.feature.parserrule.miuix.MiuixParserRuleEditorScreen
import com.example.islandlyrics.ui.navigation.ActivityTransitionStyle
import com.example.islandlyrics.ui.navigation.BaseActivity
import com.example.islandlyrics.ui.navigation.PredictiveBackActivity
import com.example.islandlyrics.ui.miuix.theme.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme
import com.example.islandlyrics.R

class ParserRuleEditorActivity : BaseActivity() {

    override fun activityTransitionStyle() = ActivityTransitionStyle.OverlaySheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val suggestedName = intent.getStringExtra(EXTRA_SUGGESTED_NAME)
        val existingRule = if (!packageName.isNullOrBlank()) {
            ParserRuleHelper.loadRules(this).firstOrNull { it.packageName == packageName }
        } else {
            null
        }
        val initialRule = when {
            !packageName.isNullOrBlank() -> {
                existingRule
                    ?: ParserRuleHelper.createDefaultRule(this, packageName).copy(customName = suggestedName)
            }
            else -> ParserRuleHelper.createDefaultRule(this, "").copy(customName = suggestedName)
        }

        setContent {
            if (isMiuixEnabled(this@ParserRuleEditorActivity)) {
                MiuixAppTheme {
                    PredictiveBackActivity(
                        closeEnterTransition = R.anim.overlay_sheet_close_enter,
                        closeExitTransition = R.anim.overlay_sheet_close_exit
                    ) {
                        MiuixParserRuleEditorScreen(
                            initialRule = initialRule,
                            isNewRule = packageName.isNullOrBlank() || existingRule == null,
                            onBack = { finish() },
                            onDelete = { rule ->
                                val rules = ParserRuleHelper.loadRules(this@ParserRuleEditorActivity).toMutableList()
                                rules.removeAll { it.packageName == rule.packageName }
                                ParserRuleHelper.saveRules(this@ParserRuleEditorActivity, rules)
                                finish()
                            },
                            onSaved = { rule ->
                                val rules = ParserRuleHelper.loadRules(this@ParserRuleEditorActivity).toMutableList()
                                val index = rules.indexOfFirst { it.packageName == rule.packageName }
                                if (index >= 0) {
                                    rules[index] = rule
                                } else {
                                    rules.add(rule)
                                }
                                rules.sort()
                                ParserRuleHelper.saveRules(this@ParserRuleEditorActivity, rules)
                                finish()
                            }
                        )
                    }
                }
            } else {
                IslandLyricsMaterialTheme {
                    PredictiveBackActivity(
                        closeEnterTransition = R.anim.overlay_sheet_close_enter,
                        closeExitTransition = R.anim.overlay_sheet_close_exit
                    ) {
                        ParserRuleEditorScreen(
                            initialRule = initialRule,
                            isNewRule = packageName.isNullOrBlank() || existingRule == null,
                            onBack = { finish() },
                            onDelete = { rule ->
                                val rules = ParserRuleHelper.loadRules(this@ParserRuleEditorActivity).toMutableList()
                                rules.removeAll { it.packageName == rule.packageName }
                                ParserRuleHelper.saveRules(this@ParserRuleEditorActivity, rules)
                                finish()
                            },
                            onSaved = { rule ->
                                val rules = ParserRuleHelper.loadRules(this@ParserRuleEditorActivity).toMutableList()
                                val index = rules.indexOfFirst { it.packageName == rule.packageName }
                                if (index >= 0) {
                                    rules[index] = rule
                                } else {
                                    rules.add(rule)
                                }
                                rules.sort()
                                ParserRuleHelper.saveRules(this@ParserRuleEditorActivity, rules)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_SUGGESTED_NAME = "suggested_name"
    }
}


