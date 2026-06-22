package com.example.islandlyrics.rules

import android.content.Context
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper for managing notification parser rules.
 * Similar to WhitelistHelper but for parser configurations.
 */
object ParserRuleHelper {

    private const val PREF_PARSER_RULES = AppPreferences.Keys.PARSER_RULES_JSON
    const val PREF_PARSER_RULE_TEMPLATE = AppPreferences.Keys.PARSER_RULE_TEMPLATE_JSON
    private const val TEMPLATE_PACKAGE_NAME = "__parser_rule_template__"

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
        ParserRule("com.tencent.qqmusic", customName="QQ Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIdsForPackage("com.tencent.qqmusic"), useSuperLyricApi=false, useLyricGetterApi=false, useLyriconApi=false),
        ParserRule("com.netease.cloudmusic", customName="NetEase Cloud Music", enabled=true, usesCarProtocol=true, separatorPattern=" - ", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIdsForPackage("com.netease.cloudmusic"), useSuperLyricApi=false, useLyricGetterApi=false, useLyriconApi=false),
        ParserRule("com.miui.player", customName="Mi Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIdsForPackage("com.miui.player"), useSuperLyricApi=false, useLyricGetterApi=false, useLyriconApi=false),
        
        // Require superlyricapi or other methods (car protocol disabled by default)
        ParserRule("com.kugou.android", customName="KuGou Music", enabled=true, usesCarProtocol=true, separatorPattern="-", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIdsForPackage("com.kugou.android"), useSuperLyricApi=false, useLyricGetterApi=false, useLyriconApi=false),
        ParserRule("com.apple.android.music", customName="Apple Music", enabled=true, usesCarProtocol=false, separatorPattern=" - ", fieldOrder=FieldOrder.TITLE_ARTIST, onlineLyricProviderOrder = OnlineLyricProvider.defaultIdsForPackage("com.apple.android.music"), useSuperLyricApi=false, useLyricGetterApi=false, useLyriconApi=false)
    )

    /**
     * Load all parser rules from SharedPreferences.
     * Returns defaults on first run.
     */
    fun loadRules(context: Context): MutableList<ParserRule> {
        val prefs = AppPreferences.of(context)
        val rules = ArrayList<ParserRule>()

        if (prefs.contains(PREF_PARSER_RULES)) {
            // Load existing rules
            val json = prefs.getString(PREF_PARSER_RULES, "[]")
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val pkg = obj.getString("pkg")
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
                                OnlineLyricProvider.defaultIdsForPackage(pkg)
                            }
                        }
                        else -> OnlineLyricProvider.defaultIdsForPackage(pkg)
                    }

                    rules.add(
                        ParserRule(
                            packageName = pkg,
                            customName = name,
                            enabled = obj.optBoolean("enabled", true),
                            usesCarProtocol = obj.optBoolean("usesCarProtocol", true),
                            separatorPattern = obj.optString("separator", "-"),
                            fieldOrder = FieldOrder.valueOf(obj.optString("fieldOrder", "ARTIST_TITLE")),
                            useOnlineLyrics = obj.optBoolean("useOnlineLyrics", false),
                            useSmartOnlineLyricSelection = obj.optBoolean("useSmartOnlineLyricSelection", true),
                            useRawMetadataForOnlineMatching = obj.optBoolean("useRawMetadataForOnlineMatching", false),
                            receiveOnlineTranslation = obj.optBoolean("receiveOnlineTranslation", false),
                            receiveOnlineRomanization = obj.optBoolean("receiveOnlineRomanization", false),
                            onlineLyricProviderOrder = OnlineLyricProvider.normalizeOrder(providerOrder).map { it.id },
                            useSuperLyricApi = obj.optBoolean("useSuperLyricApi", false),
                            useLyricGetterApi = obj.optBoolean("useLyricGetterApi", false),
                            useLyriconApi = obj.optBoolean("useLyriconApi", false),
                            receiveLyriconTranslation = obj.optBoolean("receiveLyriconTranslation", false),
                            receiveLyriconRomanization = obj.optBoolean("receiveLyriconRomanization", false),
                            useLocalLyrics = obj.optBoolean("useLocalLyrics", false)
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

        rules.sort()

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
        val prefs = AppPreferences.of(context)
        val array = JSONArray()
        
        for (rule in rules) {
            try {
                array.put(ruleToJson(rule))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        prefs.edit { putString(PREF_PARSER_RULES, array.toString()) }
        // Rules changed on disk — invalidate cache so next access re-parses
        invalidateCache()
    }

    fun updateRule(context: Context, packageName: String, transform: (ParserRule) -> ParserRule): ParserRule? {
        if (packageName.isBlank()) return null
        val rules = loadRules(context).toMutableList()
        val index = rules.indexOfFirst { it.packageName == packageName }
        val current = if (index >= 0) rules[index] else createDefaultRule(context, packageName)
        val updated = transform(current).copy(packageName = packageName)
        if (index >= 0) {
            rules[index] = updated
        } else {
            rules.add(updated)
        }
        rules.sort()
        saveRules(context, rules)
        return updated
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

    fun hasEnabledSuperLyricRule(context: Context): Boolean {
        return loadRules(context).any { rule ->
            rule.enabled && rule.useSuperLyricApi
        }
    }

    fun hasEnabledLyriconRule(context: Context): Boolean {
        return loadRules(context).any { rule ->
            rule.enabled && rule.useLyriconApi
        }
    }
    
    /**
     * Create a transient default rule for a package that doesn't have one.
     * Default to: Online Lyrics ENABLED, SuperLyric ENABLED.
     */
    fun createDefaultRule(packageName: String): ParserRule {
        return builtInDefaultRule(packageName)
    }

    fun createDefaultRule(context: Context, packageName: String): ParserRule {
        val base = builtInDefaultRule(packageName)
        return applyTemplateToRule(base, loadDefaultTemplate(context))
    }

    fun loadDefaultTemplate(context: Context): ParserRule? {
        val prefs = AppPreferences.of(context)
        val json = prefs.getString(PREF_PARSER_RULE_TEMPLATE, null) ?: return null
        return try {
            ruleFromJson(JSONObject(json), TEMPLATE_PACKAGE_NAME)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun hasDefaultTemplate(context: Context): Boolean {
        return AppPreferences.of(context)
            .contains(PREF_PARSER_RULE_TEMPLATE)
    }

    fun saveDefaultTemplate(context: Context, template: ParserRule) {
        val cleanTemplate = template.copy(
            packageName = TEMPLATE_PACKAGE_NAME,
            customName = null,
            enabled = true
        )
        AppPreferences.of(context)
            .edit { putString(PREF_PARSER_RULE_TEMPLATE, ruleToJson(cleanTemplate).toString()) }
    }

    fun clearDefaultTemplate(context: Context) {
        AppPreferences.of(context)
            .edit { remove(PREF_PARSER_RULE_TEMPLATE) }
    }

    internal fun ruleToJson(rule: ParserRule): JSONObject {
        return JSONObject().apply {
            put("pkg", rule.packageName)
            put("name", rule.customName)
            put("enabled", rule.enabled)
            put("usesCarProtocol", rule.usesCarProtocol)
            put("separator", rule.separatorPattern)
            put("fieldOrder", rule.fieldOrder.name)
            put("useOnlineLyrics", rule.useOnlineLyrics)
            put("useSmartOnlineLyricSelection", rule.useSmartOnlineLyricSelection)
            put("useRawMetadataForOnlineMatching", rule.useRawMetadataForOnlineMatching)
            put("receiveOnlineTranslation", rule.receiveOnlineTranslation)
            put("receiveOnlineRomanization", rule.receiveOnlineRomanization)
            put(
                "onlineLyricProviderOrder",
                JSONArray(OnlineLyricProvider.normalizeOrder(rule.onlineLyricProviderOrder).map { it.id })
            )
            put("useSuperLyricApi", rule.useSuperLyricApi)
            put("useLyricGetterApi", rule.useLyricGetterApi)
            put("useLyriconApi", rule.useLyriconApi)
            put("receiveLyriconTranslation", rule.receiveLyriconTranslation)
            put("receiveLyriconRomanization", rule.receiveLyriconRomanization)
            put("useLocalLyrics", rule.useLocalLyrics)
        }
    }

    internal fun ruleFromJson(obj: JSONObject, fallbackPackageName: String? = null): ParserRule {
        val pkg = obj.optString("pkg", obj.optString("package", fallbackPackageName.orEmpty()))
        val name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else null
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
                    OnlineLyricProvider.defaultIdsForPackage(pkg)
                }
            }
            else -> OnlineLyricProvider.defaultIdsForPackage(pkg)
        }

        return ParserRule(
            packageName = pkg,
            customName = name,
            enabled = obj.optBoolean("enabled", true),
            usesCarProtocol = obj.optBoolean("usesCarProtocol", true),
            separatorPattern = obj.optString("separator", "-"),
            fieldOrder = runCatching {
                FieldOrder.valueOf(obj.optString("fieldOrder", "ARTIST_TITLE"))
            }.getOrDefault(FieldOrder.ARTIST_TITLE),
            useOnlineLyrics = obj.optBoolean("useOnlineLyrics", false),
            useSmartOnlineLyricSelection = obj.optBoolean("useSmartOnlineLyricSelection", true),
            useRawMetadataForOnlineMatching = obj.optBoolean("useRawMetadataForOnlineMatching", false),
            receiveOnlineTranslation = obj.optBoolean("receiveOnlineTranslation", false),
            receiveOnlineRomanization = obj.optBoolean("receiveOnlineRomanization", false),
            onlineLyricProviderOrder = OnlineLyricProvider.normalizeOrder(providerOrder).map { it.id },
            useSuperLyricApi = obj.optBoolean("useSuperLyricApi", false),
            useLyricGetterApi = obj.optBoolean("useLyricGetterApi", false),
            useLyriconApi = obj.optBoolean("useLyriconApi", false),
            receiveLyriconTranslation = obj.optBoolean("receiveLyriconTranslation", false),
            receiveLyriconRomanization = obj.optBoolean("receiveLyriconRomanization", false),
            useLocalLyrics = obj.optBoolean("useLocalLyrics", false)
        )
    }

    private fun builtInDefaultRule(packageName: String): ParserRule {
        return ParserRule(
            packageName = packageName,
            customName = null,
            enabled = true,
            usesCarProtocol = false,
            separatorPattern = "-",
            fieldOrder = FieldOrder.TITLE_ARTIST,
            useOnlineLyrics = true,
            useSmartOnlineLyricSelection = true,
            useRawMetadataForOnlineMatching = false,
            receiveOnlineTranslation = false,
            receiveOnlineRomanization = false,
            onlineLyricProviderOrder = OnlineLyricProvider.defaultIdsForPackage(packageName),
            useSuperLyricApi = false,
            useLyricGetterApi = false,
            useLyriconApi = false,
            receiveLyriconTranslation = false,
            receiveLyriconRomanization = false
        )
    }

    private fun applyTemplateToRule(rule: ParserRule, template: ParserRule?): ParserRule {
        if (template == null) return rule
        return rule.copy(
            usesCarProtocol = template.usesCarProtocol,
            separatorPattern = template.separatorPattern,
            fieldOrder = template.fieldOrder,
            useLocalLyrics = template.useLocalLyrics,
            useOnlineLyrics = template.useOnlineLyrics,
            useSmartOnlineLyricSelection = template.useSmartOnlineLyricSelection,
            useRawMetadataForOnlineMatching = template.useRawMetadataForOnlineMatching,
            receiveOnlineTranslation = template.receiveOnlineTranslation,
            receiveOnlineRomanization = template.receiveOnlineRomanization,
            onlineLyricProviderOrder = OnlineLyricProvider.normalizeOrder(template.onlineLyricProviderOrder).map { it.id },
            useSuperLyricApi = template.useSuperLyricApi,
            useLyricGetterApi = template.useLyricGetterApi,
            useLyriconApi = template.useLyriconApi,
            receiveLyriconTranslation = template.receiveLyriconTranslation,
            receiveLyriconRomanization = template.receiveLyriconRomanization
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

