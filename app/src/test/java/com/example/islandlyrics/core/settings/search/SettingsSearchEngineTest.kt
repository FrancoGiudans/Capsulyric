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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
