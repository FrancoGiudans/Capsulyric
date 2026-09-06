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
import androidx.core.content.edit
import org.json.JSONArray

object SettingsSearchHistoryStore {
    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val KEY_SEARCH_HISTORY = "settings_search_history_json"
    private const val MAX_HISTORY_ITEMS = 8

    @Synchronized
    fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)?.trim()
                if (!item.isNullOrEmpty() && item !in list) {
                    list.add(item)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addHistory(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        val current = getHistory(context).toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > MAX_HISTORY_ITEMS) {
            current.subList(MAX_HISTORY_ITEMS, current.size).clear()
        }

        saveList(context, current)
    }

    @Synchronized
    fun removeHistory(context: Context, query: String) {
        val trimmed = query.trim()
        val current = getHistory(context).toMutableList()
        if (current.remove(trimmed)) {
            saveList(context, current)
        }
    }

    @Synchronized
    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_SEARCH_HISTORY) }
    }

    private fun saveList(context: Context, list: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(item)
        }
        prefs.edit { putString(KEY_SEARCH_HISTORY, jsonArray.toString()) }
    }
}
