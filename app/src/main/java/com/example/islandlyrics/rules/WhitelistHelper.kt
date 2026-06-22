package com.example.islandlyrics.rules

import android.content.Context
import com.example.islandlyrics.core.settings.AppPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

object WhitelistHelper {

    private val DEFAULTS = hashSetOf(
        "com.tencent.qqmusic",
        "com.miui.player",
        "com.netease.cloudmusic"
    )

    fun loadWhitelist(context: Context): MutableList<WhitelistItem> {
        val prefs = AppPreferences.of(context)
        val items = ArrayList<WhitelistItem>()

        if (prefs.contains(AppPreferences.Keys.WHITELIST_JSON)) {
            // New Format
            val json = prefs.getString(AppPreferences.Keys.WHITELIST_JSON, "[]")
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
        } else if (prefs.contains(AppPreferences.Keys.WHITELIST_PACKAGES_LEGACY)) {
            // Migrate
            val oldSet = prefs.getStringSet(AppPreferences.Keys.WHITELIST_PACKAGES_LEGACY, HashSet()) ?: HashSet()
            for (pkg in oldSet) {
                items.add(WhitelistItem(pkg, true))
            }
            saveWhitelist(context, items) // Verify migration
            prefs.edit().remove(AppPreferences.Keys.WHITELIST_PACKAGES_LEGACY).apply() // Clean up
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
        val prefs = AppPreferences.of(context)
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
        prefs.edit().putString(AppPreferences.Keys.WHITELIST_JSON, array.toString()).apply()
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
