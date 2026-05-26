package com.example.islandlyrics

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.feature.customsettings.CustomSettingsActivity
import com.example.islandlyrics.feature.settings.AboutActivity
import com.example.islandlyrics.feature.settings.SettingsActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreReleaseSmokeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        resetAppState()
    }

    @After
    fun tearDown() {
        resetAppState()
    }

    @Test
    fun materialCustomSettings_launches_withEnglishLocaleAndPredictiveBack() {
        configureLaunchState(
            useMiuix = false,
            languageCode = "en",
            predictiveBackEnabled = true
        )

        launchAndAssertLocale(CustomSettingsActivity::class.java, "en")
    }

    @Test
    fun materialAbout_launches_withChineseLocaleAndPredictiveBack() {
        configureLaunchState(
            useMiuix = false,
            languageCode = "zh-CN",
            predictiveBackEnabled = true
        )

        launchAndAssertLocale(AboutActivity::class.java, "zh")
    }

    @Test
    fun miuixCustomSettings_launches_withChineseLocaleAndPredictiveBack() {
        configureLaunchState(
            useMiuix = true,
            languageCode = "zh-CN",
            predictiveBackEnabled = true
        )

        launchAndAssertLocale(CustomSettingsActivity::class.java, "zh")
    }

    @Test
    fun miuixSettings_launches_withEnglishLocaleAndPredictiveBack() {
        configureLaunchState(
            useMiuix = true,
            languageCode = "en",
            predictiveBackEnabled = true
        )

        launchAndAssertLocale(SettingsActivity::class.java, "en")
    }

    private fun configureLaunchState(
        useMiuix: Boolean,
        languageCode: String,
        predictiveBackEnabled: Boolean
    ) {
        prefs.edit()
            .clear()
            .putBoolean("is_setup_complete", true)
            .putBoolean("ui_use_miuix", useMiuix)
            .putBoolean("predictive_back_enabled", predictiveBackEnabled)
            .putBoolean("theme_dynamic_color", false)
            .putBoolean("material_theme_dynamic_color", false)
            .putBoolean("card_blur_enabled", false)
            .commit()

        OfflineModeManager.setEnabled(context, true)
        instrumentation.runOnMainSync {
            ThemeHelper.setLanguage(context, languageCode)
            ThemeHelper.applyTheme(context)
        }
        instrumentation.waitForIdleSync()
    }

    private fun resetAppState() {
        prefs.edit().clear().commit()
        OfflineModeManager.setEnabled(context, true)
        instrumentation.runOnMainSync {
            ThemeHelper.setLanguage(context, "")
            ThemeHelper.applyTheme(context)
        }
        instrumentation.waitForIdleSync()
    }

    private fun <T : Activity> launchAndAssertLocale(activityClass: Class<T>, expectedLocalePrefix: String) {
        val scenario = ActivityScenario.launch<T>(Intent(context, activityClass))
        try {
            scenario.onActivity { activity: T ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
                val localeTag = activity.resources.configuration.locales[0].toLanguageTag()
                assertTrue(
                    "Expected locale starting with $expectedLocalePrefix but was $localeTag",
                    localeTag.startsWith(expectedLocalePrefix)
                )
            }
        } finally {
            scenario.close()
        }
    }
}
