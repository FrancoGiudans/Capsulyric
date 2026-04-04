package com.example.islandlyrics.data

import android.content.Context
import com.example.islandlyrics.data.lyric.OnlineLyricProvider
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

    // ── In-memory cache ──
    // keyed by packageName; null value means "rule exists but is disabled".
    // cacheValid is set to false whenever saveRules() is called so that
    // the next access re-reads from SharedPreferences.
    private val ruleCache = HashMap<String, ParserRule?>()
    private var cacheValid = false

    private fun invalidateCache() {
        cacheValid = false
        ruleCache.clear()
    }

    /**
     * Default rules for common music apps.
     * Apps with native notification lyric support have usesCarProtocol=true.
     * Apps requiring superlyricapi have usesCarProtocol=false.
     */
    private val DEFAULTS = listOf(
        // Native notification lyric support (car/bluetooth protocol)
        ParserRule("com.tencent.qqmusic", customName="QQ Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIds(), useSuperLyricApi=false, useLyricGetterApi=false),
        ParserRule("com.netease.cloudmusic", customName="NetEase Cloud Music", enabled=true, usesCarProtocol=true, separatorPattern=" - ", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIds(), useSuperLyricApi=false, useLyricGetterApi=false),
        ParserRule("com.miui.player", customName="Mi Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIds(), useSuperLyricApi=false, useLyricGetterApi=false),
        
        // Require superlyricapi or other methods (car protocol disabled by default)
        ParserRule("com.kugou.android", customName="KuGou Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIds(), useSuperLyricApi=false, useLyricGetterApi=false),
        ParserRule("com.apple.android.music", customName="Apple Music", enabled=true, usesCarProtocol=false, separatorPattern=" - ", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIds(), useSuperLyricApi=true, useLyricGetterApi=false)
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
                    
                    val providerOrder = when {
                        obj.has("onlineLyricProviderOrder") -> {
                            val array = obj.optJSONArray("onlineLyricProviderOrder")
                            if (array != null) {
                                buildList {
                                    for (index in 0 until array.length()) {
                                        add(array.optString(index))
                                    }
                                }
                            } else {
                                OnlineLyricProvider.defaultIds()
                            }
                        }
                        else -> OnlineLyricProvider.defaultIds()
                    }

                    rules.add(
                        ParserRule(
                            packageName = obj.getString("pkg"),
                            customName = name,
                            enabled = obj.optBoolean("enabled", true),
                            usesCarProtocol = obj.optBoolean("usesCarProtocol", true),
                            separatorPattern = obj.optString("separator", "-"),
                            fieldOrder = FieldOrder.valueOf(obj.optString("fieldOrder", "ARTIST_TITLE")),
                            useOnlineLyrics = obj.optBoolean("useOnlineLyrics", false),
                            useSmartOnlineLyricSelection = obj.optBoolean("useSmartOnlineLyricSelection", true),
                            useRawMetadataForOnlineMatching = obj.optBoolean("useRawMetadataForOnlineMatching", false),
                            onlineLyricProviderOrder = OnlineLyricProvider.normalizeOrder(providerOrder).map { it.id },
                            useSuperLyricApi = obj.optBoolean("useSuperLyricApi", false),
                            useLyricGetterApi = obj.optBoolean("useLyricGetterApi", false)
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

        // Populate cache
        ruleCache.clear()
        for (rule in rules) {
            ruleCache[rule.packageName] = if (rule.enabled) rule else null
        }
        cacheValid = true

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
                obj.put("useSmartOnlineLyricSelection", rule.useSmartOnlineLyricSelection)
                obj.put("useRawMetadataForOnlineMatching", rule.useRawMetadataForOnlineMatching)
                obj.put("onlineLyricProviderOrder", JSONArray(OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder).map { it.id }))
                obj.put("useSuperLyricApi", rule.useSuperLyricApi)
                obj.put("useLyricGetterApi", rule.useLyricGetterApi)
                array.put(obj)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        prefs.edit().putString(PREF_PARSER_RULES, array.toString()).apply()
        // Rules changed on disk — invalidate cache so next access re-parses
        invalidateCache()
    }

    /**
     * Get parser rule for a specific package.
     * Returns null if no rule exists or rule is disabled.
     */
    fun getRuleForPackage(context: Context, packageName: String): ParserRule? {
        // Fast path: use in-memory cache if valid
        if (cacheValid) {
            return if (ruleCache.containsKey(packageName)) ruleCache[packageName] else null
        }
        // Cache miss: load from SharedPreferences (populates cache as a side-effect)
        loadRules(context)
        return if (ruleCache.containsKey(packageName)) ruleCache[packageName] else null
    }
    
    /**
     * Get display name for a package.
     * Returns custom name if set, otherwise package name (or unknown).
     * If rule doesn't exist, returns package name.
     */
    fun getAppNameForPackage(context: Context, packageName: String): String {
        val rule = getRuleForPackage(context, packageName)
        return rule?.customName ?: packageName
    }

    /**
     * Get all enabled packages (for whitelist integration).
     */
    fun getEnabledPackages(context: Context): Set<String> {
        if (!cacheValid) loadRules(context)
        return ruleCache.entries
            .filter { it.value != null }
            .map { it.key }
            .toSet()
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
            fieldOrder = FieldOrder.TITLE_ARTIST,
            useOnlineLyrics = false, // DISABLED BY DEFAULT
            useSmartOnlineLyricSelection = true,
            useRawMetadataForOnlineMatching = false,
            onlineLyricProviderOrder = OnlineLyricProvider.defaultIds(),
            useSuperLyricApi = false, // DISABLED BY DEFAULT
            useLyricGetterApi = false // DISABLED BY DEFAULT
        )
    }
    /**
     * Parse notification text using configurable separator and field order.
     * @param input Raw text like "Artist-Title" or "Title | Artist"
     * @param rule Parser rule with separator and field order
     * @return Triple(title, artist, isSuccess)
     */
    fun parseWithRule(input: String, rule: ParserRule): Triple<String, String, Boolean> {
        var separator = rule.separatorPattern
        
        // Smart separator fallback:
        // If the configured separator is strictly "-" but the string contains " - " (with spaces),
        // we prioritize the spaced version as it has a much lower false-positive rate.
        if (separator == "-" && input.contains(" - ")) {
            separator = " - "
        }
        
        val splitIndex = when (rule.fieldOrder) {
            // "Title-Artist" 更容易出现歌名中自带连字符，所以优先从最后一个分隔符切分
            FieldOrder.TITLE_ARTIST -> input.lastIndexOf(separator)
            // "Artist-Title" 场景下，歌手字段通常更短，优先从第一个分隔符切分
            FieldOrder.ARTIST_TITLE -> input.indexOf(separator)
        }
        
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
