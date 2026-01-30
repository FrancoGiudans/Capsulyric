package com.example.islandlyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

object WhitelistHelper {

    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val PREF_WHITELIST_OLD = "whitelist_packages"
    private const val PREF_WHITELIST_JSON = "whitelist_json"

    private val DEFAULTS = hashSetOf(
        "com.tencent.qqmusic",
        "com.miui.player",
        "com.netease.cloudmusic"
    )

    fun loadWhitelist(context: Context): MutableList<WhitelistItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val items = ArrayList<WhitelistItem>()

        if (prefs.contains(PREF_WHITELIST_JSON)) {
            // New Format
            val json = prefs.getString(PREF_WHITELIST_JSON, "[]")
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    items.add(
                        WhitelistItem(
                            obj.getString("pkg"),
                            obj.optBoolean("enabled", true)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (prefs.contains(PREF_WHITELIST_OLD)) {
            // Migrate
            val oldSet = prefs.getStringSet(PREF_WHITELIST_OLD, HashSet()) ?: HashSet()
            for (pkg in oldSet) {
                items.add(WhitelistItem(pkg, true))
            }
            saveWhitelist(context, items) // Verify migration
            prefs.edit().remove(PREF_WHITELIST_OLD).apply() // Clean up
        } else {
            // Defaults
            for (pkg in DEFAULTS) {
                items.add(WhitelistItem(pkg, true))
            }
            saveWhitelist(context, items)
        }

        Collections.sort(items)
        return items
    }

    fun saveWhitelist(context: Context, items: List<WhitelistItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in items) {
            try {
                val obj = JSONObject()
                obj.put("pkg", item.packageName)
                obj.put("enabled", item.isEnabled)
                array.put(obj)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        prefs.edit().putString(PREF_WHITELIST_JSON, array.toString()).apply()
    }

    // Helper for Service: Get only Enabled packages
    fun getEnabledPackages(context: Context): Set<String> {
        val items = loadWhitelist(context)
        val enabled = HashSet<String>()
        for (item in items) {
            if (item.isEnabled) {
                enabled.add(item.packageName)
            }
        }
        return enabled
    }
}
