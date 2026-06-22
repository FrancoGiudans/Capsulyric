package com.example.islandlyrics.ui.overlay.superisland.config
import android.content.Context
import android.content.SharedPreferences
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.core.settings.AppPreferences

internal class SuperIslandPreferencesCache(
    context: Context,
    private val onClickStyleChanged: () -> Unit,
    private val onXmsfModeChanged: (XmsfBypassMode) -> Unit
) {
    var clickStyle = "default"
        private set
    var textColorEnabled = false
        private set
    var shareEnabled = true
        private set
    var shareFormat = "format_1"
        private set
    var progressBarColorEnabled = false
        private set
    var actionStyle = "disabled"
        private set
    var mediaButtonLayout = "two_button"
        private set
    var notificationStyle = "standard"
        private set
    var lyricMode = "standard"
        private set
    var fullLyricShowLeftCover = true
        private set
    var colorSource = SuperIslandColorSource.ALBUM_ART
        private set
    var customColor = 0xFF3482FF.toInt()
        private set
    var rightTextWeight = SuperIslandLyricLayout.calculateWeight("七七七七七七七")
        private set
    var leftWithCoverTextWeight = SuperIslandLyricLayout.calculateWeight("六六六六六六")
        private set
    var leftNoCoverTextWeight = SuperIslandLyricLayout.calculateWeight("八八八八八八八八")
        private set
    var xmsfBypassMode = XmsfBypassMode.DISABLED
        private set
    var xmsfCustomDurationMs = XmsfBypassMode.DEFAULT_CUSTOM_DURATION_MS
        private set

    private val prefs by lazy { AppPreferences.of(context) }
    private var relaxedTextLimitsEnabled = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            AppPreferences.Keys.NOTIFICATION_CLICK_STYLE -> {
                clickStyle = AppPreferences.notificationClickStyle(p)
                onClickStyleChanged()
            }
            AppPreferences.Keys.SUPER_ISLAND_TEXT_COLOR_ENABLED ->
                textColorEnabled = AppPreferences.isSuperIslandTextColorEnabled(p)
            SuperIslandColorSource.PREF_KEY -> colorSource = SuperIslandColorSource.read(p)
            SuperIslandColorSource.CUSTOM_COLOR_PREF_KEY -> customColor = SuperIslandColorSource.readCustomColor(p)
            AppPreferences.Keys.SUPER_ISLAND_SHARE_ENABLED ->
                shareEnabled = AppPreferences.isSuperIslandShareEnabled(p)
            AppPreferences.Keys.SUPER_ISLAND_SHARE_FORMAT ->
                shareFormat = AppPreferences.superIslandShareFormat(p)
            AppPreferences.Keys.PROGRESS_BAR_COLOR_ENABLED ->
                progressBarColorEnabled = AppPreferences.isProgressBarColorEnabled(p)
            AppPreferences.Keys.NOTIFICATION_ACTIONS_STYLE ->
                actionStyle = AppPreferences.notificationActionsStyle(p)
            AppPreferences.Keys.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT ->
                mediaButtonLayout = AppPreferences.superIslandMediaButtonLayout(p)
            AppPreferences.Keys.SUPER_ISLAND_NOTIFICATION_STYLE ->
                notificationStyle = AppPreferences.superIslandNotificationStyle(p)
            AppPreferences.Keys.SUPER_ISLAND_LYRIC_MODE ->
                lyricMode = sanitizeLyricMode(AppPreferences.superIslandLyricMode(p))
            AppPreferences.Keys.SUPER_ISLAND_FULL_LYRIC_SHOW_LEFT_COVER ->
                fullLyricShowLeftCover = AppPreferences.isSuperIslandFullLyricLeftCoverEnabled(p)
            SuperIslandTextLimitConfig.KEY_RIGHT_CHARS,
            SuperIslandTextLimitConfig.KEY_LEFT_WITH_COVER_CHARS,
            SuperIslandTextLimitConfig.KEY_LEFT_NO_COVER_CHARS,
            "lab_super_island_relaxed_text_limits_enabled" -> loadTextLimits(p)
            AppPreferences.Keys.XMSF_NETWORK_MODE, AppPreferences.Keys.XMSF_NETWORK_LEGACY -> {
                xmsfBypassMode = XmsfBypassMode.read(p)
                onXmsfModeChanged(xmsfBypassMode)
            }
            AppPreferences.Keys.XMSF_NETWORK_CUSTOM_DURATION_MS -> {
                xmsfCustomDurationMs = XmsfBypassMode.readCustomDurationMs(p)
            }
        }
    }

    fun start() {
        load()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun load() {
        clickStyle = AppPreferences.notificationClickStyle(prefs)
        textColorEnabled = AppPreferences.isSuperIslandTextColorEnabled(prefs)
        colorSource = SuperIslandColorSource.read(prefs)
        customColor = SuperIslandColorSource.readCustomColor(prefs)
        shareEnabled = AppPreferences.isSuperIslandShareEnabled(prefs)
        shareFormat = AppPreferences.superIslandShareFormat(prefs)
        progressBarColorEnabled = AppPreferences.isProgressBarColorEnabled(prefs)
        actionStyle = AppPreferences.notificationActionsStyle(prefs)
        mediaButtonLayout = AppPreferences.superIslandMediaButtonLayout(prefs)
        notificationStyle = AppPreferences.superIslandNotificationStyle(prefs)
        lyricMode = sanitizeLyricMode(AppPreferences.superIslandLyricMode(prefs))
        fullLyricShowLeftCover = AppPreferences.isSuperIslandFullLyricLeftCoverEnabled(prefs)
        loadTextLimits(prefs)
        xmsfBypassMode = XmsfBypassMode.read(prefs)
        xmsfCustomDurationMs = XmsfBypassMode.readCustomDurationMs(prefs)
    }

    private fun loadTextLimits(prefs: SharedPreferences) {
        relaxedTextLimitsEnabled = prefs.getBoolean("lab_super_island_relaxed_text_limits_enabled", false)
        rightTextWeight = SuperIslandTextLimitConfig.weightForChars(
            SuperIslandTextLimitConfig.rightChars(prefs, relaxedTextLimitsEnabled)
        )
        leftWithCoverTextWeight = SuperIslandTextLimitConfig.weightForChars(
            SuperIslandTextLimitConfig.leftChars(
                prefs,
                showLeftCover = true,
                relaxed = relaxedTextLimitsEnabled
            )
        )
        leftNoCoverTextWeight = SuperIslandTextLimitConfig.weightForChars(
            SuperIslandTextLimitConfig.leftChars(
                prefs,
                showLeftCover = false,
                relaxed = relaxedTextLimitsEnabled
            )
        )
    }

    private fun sanitizeLyricMode(mode: String?): String =
        if (mode == "full") "full" else "standard"
}
