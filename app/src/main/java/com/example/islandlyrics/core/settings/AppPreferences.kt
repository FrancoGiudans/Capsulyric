package com.example.islandlyrics.core.settings

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    const val NAME = "IslandLyricsPrefs"

    object Keys {
        const val NOTIFICATION_ACTIONS_STYLE = "notification_actions_style"
        const val NOTIFICATION_CLICK_STYLE = "notification_click_style"
        const val PROGRESS_BAR_COLOR_ENABLED = "progress_bar_color_enabled"
        const val DYNAMIC_ICON_STYLE = "dynamic_icon_style"
        const val DISABLE_LYRIC_SCROLLING = "disable_lyric_scrolling"
        const val SUPER_ISLAND_ENABLED_LEGACY = "super_island_enabled"
        const val SUPER_ISLAND_TEXT_COLOR_ENABLED = "super_island_text_color_enabled"
        const val SUPER_ISLAND_SHARE_ENABLED = "super_island_share_enabled"
        const val SUPER_ISLAND_SHARE_FORMAT = "super_island_share_format"
        const val SUPER_ISLAND_MEDIA_BUTTON_LAYOUT = "super_island_media_button_layout"
        const val SUPER_ISLAND_NOTIFICATION_STYLE = "super_island_notification_style"
        const val SUPER_ISLAND_LYRIC_MODE = "super_island_lyric_mode"
        const val SUPER_ISLAND_FULL_LYRIC_SHOW_LEFT_COVER = "super_island_full_lyric_show_left_cover"
        const val XMSF_NETWORK_MODE = "block_xmsf_network_mode"
        const val XMSF_NETWORK_LEGACY = "block_xmsf_network"
        const val XMSF_NETWORK_CUSTOM_DURATION_MS = "block_xmsf_network_custom_duration_ms"
        const val DEV_MODE_ENABLED = "dev_mode_enabled"
        const val DEBUG_FORCED_ROM_TYPE = "debug_forced_rom_type"
        const val LOG_RECORD_LEVEL = "log_record_level"
        const val LAST_LOG_CLEANUP_TIME = "last_log_cleanup_time"
        const val LANGUAGE_CODE = "language_code"
        const val UI_USE_MIUIX = "ui_use_miuix"
        const val IS_SETUP_COMPLETE = "is_setup_complete"
        const val RECOMMEND_MEDIA_APP = "recommend_media_app"
        const val HIDE_RECENTS_ENABLED = "hide_recents_enabled"
        const val CARD_BLUR_ENABLED = "card_blur_enabled"
        const val PREDICTIVE_BACK_ENABLED = "predictive_back_enabled"
        const val PREDICTIVE_BACK_ANIMATION_MODE = "predictive_back_animation_mode"
        const val PREDICTIVE_BACK_ANIMATION_STYLE = "predictive_back_animation_style"
        const val HOME_LYRIC_PREVIEW_DISPLAY_MODES = "home_lyric_preview_display_modes"
        const val THEME_FOLLOW_SYSTEM = "theme_follow_system"
        const val THEME_DARK_MODE = "theme_dark_mode"
        const val THEME_PURE_BLACK = "theme_pure_black"
        const val THEME_DYNAMIC_COLOR = "theme_dynamic_color"
        const val THEME_CUSTOM_COLOR = "theme_custom_color"
        const val THEME_CUSTOM_COLOR_GLOBAL_TINT = "theme_custom_color_global_tint"
        const val MIUIX_THEME_COLOR_SOURCE = "miuix_theme_color_source"
        const val MATERIAL_THEME_FOLLOW_SYSTEM = "material_theme_follow_system"
        const val MATERIAL_THEME_DARK_MODE = "material_theme_dark_mode"
        const val MATERIAL_THEME_PURE_BLACK = "material_theme_pure_black"
        const val MATERIAL_THEME_DYNAMIC_COLOR = "material_theme_dynamic_color"
        const val MATERIAL_THEME_COLOR_SOURCE = "material_theme_color_source"
        const val MATERIAL_THEME_CUSTOM_COLOR = "material_theme_custom_color"
        const val MATERIAL_THEME_CUSTOM_COLOR_GLOBAL_TINT = "material_theme_custom_color_global_tint"
        const val FLOATING_LYRICS_ENABLED = "floating_lyrics_enabled"
        const val FLOATING_TEXT_SIZE_SP = "floating_text_size_sp"
        const val FLOATING_TEXT_COLOR = "floating_text_color"
        const val FLOATING_FOLLOW_ALBUM_COLOR = "floating_follow_album_color"
        const val FLOATING_SHOW_ALBUM_ART = "floating_show_album_art"
        const val FLOATING_TEXT_STROKE = "floating_text_stroke"
        const val FLOATING_TEXT_BACKGROUND = "floating_text_background"
        const val FLOATING_DISPLAY_MODE = "floating_display_mode"
        const val FLOATING_NEIGHBOR_ALIGNMENT = "floating_neighbor_alignment"
        const val FLOATING_WORD_HIGHLIGHT = "floating_word_highlight"
        const val FLOATING_POS_X = "floating_pos_x"
        const val FLOATING_POS_Y = "floating_pos_y"
        const val FULLY_OFFLINE_MODE = "fully_offline_mode"
        const val WHITELIST_PACKAGES_LEGACY = "whitelist_packages"
        const val WHITELIST_JSON = "whitelist_json"
        const val PARSER_RULES_JSON = "parser_rules_json"
        const val PARSER_RULE_TEMPLATE_JSON = "parser_rule_template_json"
        const val IGNORED_UPDATE_VERSION = "ignored_update_version"
        const val AUTO_CHECK_UPDATES = "auto_check_updates"
        const val LAST_UPDATE_CHECK_TIME = "last_update_check_time"
        const val ALLOW_PRERELEASE_UPDATES = "allow_prerelease_updates"
        const val PRERELEASE_CHANNEL = "prerelease_channel"
        const val UPDATE_CHANNEL = "update_channel"
        const val LOCAL_LYRIC_DIRECTORIES_JSON = "local_lyric_directories_json"
        const val LOCAL_LYRIC_CUSTOM_MATCHES_JSON = "local_lyric_custom_matches_json"
        const val LOCAL_LYRIC_EXPORT_DIRECTORY_URI = "local_lyric_export_directory_uri"
        const val LOCAL_LYRIC_EXPORT_MATCH_SYNC_ENABLED = "local_lyric_export_match_sync_enabled"
        const val NEW_PLAYING_APP_ALERT_ENABLED = "new_playing_app_alert_enabled"
        const val NEW_PLAYING_APP_ALERT_IGNORED_PACKAGES = "new_playing_app_alert_ignored_packages"
        const val NEW_PLAYING_APP_ALERT_ALERTED_PACKAGES = "new_playing_app_alert_alerted_packages"
        const val NEW_PLAYING_APP_ALERT_LAST_NOTIFY_TIME = "new_playing_app_alert_last_notify_time"
    }

    object Defaults {
        const val NOTIFICATION_ACTIONS_STYLE = "disabled"
        const val NOTIFICATION_CLICK_STYLE = "default"
        const val DYNAMIC_ICON_STYLE = "disabled"
        const val SUPER_ISLAND_SHARE_FORMAT = "format_1"
        const val SUPER_ISLAND_MEDIA_BUTTON_LAYOUT = "two_button"
        const val SUPER_ISLAND_NOTIFICATION_STYLE = "standard"
        const val SUPER_ISLAND_LYRIC_MODE = "standard"
    }

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun notificationActionsStyle(prefs: SharedPreferences): String =
        prefs.getString(Keys.NOTIFICATION_ACTIONS_STYLE, Defaults.NOTIFICATION_ACTIONS_STYLE)
            ?: Defaults.NOTIFICATION_ACTIONS_STYLE

    fun notificationClickStyle(prefs: SharedPreferences): String =
        prefs.getString(Keys.NOTIFICATION_CLICK_STYLE, Defaults.NOTIFICATION_CLICK_STYLE)
            ?: Defaults.NOTIFICATION_CLICK_STYLE

    fun isProgressBarColorEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.PROGRESS_BAR_COLOR_ENABLED, false)

    fun dynamicIconStyle(prefs: SharedPreferences): String =
        prefs.getString(Keys.DYNAMIC_ICON_STYLE, Defaults.DYNAMIC_ICON_STYLE)
            ?: Defaults.DYNAMIC_ICON_STYLE

    fun isLyricScrollingDisabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.DISABLE_LYRIC_SCROLLING, false)

    fun isSuperIslandTextColorEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.SUPER_ISLAND_TEXT_COLOR_ENABLED, false)

    fun isSuperIslandShareEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.SUPER_ISLAND_SHARE_ENABLED, true)

    fun superIslandShareFormat(prefs: SharedPreferences): String =
        prefs.getString(Keys.SUPER_ISLAND_SHARE_FORMAT, Defaults.SUPER_ISLAND_SHARE_FORMAT)
            ?: Defaults.SUPER_ISLAND_SHARE_FORMAT

    fun superIslandMediaButtonLayout(prefs: SharedPreferences): String =
        prefs.getString(Keys.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT, Defaults.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT)
            ?: Defaults.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT

    fun superIslandNotificationStyle(prefs: SharedPreferences): String =
        prefs.getString(Keys.SUPER_ISLAND_NOTIFICATION_STYLE, Defaults.SUPER_ISLAND_NOTIFICATION_STYLE)
            ?: Defaults.SUPER_ISLAND_NOTIFICATION_STYLE

    fun superIslandLyricMode(prefs: SharedPreferences): String =
        prefs.getString(Keys.SUPER_ISLAND_LYRIC_MODE, Defaults.SUPER_ISLAND_LYRIC_MODE)
            ?: Defaults.SUPER_ISLAND_LYRIC_MODE

    fun isSuperIslandFullLyricLeftCoverEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.SUPER_ISLAND_FULL_LYRIC_SHOW_LEFT_COVER, true)

    fun isDevModeEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.DEV_MODE_ENABLED, false)

    fun isOfflineModeEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Keys.FULLY_OFFLINE_MODE, false)
}
