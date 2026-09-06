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

data class SettingsSearchResult(
    val item: SettingsSearchItem,
    val title: String,
    val summary: String?,
    val breadcrumbText: String,
    val score: Int
)

object SettingsSearchEngine {

    fun search(context: Context, query: String): List<SettingsSearchResult> {
        return search(
            stringResolver = { resId -> context.getString(resId) },
            isVisible = { item -> item.isVisible(context) },
            query = query
        )
    }

    fun search(
        stringResolver: (Int) -> String,
        isVisible: (SettingsSearchItem) -> Boolean = { true },
        query: String
    ): List<SettingsSearchResult> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val allItems = SettingsSearchRegistry.getAllItems()
        val results = mutableListOf<SettingsSearchResult>()

        for (item in allItems) {
            if (!isVisible(item)) continue

            val title = try { stringResolver(item.titleRes) } catch (_: Exception) { "" }
            val summary = item.summaryRes?.let {
                try { stringResolver(it) } catch (_: Exception) { null }
            }
            val breadcrumbText = item.breadcrumbResList.mapNotNull { resId ->
                try { stringResolver(resId) } catch (_: Exception) { null }
            }.joinToString(" > ")

            val lowerTitle = title.lowercase()
            val lowerSummary = summary?.lowercase() ?: ""
            val lowerBreadcrumb = breadcrumbText.lowercase()

            var score = 0

            // 1. 标题匹配
            if (lowerTitle == q) {
                score += 100
            } else if (lowerTitle.startsWith(q)) {
                score += 80
            } else if (lowerTitle.contains(q)) {
                score += 60
            }

            // 2. 同义词与缩写匹配
            if (item.keywords.any { it.equals(q, ignoreCase = true) }) {
                score += 70
            } else if (item.keywords.any { it.contains(q, ignoreCase = true) || q.contains(it, ignoreCase = true) }) {
                score += 50
            }

            // 3. 摘要说明匹配
            if (lowerSummary.isNotEmpty() && lowerSummary.contains(q)) {
                score += 30
            }

            // 4. 面包屑路径匹配
            if (lowerBreadcrumb.isNotEmpty() && lowerBreadcrumb.contains(q)) {
                score += 20
            }

            if (score > 0) {
                results.add(
                    SettingsSearchResult(
                        item = item,
                        title = title,
                        summary = summary,
                        breadcrumbText = breadcrumbText,
                        score = score
                    )
                )
            }
        }

        return results.sortedWith(compareByDescending<SettingsSearchResult> { it.score }
            .thenBy { it.title.length })
    }
}
