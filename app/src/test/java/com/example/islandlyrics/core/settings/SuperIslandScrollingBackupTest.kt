package com.example.islandlyrics.core.settings

import android.content.SharedPreferences
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferenceMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SuperIslandScrollingBackupTest {
    @Test
    fun fullBackup_restoresOriginalScrollingChoiceWhileFullLyricsRemainSelected() {
        val allLeaves = BackupCategories.ALL_CATEGORIES.flatMap { category ->
            if (category.subGroups.isEmpty()) listOf(category.id) else category.subGroups.map { it.id }
        }.toSet()
        for (originalDisabled in listOf(false, true)) {
            assertRoundTrip(allLeaves, originalDisabled, legacy = false)
        }
    }

    @Test
    fun capsuleStyleBackup_keepsForcedStateAndOriginalValueTogether() {
        for (originalDisabled in listOf(false, true)) {
            assertRoundTrip(setOf("capsule_style"), originalDisabled, legacy = false)
        }
    }

    @Test
    fun capsuleStyleBackup_keepsLegacyForcedStateReadable() {
        for (originalDisabled in listOf(false, true)) {
            assertRoundTrip(setOf("capsule_style"), originalDisabled, legacy = true)
        }
    }

    @Test
    fun smallIslandProgress_migratesExpandedPreferenceOnceThenRemainsIndependent() {
        val values = mutableMapOf<String, Any>(
            AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR to false
        )
        val prefs = inMemoryPreferences(values)

        assertFalse(AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs))
        assertFalse(prefs.getBoolean(AppPreferences.Keys.SUPER_ISLAND_SMALL_ISLAND_SHOW_PROGRESS_BAR, true))

        prefs.edit().putBoolean(AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR, true).apply()
        assertFalse(AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs))
    }

    @Test
    fun smallIslandProgress_advancedMigrationPreservesRingForBothExpandedValues() {
        for (style in listOf("advanced_beta", "advanced_lyrics_dual")) {
            for (expanded in listOf(false, true)) {
                val values = mutableMapOf<String, Any>(
                    AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE to style,
                    AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR to expanded
                )
                val prefs = inMemoryPreferences(values)
                assertTrue("$style / expanded=$expanded", AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs))
                prefs.edit().putBoolean(AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR, !expanded).apply()
                assertTrue(AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs))
            }
        }
    }

    @Test
    fun smallIslandProgress_explicitChoiceWinsOverStyleAndExpandedProgress() {
        for (style in listOf("standard", "advanced_beta", "advanced_lyrics_dual")) {
            for (collapsed in listOf(false, true)) {
                val values = mutableMapOf<String, Any>(
                    AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE to style,
                    AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR to !collapsed,
                    AppPreferences.Keys.SUPER_ISLAND_SMALL_ISLAND_SHOW_PROGRESS_BAR to collapsed
                )
                assertEquals(collapsed, AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(inMemoryPreferences(values)))
            }
        }
    }

    @Test
    fun smallIslandProgress_defaultsAndEnabledStandardMigrationKeepRing() {
        assertTrue(AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(inMemoryPreferences(mutableMapOf())))
        val values = mutableMapOf<String, Any>(
            AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE to "standard",
            AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR to true
        )
        val prefs = inMemoryPreferences(values)
        assertTrue(AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs))
        prefs.edit().putBoolean(AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR, false).apply()
        assertTrue(AppPreferences.isSuperIslandSmallIslandProgressBarEnabled(prefs))
    }

    @Test
    fun capsuleLayoutBackup_includesIndependentCollapsedProgressPreferences() {
        val keys = setOf(
            AppPreferences.Keys.SUPER_ISLAND_SMALL_ISLAND_SHOW_PROGRESS_BAR,
            AppPreferences.Keys.SUPER_ISLAND_COLLAPSED_RIGHT_PROGRESS_RING,
            AppPreferences.Keys.SUPER_ISLAND_TEMPLATE18_TEXT_MODES,
            AppPreferences.Keys.SUPER_ISLAND_TEMPLATE18_BUTTON_ACTION
        )

        val selected = BackupCategories.collectKeysForLeafIds(keys, setOf("capsule_layout"))

        assertTrue(selected.containsAll(keys))
    }

    private fun assertRoundTrip(leaves: Set<String>, originalDisabled: Boolean, legacy: Boolean) {
        val forcedKey = if (legacy) {
            "full_super_island_forced_disable_scrolling"
        } else {
            "super_island_lyric_mode_forced_disable_scrolling"
        }
        val backupKey = if (legacy) {
            "disable_lyric_scrolling_before_full_super_island"
        } else {
            "disable_lyric_scrolling_before_super_island_lyric_mode"
        }
        val source = mapOf<String, Any>(
            "capsule_render_mode" to "xiaomi_super_island",
            "super_island_lyric_mode" to "full",
            "disable_lyric_scrolling" to true,
            forcedKey to true,
            backupKey to originalDisabled,
            "super_island_notification_logic_v2_migrated" to true
        )
        val selectedKeys = BackupCategories.collectKeysForLeafIds(source.keys, leaves)
        val restored = source.filterKeys { it in selectedKeys }.toMutableMap()
        val prefs = inMemoryPreferences(restored)

        assertTrue("Forced marker omitted from $leaves", prefs.getBoolean(forcedKey, false))
        assertTrue("Original scrolling choice omitted from $leaves", prefs.contains(backupKey))
        assertEquals(originalDisabled, prefs.getBoolean(backupKey, !originalDisabled))

        SuperIslandPreferenceMigration.migrate(prefs)
        assertEquals("full", prefs.getString("super_island_lyric_mode", null))
        assertEquals(originalDisabled, prefs.getBoolean("disable_lyric_scrolling", !originalDisabled))
        assertFalse(prefs.contains(forcedKey))
        assertFalse(prefs.contains(backupKey))

        // A later user choice must survive subsequent migrations.
        restored["disable_lyric_scrolling"] = !originalDisabled
        SuperIslandPreferenceMigration.migrate(prefs)
        assertEquals(!originalDisabled, prefs.getBoolean("disable_lyric_scrolling", originalDisabled))
    }

    @Test
    fun importedForcedState_restoresWithoutRestartingOrChangingModes() {
        val values = mutableMapOf<String, Any>(
            "super_island_lyric_mode" to "full",
            "disable_lyric_scrolling" to true,
            "super_island_lyric_mode_forced_disable_scrolling" to true,
            "disable_lyric_scrolling_before_super_island_lyric_mode" to false
        )
        val prefs = inMemoryPreferences(values)

        SuperIslandPreferenceMigration.restoreLegacyScrollingPreference(prefs)

        assertFalse(prefs.getBoolean("disable_lyric_scrolling", true))
        assertEquals("full", prefs.getString("super_island_lyric_mode", null))
        assertFalse(prefs.contains("super_island_lyric_mode_forced_disable_scrolling"))
    }

    @Test
    fun template18Migration_restoresScrollingAndKeepsSingleButtonForBothMigrationStates() {
        for (alreadyMigrated in listOf(false, true)) {
            for (legacy in listOf(false, true)) {
                for (originalDisabled in listOf(false, true)) {
                    val forcedKey = if (legacy) {
                        "full_super_island_forced_disable_scrolling"
                    } else {
                        "super_island_lyric_mode_forced_disable_scrolling"
                    }
                    val backupKey = if (legacy) {
                        "disable_lyric_scrolling_before_full_super_island"
                    } else {
                        "disable_lyric_scrolling_before_super_island_lyric_mode"
                    }
                    val values = mutableMapOf<String, Any>(
                        "super_island_notification_logic_v2_migrated" to alreadyMigrated,
                        AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE to "template18",
                        AppPreferences.Keys.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT to "no_button",
                        AppPreferences.Keys.SUPER_ISLAND_LYRIC_MODE to "full",
                        AppPreferences.Keys.DISABLE_LYRIC_SCROLLING to true,
                        forcedKey to true,
                        backupKey to originalDisabled
                    )
                    val prefs = inMemoryPreferences(values)

                    repeat(2) {
                        SuperIslandPreferenceMigration.migrate(prefs)

                        assertEquals("standard", prefs.getString(AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE, null))
                        assertEquals("single_button", prefs.getString(AppPreferences.Keys.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT, null))
                        assertEquals("full", prefs.getString(AppPreferences.Keys.SUPER_ISLAND_LYRIC_MODE, null))
                        assertEquals(originalDisabled, prefs.getBoolean(AppPreferences.Keys.DISABLE_LYRIC_SCROLLING, !originalDisabled))
                        assertFalse(prefs.contains(forcedKey))
                        assertFalse(prefs.contains(backupKey))
                    }
                }
            }
        }
    }

    private fun inMemoryPreferences(values: MutableMap<String, Any>): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putBoolean", "putString" -> {
                    values[args!![0] as String] = args[1]!!
                    editor
                }
                "remove" -> {
                    values.remove(args!![0] as String)
                    editor
                }
                "apply" -> null
                "commit" -> true
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean", "getString" -> values[args!![0] as String] ?: args[1]
                "contains" -> values.containsKey(args!![0] as String)
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
