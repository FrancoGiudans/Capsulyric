package com.example.islandlyrics.feature.parserrule

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.islandlyrics.R
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.ParserRuleHelper
import com.example.islandlyrics.feature.parserrule.material.ParserRuleSourceConfigScreen
import com.example.islandlyrics.feature.parserrule.miuix.MiuixParserRuleSourceConfigScreen
import com.example.islandlyrics.ui.common.BaseActivity
import com.example.islandlyrics.ui.miuix.MiuixAppTheme
import com.example.islandlyrics.ui.miuix.isMiuixEnabled
import com.example.islandlyrics.ui.theme.material.AppTheme

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
            ?: ParserRuleHelper.createDefaultRule(packageName)

        setContent {
            if (isMiuixEnabled(this@ParserRuleSourceConfigActivity)) {
                MiuixAppTheme {
                    MiuixParserRuleSourceConfigScreen(
                        configType = configType,
                        initialRule = initialRule,
                        onBack = { finish() },
                        onStateChange = ::saveSourceState
                    )
                }
            } else {
                val prefs = getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                val followSystem = prefs.getBoolean("theme_follow_system", true)
                val darkMode = prefs.getBoolean("theme_dark_mode", false)
                val pureBlack = prefs.getBoolean("theme_pure_black", false)
                val dynamicColor = prefs.getBoolean("theme_dynamic_color", true)
                val isSystemDark = isSystemInDarkTheme()
                val useDarkTheme = if (followSystem) isSystemDark else darkMode

                AppTheme(
                    darkTheme = useDarkTheme,
                    dynamicColor = dynamicColor,
                    pureBlack = pureBlack && useDarkTheme
                ) {
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
