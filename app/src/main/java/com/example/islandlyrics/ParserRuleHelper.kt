package com.example.islandlyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * Helper for managing notification parser rules.
 * Similar to WhitelistHelper but for parser configurations.
 */
object ParserRuleHelper {

    private const val PREFS_NAME = "IslandLyricsPrefs"
    private const val PREF_PARSER_RULES = "parser_rules_json"

    /**
     * Default rules for common music apps.
     * Apps with native notification lyric support have usesCarProtocol=true.
     * Apps requiring superlyricapi have usesCarProtocol=false.
     */
    private val DEFAULTS = listOf(
        // Native notification lyric support (car/bluetooth protocol)
        ParserRule("com.tencent.qqmusic", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.ARTIST_TITLE),
        ParserRule("com.netease.cloudmusic", enabled=true, usesCarProtocol=true, separatorPattern=" - ", fieldOrder=FieldOrder.ARTIST_TITLE),
        ParserRule("com.miui.player", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.ARTIST_TITLE),
        
        // Require superlyricapi or other methods (car protocol disabled by default)
        ParserRule("com.kugou.android", enabled=true, usesCarProtocol=false, separatorPattern="-", fieldOrder=FieldOrder.ARTIST_TITLE),
        ParserRule("com.apple.android.music", enabled=true, usesCarProtocol=false, separatorPattern=" - ", fieldOrder=FieldOrder.ARTIST_TITLE)
    )

    /**
     * Load all parser rules from SharedPreferences.
     * Returns defaults on first run.
     */
    fun loadRules(context: Context): MutableList<ParserRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rules = ArrayList<ParserRule>()

        if (prefs.contains(PREF_PARSER_RULES)) {
            // Load existing rules
            val json = prefs.getString(PREF_PARSER_RULES, "[]")
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    rules.add(
                        ParserRule(
                            packageName = obj.getString("pkg"),
                            enabled = obj.optBoolean("enabled", true),
                            usesCarProtocol = obj.optBoolean("usesCarProtocol", true),
                            separatorPattern = obj.optString("separator", "-"),
                            fieldOrder = FieldOrder.valueOf(obj.optString("fieldOrder", "ARTIST_TITLE"))
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fall back to defaults on parse error
                return DEFAULTS.toMutableList()
            }
        } else {
            // First run: initialize with defaults
            rules.addAll(DEFAULTS)
            saveRules(context, rules)
        }

        Collections.sort(rules)
        return rules
    }

    /**
     * Save parser rules to SharedPreferences as JSON.
     */
    fun saveRules(context: Context, rules: List<ParserRule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        
        for (rule in rules) {
            try {
                val obj = JSONObject()
                obj.put("pkg", rule.packageName)
                obj.put("enabled", rule.enabled)
                obj.put("usesCarProtocol", rule.usesCarProtocol)
                obj.put("separator", rule.separatorPattern)
                obj.put("fieldOrder", rule.fieldOrder.name)
                array.put(obj)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        prefs.edit().putString(PREF_PARSER_RULES, array.toString()).apply()
    }

    /**
     * Get parser rule for a specific package.
     * Returns null if no rule exists or rule is disabled.
     */
    fun getRuleForPackage(context: Context, packageName: String): ParserRule? {
        val rules = loadRules(context)
        return rules.find { it.packageName == packageName && it.enabled }
    }

    /**
     * Get all enabled packages (for whitelist integration).
     */
    fun getEnabledPackages(context: Context): Set<String> {
        val rules = loadRules(context)
        return rules.filter { it.enabled }.map { it.packageName }.toSet()
    }
}
