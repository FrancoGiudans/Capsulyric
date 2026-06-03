package com.example.islandlyrics.core.settings

import android.content.Context
import org.json.JSONArray

/**
 * Defines all backup-able data categories with their key patterns.
 * Every coarse category has sub-groups for fine-grained selection.
 *
 * Display labels are resolved at the UI layer via string resources keyed by "backup_cat_<id>".
 */
object BackupCategories {

    data class Category(
        val id: String,
        /** Exact keys or key prefixes to match. Trailing "*" means prefix match. */
        val keyPatterns: List<String>,
        /** Sub-groups for fine-grained selection. */
        val subGroups: List<SubGroup>
    )

    data class SubGroup(
        val id: String,
        val keyPatterns: List<String>,
        /** Optional display label override for dynamic sub-groups (e.g. parser rules per-app). */
        val labelOverride: String? = null
    )

    /** All keys excluded from backup/restore (applied after category matching). */
    val EXCLUDED_KEYS = setOf("is_setup_complete")

    // ═══════════════════════════════════════════════════════════════════════
    // 1. 胶囊 (Capsule / Super Island)
    // ═══════════════════════════════════════════════════════════════════════

    val CAPSULE = Category(
        id = "capsule",
        keyPatterns = listOf("super_island_*"),
        subGroups = listOf(
            SubGroup("capsule_style", listOf(
                "super_island_notification_style", "super_island_lyric_mode",
                "super_island_enabled"
            )),
            SubGroup("capsule_layout", listOf(
                "super_island_full_lyric_show_left_cover",
                "super_island_media_button_layout",
                "super_island_text_limit_*"
            )),
            SubGroup("capsule_color_share", listOf(
                "super_island_text_color_enabled",
                "super_island_color_source",
                "super_island_share_enabled",
                "super_island_share_format"
            ))
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 2. 通知 (Notifications)
    // ═══════════════════════════════════════════════════════════════════════

    val NOTIFICATIONS = Category(
        id = "notifications",
        keyPatterns = listOf(
            "notification_*", "dynamic_icon_*", "floating_*",
            "progress_bar_color_enabled", "oneui_capsule_color_mode"
        ),
        subGroups = listOf(
            SubGroup("notif_behavior", listOf(
                "notification_actions_style", "notification_click_style",
                "notification_dismiss_delay"
            )),
            SubGroup("notif_dynamic_icon", listOf(
                "dynamic_icon_enabled", "dynamic_icon_style"
            )),
            SubGroup("notif_floating", listOf(
                "floating_*"
            )),
            SubGroup("notif_appearance", listOf(
                "progress_bar_color_enabled", "oneui_capsule_color_mode"
            ))
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 3. 应用外观 (Appearance)
    // ═══════════════════════════════════════════════════════════════════════

    val APPEARANCE = Category(
        id = "appearance",
        keyPatterns = listOf("theme_*", "card_blur_enabled", "ui_use_miuix"),
        subGroups = listOf(
            SubGroup("appearance_theme", listOf("theme_*")),
            SubGroup("appearance_blur", listOf("card_blur_enabled")),
            SubGroup("appearance_ui_style", listOf("ui_use_miuix"))
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 4. 通用设置 (General)
    // ═══════════════════════════════════════════════════════════════════════

    val GENERAL = Category(
        id = "general",
        keyPatterns = listOf(
            "language_code", "recommend_media_app",
            "hide_recents_enabled", "predictive_back_enabled",
            "service_enabled",
            "lyric_text_display_mode", "disable_lyric_scrolling"
        ),
        subGroups = listOf(
            SubGroup("general_language", listOf("language_code")),
            SubGroup("general_behavior", listOf(
                "recommend_media_app", "hide_recents_enabled",
                "predictive_back_enabled", "service_enabled"
            )),
            SubGroup("general_lyric_display", listOf(
                "lyric_text_display_mode", "disable_lyric_scrolling"
            ))
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 5. 解析规则 (Parser Rules)
    // ═══════════════════════════════════════════════════════════════════════

    val PARSER_RULES = Category(
        id = "parser_rules",
        keyPatterns = listOf("parser_rules_json"),
        subGroups = listOf(
            SubGroup("parser_all", listOf("parser_rules_json"))
        )
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 6. 高级功能 (Advanced)
    // ═══════════════════════════════════════════════════════════════════════

    val ADVANCED = Category(
        id = "advanced",
        keyPatterns = listOf(
            "lab_*", "fully_offline_mode",
            "dev_mode_enabled", "log_record_level",
            "lyric_directories", "whitelist_json",
            "local_lyric_custom_matches", "local_lyric_export_dir",
            "local_lyric_export_match_sync"
        ),
        subGroups = listOf(
            SubGroup("advanced_lab", listOf("lab_*")),
            SubGroup("advanced_offline", listOf("fully_offline_mode")),
            SubGroup("advanced_dev", listOf("dev_mode_enabled", "log_record_level")),
            SubGroup("advanced_lyric_dir", listOf(
                "lyric_directories", "whitelist_json",
                "local_lyric_custom_matches", "local_lyric_export_dir",
                "local_lyric_export_match_sync"
            ))
        )
    )

    // ── Flat list ──────────────────────────────────────────────────────

    val ALL_CATEGORIES: List<Category> = listOf(
        CAPSULE, NOTIFICATIONS, APPEARANCE, GENERAL, PARSER_RULES, ADVANCED
    )

    // ── Key → Category lookup ──────────────────────────────────────────

    private val keyToCategory: Map<String, Category> by lazy {
        val map = mutableMapOf<String, Category>()
        for (cat in ALL_CATEGORIES) {
            for (pattern in cat.keyPatterns) {
                map[pattern] = cat
            }
            for (sub in cat.subGroups) {
                for (pattern in sub.keyPatterns) {
                    map[pattern] = cat
                }
            }
        }
        map
    }

    fun categoryForKey(key: String): Category? {
        return keyToCategory.entries.firstOrNull { (pattern, _) ->
            matchesPattern(key, pattern)
        }?.value
    }

    /** Check whether [key] matches [pattern] (exact or prefix with "*"). */
    fun matchesPattern(key: String, pattern: String): Boolean {
        return if (pattern.endsWith("*")) {
            key.startsWith(pattern.removeSuffix("*"))
        } else {
            key == pattern
        }
    }

    /** Collect all patterns from the given category IDs (including sub-group patterns). */
    fun allPatternsFor(selectedCategoryIds: Set<String>): List<String> {
        return ALL_CATEGORIES
            .filter { it.id in selectedCategoryIds }
            .flatMap { cat -> cat.keyPatterns + cat.subGroups.flatMap { it.keyPatterns } }
    }

    /**
     * Like [allPatternsFor] but accepts a mix of category IDs and subGroup IDs.
     * - Category IDs: include all patterns (category + all sub-groups).
     * - SubGroup IDs: include only that sub-group's patterns.
     * - Special handling for dynamic parser IDs (parser_com_xxx).
     */
    fun patternsForLeafIds(leafIds: Set<String>): List<String> {
        val result = mutableListOf<String>()
        for (cat in ALL_CATEGORIES) {
            if (cat.subGroups.isNotEmpty()) {
                val selectedSubs = cat.subGroups.filter { it.id in leafIds }
                if (selectedSubs.size == cat.subGroups.size) {
                    result.addAll(cat.keyPatterns)
                }
                for (sub in selectedSubs) {
                    result.addAll(sub.keyPatterns)
                }

                // Special handling for parser_rules: dynamic parser IDs (parser_com_xxx)
                // should also include parser_rules_json
                if (cat.id == "parser_rules" && leafIds.any { it.startsWith("parser_") }) {
                    result.addAll(cat.keyPatterns)  // Add "parser_rules_json"
                }
            } else if (cat.id in leafIds) {
                result.addAll(cat.keyPatterns)
            }
        }
        return result
    }

    /** Collect all keys from SharedPreferences that belong to the given leaf IDs. */
    fun collectKeysForLeafIds(
        allPrefKeys: Set<String>,
        leafIds: Set<String>
    ): Set<String> {
        val patterns = patternsForLeafIds(leafIds)
        return allPrefKeys.filter { key ->
            if (key in EXCLUDED_KEYS) return@filter false
            patterns.any { pattern -> matchesPattern(key, pattern) }
        }.toSet()
    }

    /** Collect all keys from SharedPreferences that belong to the given category IDs. */
    fun collectKeysForCategories(
        allPrefKeys: Set<String>,
        selectedCategoryIds: Set<String>
    ): Set<String> {
        val patterns = allPatternsFor(selectedCategoryIds)
        return allPrefKeys.filter { key ->
            if (key in EXCLUDED_KEYS) return@filter false
            patterns.any { pattern -> matchesPattern(key, pattern) }
        }.toSet()
    }

    /**
     * For import preview: parse a flat v1 or structured v2 JSON and return
     * which category IDs are present along with their key counts.
     */
    fun detectCategoriesFromJson(prefsJson: org.json.JSONObject): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val keys = mutableListOf<String>()
        val iterator = prefsJson.keys()
        while (iterator.hasNext()) keys.add(iterator.next())

        for (cat in ALL_CATEGORIES) {
            val count = keys.count { key ->
                if (key in EXCLUDED_KEYS) return@count false
                cat.keyPatterns.any { p -> matchesPattern(key, p) } ||
                    cat.subGroups.any { sg -> sg.keyPatterns.any { p -> matchesPattern(key, p) } }
            }
            if (count > 0) result[cat.id] = count
        }
        return result
    }

    // ── Dynamic parser rule sub-groups ─────────────────────────────────

    private const val PREF_PARSER_RULES = "parser_rules_json"

    /**
     * Read current parser rules from SharedPreferences and return one SubGroup per app.
     * SubGroup ID format: "parser_<packageName>" (dots replaced with underscores).
     * Each subGroup's keyPatterns contains only ["parser_rules_json"] — filtering happens
     * at the JSON value level in [SettingsBackupManager].
     */
    fun parserAppSubGroups(context: Context): List<SubGroup> {
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREF_PARSER_RULES, null) ?: return listOf(
            SubGroup("parser_all", listOf(PREF_PARSER_RULES))
        )
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val pkg = obj.getString("pkg")
                val name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else pkg
                val subId = "parser_" + pkg.replace('.', '_')
                SubGroup(subId, listOf(PREF_PARSER_RULES), labelOverride = name)
            }
        } catch (_: Exception) {
            listOf(SubGroup("parser_all", listOf(PREF_PARSER_RULES)))
        }
    }

    /**
     * Parse parser rules from a JSON string and return one SubGroup per app.
     * Used for import preview to show apps in the backup file, not just device apps.
     */
    fun parserAppSubGroupsFromJson(jsonString: String?): List<SubGroup> {
        if (jsonString.isNullOrEmpty()) return listOf(
            SubGroup("parser_all", listOf(PREF_PARSER_RULES))
        )
        return try {
            val array = JSONArray(jsonString)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("pkg", "")
                if (pkg.isEmpty()) return@map null
                val name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else pkg
                val subId = "parser_" + pkg.replace('.', '_')
                SubGroup(subId, listOf(PREF_PARSER_RULES), labelOverride = name)
            }.filterNotNull()
        } catch (_: Exception) {
            listOf(SubGroup("parser_all", listOf(PREF_PARSER_RULES)))
        }
    }

    /**
     * Filter a parser_rules_json string to only include rules for the given leaf IDs.
     * Leaf IDs should be "parser_<packageName>" format.
     */
    fun filterParserRulesJson(json: String, selectedLeafIds: Set<String>): String {
        val prefix = "parser_"
        val selectedPkgs = selectedLeafIds
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).replace('_', '.') }
            .toSet()
        if (selectedPkgs.isEmpty()) return json
        return try {
            val array = JSONArray(json)
            val filtered = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getString("pkg") in selectedPkgs) {
                    filtered.put(obj)
                }
            }
            filtered.toString()
        } catch (_: Exception) {
            json
        }
    }

    /**
     * Merge imported parser rules JSON into the existing one.
     * Imported apps replace existing ones with the same package name.
     */
    fun mergeParserRulesJson(context: Context, importedJson: String) {
        val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
        val existingJson = prefs.getString(PREF_PARSER_RULES, "[]") ?: "[]"
        try {
            val existing = JSONArray(existingJson)
            val imported = JSONArray(importedJson)
            val byPkg = mutableMapOf<String, Int>() // pkg → index in existing
            for (i in 0 until existing.length()) {
                val obj = existing.getJSONObject(i)
                val pkg = obj.optString("pkg", "")
                if (pkg.isNotEmpty()) {
                    byPkg[pkg] = i
                }
            }
            for (i in 0 until imported.length()) {
                val obj = imported.getJSONObject(i)
                val pkg = obj.optString("pkg", "")
                if (pkg.isEmpty()) continue // Skip invalid entries
                val idx = byPkg[pkg]
                if (idx != null) {
                    existing.put(idx, obj)
                } else {
                    existing.put(obj)
                }
            }
            prefs.edit().putString(PREF_PARSER_RULES, existing.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("BackupCategories", "Failed to merge parser rules", e)
        }
    }
}
