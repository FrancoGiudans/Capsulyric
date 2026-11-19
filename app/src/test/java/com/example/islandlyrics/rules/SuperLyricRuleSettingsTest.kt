package com.example.islandlyrics.rules

import com.example.islandlyrics.core.settings.SettingsBackupManager
import com.example.islandlyrics.feature.parserrule.toEditorState
import com.example.islandlyrics.feature.parserrule.toRule
import com.example.islandlyrics.feature.parserrule.withSourceSettingsFrom
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SuperLyricRuleSettingsTest {
    private fun rule(translation: Boolean, romanization: Boolean) = ParserRule(
        packageName = "com.apple.android.music", customName = "Apple Music", useSuperLyricApi = true,
        receiveSuperLyricTranslation = translation, receiveSuperLyricRomanization = romanization,
        receiveLyriconTranslation = true, receiveLyriconRomanization = false
    )

    @Test fun ruleJsonAndEditorRoundTripPreserveIndependentSuperLyricSwitches() {
        for (translation in listOf(false, true)) for (romanization in listOf(false, true)) {
            val original = rule(translation, romanization)
            val restored = ParserRuleHelper.ruleFromJson(ParserRuleHelper.ruleToJson(original))
                .toEditorState().toRule(original)
            assertEquals(translation, restored.receiveSuperLyricTranslation)
            assertEquals(romanization, restored.receiveSuperLyricRomanization)
            assertTrue(restored.useSuperLyricApi)
            assertTrue(restored.receiveLyriconTranslation)
            assertFalse(restored.receiveLyriconRomanization)
        }
    }

    @Test fun sourceSettingsRefreshKeepsUnsavedIdentityAndLoadsBothSwitches() {
        val editing = rule(false, false).toEditorState().copy(customName = "unsaved name")
        val refreshed = editing.withSourceSettingsFrom(rule(true, false).toEditorState())
        assertEquals("unsaved name", refreshed.customName)
        assertTrue(refreshed.receiveSuperLyricTranslation)
        assertFalse(refreshed.receiveSuperLyricRomanization)
    }

    @Test fun structuredRuleBackupRestoresBothSwitchesWithoutChangingLyricon() {
        for (translation in listOf(false, true)) for (romanization in listOf(false, true)) {
            val json = JSONArray().put(ParserRuleHelper.ruleToJson(rule(translation, romanization))).toString()
            val backup = SettingsBackupManager.parserRulesJsonToBackupArray(json)
            val fields = backup.getJSONObject(0).getJSONObject("translations")
            assertEquals(translation, fields.getBoolean("super_lyric_translation"))
            assertEquals(romanization, fields.getBoolean("super_lyric_romanization"))
            val restored = ParserRuleHelper.ruleFromJson(JSONArray(
                SettingsBackupManager.backupParserArrayToParserRulesJson(backup)).getJSONObject(0))
            assertEquals(translation, restored.receiveSuperLyricTranslation)
            assertEquals(romanization, restored.receiveSuperLyricRomanization)
            assertTrue(restored.receiveLyriconTranslation)
            assertFalse(restored.receiveLyriconRomanization)
        }
    }

    @Test fun defaultTemplateBackupPreservesSuperLyricSwitches() {
        val original = ParserRuleHelper.ruleToJson(rule(false, true)).toString()
        val backup = SettingsBackupManager.backupTemplateFromInternalJson(original)!!
        val restored = ParserRuleHelper.ruleFromJson(JSONObject(SettingsBackupManager.backupTemplateToInternalJson(backup)))
        assertFalse(restored.receiveSuperLyricTranslation)
        assertTrue(restored.receiveSuperLyricRomanization)
        assertTrue(restored.useSuperLyricApi)
    }

    @Test fun oldRulesAndOldStructuredBackupsDefaultNewSwitchesToOff() {
        val oldRule = JSONObject().put("pkg", "player").put("useSuperLyricApi", true)
        assertFalse(ParserRuleHelper.ruleFromJson(oldRule).receiveSuperLyricTranslation)
        assertFalse(ParserRuleHelper.ruleFromJson(oldRule).receiveSuperLyricRomanization)
        val oldBackup = JSONArray().put(JSONObject().put("package", "player")
            .put("apis", JSONObject().put("super_lyric", true)))
        val restored = ParserRuleHelper.ruleFromJson(JSONArray(
            SettingsBackupManager.backupParserArrayToParserRulesJson(oldBackup)).getJSONObject(0))
        assertTrue(restored.useSuperLyricApi)
        assertFalse(restored.receiveSuperLyricTranslation)
        assertFalse(restored.receiveSuperLyricRomanization)
    }
}
