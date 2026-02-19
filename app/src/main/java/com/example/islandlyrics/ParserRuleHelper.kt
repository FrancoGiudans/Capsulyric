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
        ParserRule("com.tencent.qqmusic", customName="QQ Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.ARTIST_TITLE),
        ParserRule("com.netease.cloudmusic", customName="NetEase Cloud Music", enabled=true, usesCarProtocol=true, separatorPattern=" - ", fieldOrder=FieldOrder.ARTIST_TITLE),
        ParserRule("com.miui.player", customName="Mi Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.ARTIST_TITLE),
        
        // Require superlyricapi or other methods (car protocol disabled by default)
        ParserRule("com.kugou.android", customName="KuGou Music", enabled=true, usesCarProtocol=false, separatorPattern="-", fieldOrder=FieldOrder.ARTIST_TITLE),
        ParserRule("com.apple.android.music", customName="Apple Music", enabled=true, usesCarProtocol=false, separatorPattern=" - ", fieldOrder=FieldOrder.ARTIST_TITLE)
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
                    // Backward compatibility: "name" might not exist, defaults to null (which is fine)
                    val name = if (obj.has("name")) obj.getString("name") else null
                    
                    rules.add(
                        ParserRule(
                            packageName = obj.getString("pkg"),
                            customName = name,
                            enabled = obj.optBoolean("enabled", true),
                            usesCarProtocol = obj.optBoolean("usesCarProtocol", true),
                            separatorPattern = obj.optString("separator", "-"),
                            fieldOrder = FieldOrder.valueOf(obj.optString("fieldOrder", "ARTIST_TITLE")),
                            useOnlineLyrics = obj.optBoolean("useOnlineLyrics", false),
                            useSuperLyricApi = obj.optBoolean("useSuperLyricApi", true)
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
                obj.put("name", rule.customName)
                obj.put("enabled", rule.enabled)
                obj.put("usesCarProtocol", rule.usesCarProtocol)
                obj.put("separator", rule.separatorPattern)
                obj.put("fieldOrder", rule.fieldOrder.name)
                obj.put("useOnlineLyrics", rule.useOnlineLyrics)
                obj.put("useSuperLyricApi", rule.useSuperLyricApi)
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
     * Get display name for a package.
     * Returns custom name if set, otherwise package name (or unknown).
     * If rule doesn't exist, returns package name.
     */
    fun getAppNameForPackage(context: Context, packageName: String): String {
        val rule = loadRules(context).find { it.packageName == packageName }
        return rule?.customName ?: packageName
    }

    /**
     * Get all enabled packages (for whitelist integration).
     */
    fun getEnabledPackages(context: Context): Set<String> {
        val rules = loadRules(context)
        return rules.filter { it.enabled }.map { it.packageName }.toSet()
    }
    
    /**
     * Create a transient default rule for a package that doesn't have one.
     * Default to: Online Lyrics ENABLED, SuperLyric ENABLED.
     */
    fun createDefaultRule(packageName: String): ParserRule {
        return ParserRule(
            packageName = packageName,
            customName = null,
            enabled = true,
            usesCarProtocol = true, // Assume car protocol by default as it's common
            separatorPattern = "-",
            fieldOrder = FieldOrder.ARTIST_TITLE,
            useOnlineLyrics = true, // ENABLE ONLINE LYRICS BY DEFAULT
            useSuperLyricApi = true
        )
    }
    /**
     * Parse notification text using configurable separator and field order.
     * @param input Raw text like "Artist-Title" or "Title | Artist"
     * @param rule Parser rule with separator and field order
     * @return Triple(title, artist, isSuccess)
     */
    fun parseWithRule(input: String, rule: ParserRule): Triple<String, String, Boolean> {
        val separator = rule.separatorPattern
        val splitIndex = input.indexOf(separator)
        
        if (splitIndex == -1) {
            return Triple(input, "", false)
        }

        // Split the input
        val part1 = input.substring(0, splitIndex).trim()
        val part2 = input.substring(splitIndex + separator.length).trim()

        // Apply field order
        return when (rule.fieldOrder) {
            FieldOrder.ARTIST_TITLE -> Triple(part2, part1, true)  // part1=artist, part2=title
            FieldOrder.TITLE_ARTIST -> Triple(part1, part2, true)  // part1=title, part2=artist
        }
    }
}
