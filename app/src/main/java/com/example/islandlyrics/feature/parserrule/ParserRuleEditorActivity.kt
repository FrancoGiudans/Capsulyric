package com.example.islandlyrics.feature.parserrule

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.feature.parserrule.material.ParserRuleEditorScreen
import com.example.islandlyrics.feature.parserrule.miuix.MiuixParserRuleEditorScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.IslandLyricsMaterialTheme

class ParserRuleEditorActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val suggestedName = intent.getStringExtra(EXTRA_SUGGESTED_NAME)
        val initialRule = when {
            !packageName.isNullOrBlank() -> {
                ParserRuleHelper.getRuleForPackage(this, packageName)
                    ?: ParserRuleHelper.createDefaultRule(packageName).copy(customName = suggestedName)
            }
            else -> ParserRuleHelper.createDefaultRule("").copy(customName = suggestedName)
        }

        setContent {
            if (isMiuixEnabled(this@ParserRuleEditorActivity)) {
                MiuixAppTheme {
                    MiuixParserRuleEditorScreen(
                        initialRule = initialRule,
                        isNewRule = packageName.isNullOrBlank() || ParserRuleHelper.getRuleForPackage(this@ParserRuleEditorActivity, packageName) == null,
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
            } else {
                IslandLyricsMaterialTheme {
                    ParserRuleEditorScreen(
                        initialRule = initialRule,
                        isNewRule = packageName.isNullOrBlank() || ParserRuleHelper.getRuleForPackage(this@ParserRuleEditorActivity, packageName) == null,
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

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_SUGGESTED_NAME = "suggested_name"
    }
}
