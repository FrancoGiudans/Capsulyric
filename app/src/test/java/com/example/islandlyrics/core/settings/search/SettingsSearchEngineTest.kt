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
 *
 *
 */

package com.example.islandlyrics.core.settings.search

import android.content.Context
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.customsettings.CustomSettingsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class SettingsSearchEngineTest {

    @Test
    fun testRegistryItemIntegrity_uniqueIdsAndValidResources() {
        val allItems = SettingsSearchRegistry.getAllItems()
        assertTrue("Settings registry should not be empty", allItems.isNotEmpty())

        val ids = mutableSetOf<String>()
        for (item in allItems) {
            assertFalse("Duplicate item id found: ${item.id}", ids.contains(item.id))
            ids.add(item.id)
            assertTrue("Title resource should be positive for ${item.id}", item.titleRes > 0)
            assertTrue("Breadcrumbs should not be empty for ${item.id}", item.breadcrumbResList.isNotEmpty())
        }
    }

    @Test
    fun testSearchEngine_emptyQueryReturnsEmpty() {
        val result = SettingsSearchEngine.search(
            stringResolver = { "mock_str" },
            query = ""
        )
        assertTrue(result.isEmpty())
        val whitespaceResult = SettingsSearchEngine.search(
            stringResolver = { "mock_str" },
            query = "   "
        )
        assertTrue(whitespaceResult.isEmpty())
    }

    @Test
    fun testSearchEngine_keywordMatch() {
        val map = mapOf(
            R.string.settings_card_blur to "高级材质",
            R.string.page_title_personalization to "个性化",
            R.string.tab_app_ui to "界面"
        )

        // "毛玻璃" is a keyword in pref_card_blur
        val results = SettingsSearchEngine.search(
            stringResolver = { map[it] ?: "mock_$it" },
            query = "毛玻璃"
        )
        assertTrue("Should find results for '毛玻璃'", results.isNotEmpty())
        val topMatch = results.first()
        assertEquals("pref_card_blur", topMatch.item.id)
        assertEquals("高级材质", topMatch.title)
    }

    @Test
    fun testSearchEngine_titleMatch() {
        val map = mapOf(
            R.string.settings_capsule_notification to "胶囊与通知",
            R.string.settings_core_header to "核心"
        )

        val results = SettingsSearchEngine.search(
            stringResolver = { map[it] ?: "mock_$it" },
            query = "胶囊与通知"
        )
        assertTrue(results.isNotEmpty())
        assertEquals("page_capsule_notification", results.first().item.id)
    }

    @Test
    fun testSearchEngine_filtersByUiVariant() {
        val miuixResults = SettingsSearchEngine.search(
            stringResolver = { "mock_$it" },
            query = "毛玻璃",
            variant = SettingsUiVariant.MIUIX
        )
        assertTrue(miuixResults.any { it.item.id == "pref_card_blur" })

        val materialResults = SettingsSearchEngine.search(
            stringResolver = { "mock_$it" },
            query = "毛玻璃",
            variant = SettingsUiVariant.MATERIAL
        )
        assertTrue(materialResults.none { it.item.id == "pref_card_blur" })

        val materialOnlyResults = SettingsSearchEngine.search(
            stringResolver = { "mock_$it" },
            query = "Material 模糊",
            variant = SettingsUiVariant.MATERIAL
        )
        assertTrue(materialOnlyResults.any { it.item.id == "pref_material_page_blur" })
        assertTrue(
            SettingsSearchEngine.search(
                stringResolver = { "mock_$it" },
                query = "Material 模糊",
                variant = SettingsUiVariant.MIUIX
            ).none { it.item.id == "pref_material_page_blur" }
        )
    }

    @Test
    fun lyricVisualCenterSearch_opensTheLabSetting() {
        val item = SettingsSearchRegistry.getAllItems()
            .first { it.id == "pref_lyric_visual_center" }

        assertTrue(SettingsUiVariant.MIUIX in item.supportedVariants)
        assertTrue(SettingsUiVariant.MATERIAL in item.supportedVariants)
        assertEquals(
            "key_lyric_visual_center",
            (item.action as SettingsSearchAction.Navigate).targetItemKey
        )

        val results = SettingsSearchEngine.search(
            stringResolver = { "mock_$it" },
            isVisible = { true },
            query = "歌词居中",
            variant = SettingsUiVariant.MIUIX
        )
        assertTrue(results.any { it.item.id == item.id })
    }

    @Test
    fun testMainSettingsItemsNavigateToMainSettings() {
        val mainSettingIds = setOf(
            "pref_recommend_media_app",
            "pref_new_playing_app_alert",
            "pref_hide_recents"
        )
        val items = SettingsSearchRegistry.getAllItems()
            .filter { it.id in mainSettingIds }

        assertEquals(mainSettingIds, items.map { it.id }.toSet())
        items.forEach { item ->
            val action = item.action as SettingsSearchAction.Navigate
            assertEquals(SettingsNavigationTarget.MAIN_SETTINGS, action.target)
        }
    }

    @Test
    fun testSearchRegistryFixes_tabsKeysVariantsAndSummaries() {
        val itemsById = SettingsSearchRegistry.getAllItems().associateBy { it.id }

        val dualLine = itemsById.getValue("pref_super_island_dual_line_mode").action as SettingsSearchAction.Navigate
        assertEquals(SettingsNavigationTarget.CAPSULE_NOTIFICATION, dualLine.target)
        assertEquals(CustomSettingsTab.NOTIFICATION, dualLine.tab)
        assertEquals("key_super_island_dual_line_mode", dualLine.targetItemKey)

        val blockXmsf = itemsById.getValue("pref_block_xmsf").action as SettingsSearchAction.Navigate
        assertEquals(SettingsNavigationTarget.CAPSULE_NOTIFICATION, blockXmsf.target)
        assertEquals(CustomSettingsTab.CAPSULE, blockXmsf.tab)
        assertEquals("key_block_xmsf", blockXmsf.targetItemKey)

        val pureBlack = itemsById.getValue("pref_theme_pure_black")
        assertTrue(SettingsUiVariant.MATERIAL in pureBlack.supportedVariants)
        assertFalse(SettingsUiVariant.MIUIX in pureBlack.supportedVariants)

        val homeLyricPreview = itemsById.getValue("pref_home_lyric_preview")
        assertNull(homeLyricPreview.summaryRes)

        val language = itemsById.getValue("pref_language").action as SettingsSearchAction.Navigate
        assertEquals(SettingsNavigationTarget.MAIN_SETTINGS, language.target)
        assertEquals("key_language", language.targetItemKey)

        val desktopItems = SettingsSearchRegistry.getAllItems().filter { it.id.startsWith("pref_desktop_lyrics_") }
        assertTrue(desktopItems.size >= 11)
        desktopItems.forEach { item ->
            val nav = item.action as SettingsSearchAction.Navigate
            assertEquals(SettingsNavigationTarget.DESKTOP_LYRICS, nav.target)
            assertFalse("Missing targetItemKey on ${item.id}", nav.targetItemKey.isNullOrBlank())
        }

        val importOtherApps = itemsById.getValue("dialog_import_other_apps").action as SettingsSearchAction.TriggerDialog
        assertEquals(MainSettingsDialogType.IMPORT_OTHER_APPS, importOtherApps.dialog)

        val offlineMode = itemsById.getValue("pref_full_offline_mode").action as SettingsSearchAction.Navigate
        assertEquals(SettingsNavigationTarget.LAB, offlineMode.target)
        assertEquals("key_full_offline_mode", offlineMode.targetItemKey)
    }
}
