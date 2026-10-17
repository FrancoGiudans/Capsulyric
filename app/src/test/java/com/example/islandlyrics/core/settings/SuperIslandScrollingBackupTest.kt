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
    fun fullBackup_restoresOriginalScrollingChoiceAfterLeavingFullLyrics() {
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
        assertTrue("Full Lyrics must retain the forced state", prefs.getBoolean(forcedKey, false))

        restored["super_island_lyric_mode"] = "standard"
        SuperIslandPreferenceMigration.migrate(prefs)

        assertEquals(originalDisabled, prefs.getBoolean("disable_lyric_scrolling", !originalDisabled))
        assertFalse(prefs.contains(forcedKey))
        assertFalse(prefs.contains(backupKey))
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
