package com.example.islandlyrics.core.settings

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class LyricVisualCenterPreferenceTest {
    @Test
    fun visualCenterPreference_defaultsOnAndPersistsChanges() {
        val values = mutableMapOf<String, Any>()
        val prefs = inMemoryPreferences(values)

        assertTrue(LabFeatureManager.isLyricVisualCenterEnabled(prefs))

        LabFeatureManager.setLyricVisualCenterEnabled(prefs, false)
        assertFalse(LabFeatureManager.isLyricVisualCenterEnabled(prefs))

        LabFeatureManager.setLyricVisualCenterEnabled(prefs, true)
        assertTrue(LabFeatureManager.isLyricVisualCenterEnabled(prefs))
    }

    @Test
    fun visualCenterPreference_isIncludedInAdvancedLabBackup() {
        assertTrue(
            BackupCategories.ADVANCED.keyPatterns.any { pattern ->
                BackupCategories.matchesPattern(
                    LabFeatureManager.KEY_LYRIC_VISUAL_CENTER_ENABLED,
                    pattern
                )
            }
        )
    }

    private fun inMemoryPreferences(values: MutableMap<String, Any>): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    values[args!![0] as String] = args[1] as Boolean
                    editor
                }
                "remove" -> {
                    values.remove(args!![0] as String)
                    editor
                }
                "clear" -> {
                    values.clear()
                    editor
                }
                "commit" -> true
                "apply" -> null
                "toString" -> "InMemoryPreferencesEditor"
                "hashCode" -> System.identityHashCode(editor)
                "equals" -> editor === args?.firstOrNull()
                else -> null
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args!![0] as String] as? Boolean ?: args[1] as Boolean
                "contains" -> values.containsKey(args!![0] as String)
                "getAll" -> values.toMap()
                "edit" -> editor
                "toString" -> "InMemoryPreferences"
                "hashCode" -> System.identityHashCode(values)
                "equals" -> args?.firstOrNull() === values
                else -> null
            }
        } as SharedPreferences
    }
}
