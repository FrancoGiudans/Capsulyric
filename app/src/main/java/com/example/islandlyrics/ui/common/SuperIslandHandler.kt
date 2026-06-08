package com.example.islandlyrics.ui.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.widget.RemoteViews
import com.example.islandlyrics.BuildConfig
import com.example.islandlyrics.R
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.core.platform.XmsfBypassMode
import com.example.islandlyrics.data.LyricRepository
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.feature.main.MainActivity
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplate
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplateV3
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SuperIslandHandler
 * Pure DSL Renderer for Xiaomi Super Island notifications using FocusNotification API.
 */
class SuperIslandHandler(
    private val context: Context,
    private val service: LyricService
) {

    private val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    var isRunning = false
        private set

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Preferences
    private var cachedClickStyle = "default"
    private var cachedSuperIslandTextColorEnabled = false
    private var cachedSuperIslandShareEnabled = true
    private var cachedSuperIslandShareFormat = "format_1"
    private var cachedProgressBarColorEnabled = false
    private var cachedActionStyle = "disabled"
    private var cachedMediaButtonLayout = "two_button"
    private var cachedSuperIslandNotificationStyle = "standard"
    private var cachedSuperIslandLyricMode = "standard"
    private var cachedSuperIslandFullLyricShowLeftCover = true
    private var cachedSuperIslandColorSource = SuperIslandColorSource.ALBUM_ART
    private var cachedSuperIslandCustomColor = 0xFF3482FF.toInt()
    private var cachedSuperIslandRightTextWeight = SuperIslandLyricLayout.calculateWeight("七七七七七七七")
    private var cachedSuperIslandLeftWithCoverTextWeight = SuperIslandLyricLayout.calculateWeight("六六六六六六")
    private var cachedSuperIslandLeftNoCoverTextWeight = SuperIslandLyricLayout.calculateWeight("八八八八八八八八")
    private var cachedXmsfBypassMode = XmsfBypassMode.DISABLED
    private var cachedXmsfCustomDurationMs = XmsfBypassMode.DEFAULT_CUSTOM_DURATION_MS

    private var cachedContentIntent: PendingIntent? = null
    private var cachedMiPlayIntent: PendingIntent? = null
    private var networkCutJob: kotlinx.coroutines.Job? = null
    private var networkCutSeq = 0L
    private val networkCutMutex = Mutex()
    private var aggressiveNetworkCutActive = false
    private var aggressiveTrackKey: String? = null
    private var aggressiveCutGeneration = 0L

    private val prefs by lazy { context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "notification_click_style" -> {
                cachedClickStyle = p.getString(key, "default") ?: "default"
                cachedContentIntent = createContentIntent()
                cachedMiPlayIntent = createMiPlayIntent()
            }
            "super_island_text_color_enabled" -> cachedSuperIslandTextColorEnabled = p.getBoolean(key, false)
            SuperIslandColorSource.PREF_KEY -> cachedSuperIslandColorSource = SuperIslandColorSource.read(p)
            SuperIslandColorSource.CUSTOM_COLOR_PREF_KEY -> cachedSuperIslandCustomColor = SuperIslandColorSource.readCustomColor(p)
            "super_island_share_enabled" -> cachedSuperIslandShareEnabled = p.getBoolean(key, true)
            "super_island_share_format" -> cachedSuperIslandShareFormat = p.getString(key, "format_1") ?: "format_1"
            "progress_bar_color_enabled" -> cachedProgressBarColorEnabled = p.getBoolean(key, false)
            "notification_actions_style" -> cachedActionStyle = p.getString(key, "disabled") ?: "disabled"
            "super_island_media_button_layout" -> cachedMediaButtonLayout = p.getString(key, "two_button") ?: "two_button"
            "super_island_notification_style" -> cachedSuperIslandNotificationStyle = p.getString(key, "standard") ?: "standard"
            "super_island_lyric_mode" -> cachedSuperIslandLyricMode = p.getString(key, "standard") ?: "standard"
            "super_island_full_lyric_show_left_cover" -> cachedSuperIslandFullLyricShowLeftCover = p.getBoolean(key, true)
            SuperIslandTextLimitConfig.KEY_RIGHT_CHARS,
            SuperIslandTextLimitConfig.KEY_LEFT_WITH_COVER_CHARS,
            SuperIslandTextLimitConfig.KEY_LEFT_NO_COVER_CHARS -> loadSuperIslandTextLimits(p)
            "block_xmsf_network_mode", "block_xmsf_network" -> {
                cachedXmsfBypassMode = XmsfBypassMode.read(p)
                if (cachedXmsfBypassMode != XmsfBypassMode.AGGRESSIVE && aggressiveNetworkCutActive) {
                    restoreXmsfNetworkingAsync()
                }
            }
            "block_xmsf_network_custom_duration_ms" -> {
                cachedXmsfCustomDurationMs = XmsfBypassMode.readCustomDurationMs(p)
            }
        }
    }

    private fun loadPreferences() {
        cachedClickStyle = prefs.getString("notification_click_style", "default") ?: "default"
        cachedContentIntent = createContentIntent()
        cachedMiPlayIntent = createMiPlayIntent()
        cachedSuperIslandTextColorEnabled = prefs.getBoolean("super_island_text_color_enabled", false)
        cachedSuperIslandColorSource = SuperIslandColorSource.read(prefs)
        cachedSuperIslandCustomColor = SuperIslandColorSource.readCustomColor(prefs)
        cachedSuperIslandShareEnabled = prefs.getBoolean("super_island_share_enabled", true)
        cachedSuperIslandShareFormat = prefs.getString("super_island_share_format", "format_1") ?: "format_1"
        cachedProgressBarColorEnabled = prefs.getBoolean("progress_bar_color_enabled", false)
        cachedActionStyle = prefs.getString("notification_actions_style", "disabled") ?: "disabled"
        cachedMediaButtonLayout = prefs.getString("super_island_media_button_layout", "two_button") ?: "two_button"
        cachedSuperIslandNotificationStyle = prefs.getString("super_island_notification_style", "standard") ?: "standard"
        cachedSuperIslandLyricMode = prefs.getString("super_island_lyric_mode", "standard") ?: "standard"
        cachedSuperIslandFullLyricShowLeftCover = prefs.getBoolean("super_island_full_lyric_show_left_cover", true)
        loadSuperIslandTextLimits(prefs)
        cachedXmsfBypassMode = XmsfBypassMode.read(prefs)
        cachedXmsfCustomDurationMs = XmsfBypassMode.readCustomDurationMs(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadSuperIslandTextLimits(prefs: SharedPreferences) {
        cachedSuperIslandRightTextWeight = SuperIslandTextLimitConfig.weightForChars(
            SuperIslandTextLimitConfig.rightChars(prefs)
        )
        cachedSuperIslandLeftWithCoverTextWeight = SuperIslandTextLimitConfig.weightForChars(
            SuperIslandTextLimitConfig.leftChars(prefs, showLeftCover = true)
        )
        cachedSuperIslandLeftNoCoverTextWeight = SuperIslandTextLimitConfig.weightForChars(
            SuperIslandTextLimitConfig.leftChars(prefs, showLeftCover = false)
        )
    }

    // Change detection tracking
    private var lastSentDisplayLyric = ""
    private var lastSentFullLyric = ""
    private var lastSentProgressPercent = -1
    private var lastSentSubText = ""
    private var lastSentIsPlaying = false
    private var lastSentTitle = ""
    private var lastSentArtist = ""
    private var isFirstNotification = true
    private var firstNotificationReason = "initial"
    private var lastFocusParam = ""
    
    private var lastNotifyTime = 0L
    private val throttleIntervalMs = 1000L
    
    private var lastAlbumArtHash = 0
    private var lastPicAppHash = 0
    private var lastPicActionsHash = 0
    
    private var cachedAvatarIcon: Icon? = null
    private var cachedIslandIcon: Icon? = null
    private var cachedIslandSmallIcon: Icon? = null
    private var cachedShareIcon: Icon? = null
    private var cachedAppIcon: Icon? = null
    private var cachedPrevIcon: Icon? = null
    private var cachedPrevIconDark: Icon? = null
    private var cachedPlayPauseIcon: Icon? = null
    private var cachedPlayPauseIconDark: Icon? = null
    private var cachedNextIcon: Icon? = null
    private var cachedNextIconDark: Icon? = null
    private val scaledBitmapCache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)
    private val progressBitmapCache = LinkedHashMap<String, Bitmap>(12, 0.75f, true)

    private var lastAppliedAlbumColor = 0

    init {
        createChannel()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        loadPreferences()

        lastSentDisplayLyric = ""
        lastSentFullLyric = ""
        lastSentProgressPercent = -1
        lastSentSubText = ""
        lastSentIsPlaying = false
        isFirstNotification = true
        firstNotificationReason = "initial"
        
        lastAlbumArtHash = 0
        lastPicAppHash = 0
        lastPicActionsHash = 0
        lastAppliedAlbumColor = 0

        cachedAvatarIcon = null
        cachedIslandIcon = null
        cachedIslandSmallIcon = null
        cachedShareIcon = null
        cachedAppIcon = null
        cachedPrevIcon = null
        cachedPrevIconDark = null
        cachedPlayPauseIcon = null
        cachedPlayPauseIconDark = null
        cachedNextIcon = null
        cachedNextIconDark = null
        clearBitmapCaches()
        aggressiveNetworkCutActive = false
        aggressiveTrackKey = null

        lastFocusParam = ""
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        manager?.cancel(NOTIFICATION_ID)
        lastFocusParam = ""
        
        // Cancel all pending scope jobs
        // But wait, if we cancel the whole scope we might not be able to restart it?
        // Actually, for a service-bound handler, we should probably use a job that we cancel.
        
        cachedAvatarIcon = null
        cachedIslandIcon = null
        cachedIslandSmallIcon = null
        cachedShareIcon = null
        cachedAppIcon = null
        cachedPrevIcon = null
        cachedPrevIconDark = null
        cachedPlayPauseIcon = null
        cachedPlayPauseIconDark = null
        cachedNextIcon = null
        cachedNextIconDark = null
        clearBitmapCaches()
        aggressiveTrackKey = null
        aggressiveNetworkCutActive = false
        
        // Ensure network is restored if we were blocking it
        networkCutJob?.cancel()
        if (cachedXmsfBypassMode.isEnabled) {
            restoreXmsfNetworkingAsync()
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_lyrics),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_live_lyrics_desc)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    private fun updateIcons(metadata: LyricRepository.MediaInfo?, albumArt: Bitmap?, isPlaying: Boolean) {
        val albumArtHash = albumArt?.hashCode() ?: 0
        if (albumArtHash != lastAlbumArtHash) {
            if (albumArt != null) {
                cachedAvatarIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 480))
                cachedIslandIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 120))
                cachedIslandSmallIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 88))
                cachedShareIcon = Icon.createWithBitmap(scaleBitmap(albumArt, 224))
            } else {
                cachedAvatarIcon = null
                cachedIslandIcon = null
                cachedIslandSmallIcon = null
                cachedShareIcon = null
            }
            lastAlbumArtHash = albumArtHash
            
            // Also update app icon if metadata changed concurrently
            val appIconHash = metadata?.packageName?.hashCode() ?: 0
            if (appIconHash != lastPicAppHash || cachedAppIcon == null) {
                val appIconBmp = getAppIcon(metadata?.packageName)
                cachedAppIcon = if (appIconBmp != null) Icon.createWithBitmap(scaleBitmap(appIconBmp, 96)) else null
                lastPicAppHash = appIconHash
            }
        } else if (metadata?.packageName?.hashCode() != lastPicAppHash) {
            val appIconBmp = getAppIcon(metadata?.packageName)
            cachedAppIcon = if (appIconBmp != null) Icon.createWithBitmap(scaleBitmap(appIconBmp, 96)) else null
            lastPicAppHash = metadata?.packageName?.hashCode() ?: 0
        }

        val effectiveButtonLayout = if (cachedSuperIslandNotificationStyle == "advanced_beta") "three_button" else cachedMediaButtonLayout
        val actionsHash = if (cachedActionStyle == "media_controls") {
            "$isPlaying|$effectiveButtonLayout".hashCode()
        } else {
            -1
        }
        if (actionsHash != lastPicActionsHash) {
            if (cachedActionStyle == "media_controls") {
                val showPrevButton = effectiveButtonLayout == "three_button"
                val prevIconBitmap = if (showPrevButton) {
                    renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, null, android.graphics.Color.parseColor("#FF111111"))
                } else {
                    null
                }
                val prevIconBitmapDark = if (showPrevButton) {
                    renderButtonIcon(R.drawable.ic_skip_previous, 96, 0.5f, null, android.graphics.Color.WHITE)
                } else {
                    null
                }
                val playPauseResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                val playPauseIconBitmap = renderButtonIcon(playPauseResId, 96, 0.42f, null, android.graphics.Color.parseColor("#FF111111"))
                val playPauseIconBitmapDark = renderButtonIcon(playPauseResId, 96, 0.42f, null, android.graphics.Color.WHITE)
                val nextIconBitmap = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, null, android.graphics.Color.parseColor("#FF111111"))
                val nextIconBitmapDark = renderButtonIcon(R.drawable.ic_skip_next, 96, 0.5f, null, android.graphics.Color.WHITE)

                cachedPrevIcon = prevIconBitmap?.let { Icon.createWithBitmap(it) }
                cachedPrevIconDark = prevIconBitmapDark?.let { Icon.createWithBitmap(it) }
                cachedPlayPauseIcon = Icon.createWithBitmap(playPauseIconBitmap)
                cachedPlayPauseIconDark = Icon.createWithBitmap(playPauseIconBitmapDark)
                cachedNextIcon = Icon.createWithBitmap(nextIconBitmap)
                cachedNextIconDark = Icon.createWithBitmap(nextIconBitmapDark)
            } else {
                cachedPrevIcon = null
                cachedPrevIconDark = null
                cachedPlayPauseIcon = null
                cachedPlayPauseIconDark = null
                cachedNextIcon = null
                cachedNextIconDark = null
            }
            lastPicActionsHash = actionsHash
        }
    }

    private fun createBaseBuilder(): Notification.Builder {
        return applyImmediateForegroundBehavior(
            Notification.Builder(context, CHANNEL_ID)
        )
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(cachedContentIntent)
    }

    fun render(state: UIState) {
        if (!isRunning) return

        val displayLyric = state.displayLyric
        val subText = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title
        val progressPercent = state.progressCurrent
        val albumColor = state.albumColor
        val accentColor = SuperIslandColorSource.resolveColor(
            source = cachedSuperIslandColorSource,
            albumColor = albumColor,
            customColor = cachedSuperIslandCustomColor
        )

        // 1. TRACK SWITCH DETECTION: Clear builder cache to prevent "stuck" metadata on Android 15
        val trackChanged = state.title != lastSentTitle || state.artist != lastSentArtist
        if (trackChanged && !isFirstNotification) {
            com.example.islandlyrics.core.logging.AppLogger.getInstance().d("SuperIsland", "Track changed: ${state.title}. Resetting builder.")
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    "SuperIsland",
                    "[NotifyTrace] markFirst reason=trackChanged track=${state.title} - ${state.artist}"
                )
            }
            isFirstNotification = true
            firstNotificationReason = "trackChanged"
            lastFocusParam = ""
        }

        // Color changed?
        val colorChanged = accentColor != lastAppliedAlbumColor

        // 2. LYRIC LINE CHANGE: Force startForeground to clear MIUI rendering frame cache (Issue #22)
        // Compare fullLyric (not displayLyric) to detect actual line changes.
        // displayLyric changes on every syllable scroll tick within the same line,
        // which would cause excessive startForeground calls and system-level delays.
        val lyricLineChanged = !isFirstNotification && !trackChanged
                && state.fullLyric.isNotEmpty() && state.fullLyric != lastSentFullLyric
        if (lyricLineChanged) {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    "SuperIsland",
                    "[NotifyTrace] markFirst reason=lyricLineChanged fullLyric=${state.fullLyric}"
                )
            }
            isFirstNotification = true
            firstNotificationReason = "lyricLineChanged"
        }

        // 3. CONTENT-AWARE THROTTLING
        val contentChanged = trackChanged || lyricLineChanged || displayLyric != lastSentDisplayLyric || state.isPlaying != lastSentIsPlaying
        val now = System.currentTimeMillis()
        
        if (!isFirstNotification && !colorChanged && !contentChanged) {
            // Only progress changed. Apply 1000ms throttle for Android 15 stability.
            val progressChangedEnough = kotlin.math.abs(progressPercent - lastSentProgressPercent) >= PROGRESS_NOTIFY_STEP_PERCENT
            if (!progressChangedEnough || now - lastNotifyTime < throttleIntervalMs) {
                return
            }
        }

        // Pre-JSON FAST PATH: skip expensive serialization if literally nothing changed (including exact progress)
        if (!isFirstNotification && !colorChanged && !contentChanged && progressPercent == lastSentProgressPercent) {
            return
        }

        val metadata = LyricRepository.getInstance().liveMetadata.value
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        val currentTrackKey = buildTrackKey(state)

        if (aggressiveNetworkCutActive) {
            val songEnded = !state.isPlaying
            val songChanged = aggressiveTrackKey != null && aggressiveTrackKey != currentTrackKey
            if (songEnded || cachedXmsfBypassMode != XmsfBypassMode.AGGRESSIVE) {
                restoreXmsfNetworkingAsync(expectedGeneration = aggressiveCutGeneration)
            } else if (songChanged) {
                // Auto-advance can deliver the next track while playback remains active.
                // Keep the aggressive bypass window open instead of briefly restoring XMSF,
                // otherwise HyperOS may remove the freshly posted focus notification.
                aggressiveTrackKey = currentTrackKey
                aggressiveCutGeneration++
            }
        }

        updateIcons(metadata, albumArt, state.isPlaying)

        val hexColor = String.format("#FF%06X", 0xFFFFFF and accentColor)
        val showHighlightColor = cachedSuperIslandTextColorEnabled 
        val ringColor = if (showHighlightColor) hexColor else "#757575"
        val progressBarColor = if (cachedProgressBarColorEnabled) hexColor else "#757575"
        val packageName = state.mediaPackage.ifEmpty { context.packageName }
        val titleWithArtist = if (state.artist.isNotBlank()) "${state.title} - ${state.artist}" else state.title

        val customExpandEnabled = cachedSuperIslandNotificationStyle == "advanced_beta" &&
            cachedActionStyle == "media_controls"

        val standardExtras = buildStandardFocusBundle(
            state = state,
            displayLyric = displayLyric,
            subText = subText,
            progressPercent = progressPercent,
            hexColor = hexColor,
            showHighlightColor = showHighlightColor,
            ringColor = ringColor,
            progressBarColor = progressBarColor,
            packageName = packageName,
            titleWithArtist = titleWithArtist
        )

        val extras = if (customExpandEnabled) {
            val customExtras = FocusNotification.buildCustomV3 {
                business = "lyric_display"
                isShowNotification = true
                enableFloat = false
                updatable = true
                islandFirstFloat = false
                hideDeco = true
                aodTitle = displayLyric.take(20).ifEmpty { "♪" }
                val avatarKey = cachedAvatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
                val appKey = cachedAppIcon?.let { createPicture("miui.focus.pic_app", it) }
                val islandKey = cachedIslandIcon?.let { createPicture("miui.focus.pic_island", it) }
                val islandSmallKey = cachedIslandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
                val shareKey = cachedShareIcon?.let { createPicture("miui.focus.pic_share", it) }

                ticker = displayLyric.ifEmpty { subText.ifEmpty { state.title.ifEmpty { "♪" } } }
                tickerPic = appKey ?: islandSmallKey ?: avatarKey

                val customLightViews = createCustomExpandRemoteViews(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = false
                )
                val customDarkViews = createCustomExpandRemoteViews(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = true
                )
                val tinyViews = createCustomTinyRemoteViews(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = true
                )
                createRemoteViews(CustomFocusTemplate.LAYOUT, customLightViews)
                createRemoteViews(CustomFocusTemplate.LAYOUT_NIGHT, customDarkViews)
                createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY, tinyViews)
                createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY_NIGHT, tinyViews)
                createRemoteViews(CustomFocusTemplateV3.LAYOUT_ISLAND_EXPAND, customDarkViews)

                island {
                    islandProperty = 1
                    if (showHighlightColor) {
                        highlightColor = hexColor
                    }

            bigIslandArea {
                applyBigIslandLyrics(
                    preferMetadataLayout = state.preferMetadataLayout,
                    isTimingGapPlaceholder = state.isTimingGapPlaceholder,
                    fullLyric = state.fullLyric,
                    displayLyric = displayLyric,
                    titleWithArtist = titleWithArtist,
                    islandKey = islandKey,
                    showHighlightColor = showHighlightColor,
                    title = state.title,
                    artist = state.artist
                )
            }

                    if (cachedSuperIslandShareEnabled) {
                        shareData {
                            pic = shareKey
                            title = state.title.ifEmpty { "♪" }
                            content = resolvePrimaryLyricText(state)
                            val shareArtist = if (state.artist.isNotBlank()) state.artist else "未知歌手"
                            val shareSong = state.title.ifEmpty { "未知歌曲" }
                            this.shareContent = when (cachedSuperIslandShareFormat) {
                                "format_2" -> "${resolvePrimaryLyricText(state)} -$shareArtist\uff0c$shareSong"
                                "format_3" -> "${resolvePrimaryLyricText(state)}\n$shareArtist\uff0c$shareSong"
                                else -> "${resolvePrimaryLyricText(state)}\n$shareSong by $shareArtist"
                            }
                        }
                    }

                    smallIslandArea {
                        combinePicInfo {
                            if (islandSmallKey != null) {
                                picInfo {
                                    type = 1
                                    pic = islandSmallKey
                                }
                            }
                        }
                    }
                }
            }
            mergeCustomFocusWithStandardIsland(customExtras, standardExtras)
        } else {
            standardExtras
        }

        val newParams = extras.getString("miui.focus.param")
        val newCustomParams = extras.getString("miui.focus.param.custom")

        val focusSignature = if (customExpandEnabled) newCustomParams.orEmpty() else newParams.orEmpty()
        if (focusSignature == lastFocusParam && !colorChanged && !isFirstNotification) {
            return
        }

        val notificationTitle = sequenceOf(displayLyric, state.fullLyric, state.title)
            .firstOrNull { !isLyricPlaceholder(it) }
            ?: "Capsulyric"
        val notificationText = subText.ifEmpty { context.getString(R.string.channel_live_lyrics) }
        val notificationBuilder = createBaseBuilder()
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSubText(if (state.mediaPackage.isNotBlank()) state.mediaPackage else null)
            .setColor(if (cachedActionStyle == "media_controls") 0xFF757575.toInt() else accentColor)
            .addExtras(extras)
        if (customExpandEnabled) {
            notificationBuilder.setStyle(Notification.DecoratedCustomViewStyle())
        }
        val notification = notificationBuilder.build()

        lastAppliedAlbumColor = accentColor
        lastFocusParam = focusSignature

        lastSentDisplayLyric = displayLyric
        lastSentFullLyric = state.fullLyric
        lastSentProgressPercent = progressPercent
        lastSentSubText = subText
        lastSentIsPlaying = state.isPlaying
        lastSentTitle = state.title
        lastSentArtist = state.artist
        lastNotifyTime = System.currentTimeMillis()

        if (BuildConfig.DEBUG) {
            AppLogger.getInstance().log(
                "SuperIsland",
                "[NotifyTrace] send first=$isFirstNotification reason=$firstNotificationReason title=$notificationTitle focusEmpty=${focusSignature.isEmpty()} running=$isRunning isPlaying=${state.isPlaying} track=${state.title} - ${state.artist}"
            )
        }

        notifyWithNetworkCut(
            notification = notification,
            isFirst = isFirstNotification,
            firstReason = firstNotificationReason,
            currentTrackKey = currentTrackKey,
            isPlaying = state.isPlaying
        )
        if (isFirstNotification) {
            isFirstNotification = false
            firstNotificationReason = "steady"
        }
    }

    private fun notifyWithNetworkCut(
        notification: Notification,
        isFirst: Boolean,
        firstReason: String,
        currentTrackKey: String,
        isPlaying: Boolean
    ) {
        val canUsePrimedNotify = isFirst &&
            firstReason != "lyricLineChanged" &&
            service.isForegroundSlotPrimed() &&
            manager != null

        fun traceDispatch(path: String) {
            if (BuildConfig.DEBUG) {
                AppLogger.getInstance().log(
                    "SuperIsland",
                    "[NotifyTrace] dispatch mode=$cachedXmsfBypassMode path=$path isFirst=$isFirst firstReason=$firstReason primed=${service.isForegroundSlotPrimed()} playing=$isPlaying trackKey=$currentTrackKey"
                )
            }
        }

        when (cachedXmsfBypassMode) {
            XmsfBypassMode.AGGRESSIVE -> {
                networkCutJob?.cancel()
                if (!isPlaying) {
                    restoreXmsfNetworkingAsync(expectedGeneration = aggressiveCutGeneration)
                    if (canUsePrimedNotify) {
                        traceDispatch("notify.aggressive.notPlaying.primed")
                        manager.notify(NOTIFICATION_ID, notification)
                    } else if (isFirst) {
                        traceDispatch("startForeground.aggressive.notPlaying")
                        service.startForegroundTracked(
                            NOTIFICATION_ID,
                            notification,
                            reason = "superIsland.aggressive.notPlaying.$firstReason"
                        )
                    } else {
                        traceDispatch("notify.aggressive.notPlaying")
                        manager?.notify(NOTIFICATION_ID, notification)
                    }
                    return
                }

                val cutGeneration = ++aggressiveCutGeneration
                networkCutJob = scope.launch(Dispatchers.IO) {
                    networkCutMutex.withLock {
                        if (cutGeneration != aggressiveCutGeneration) {
                            return@withLock
                        }
                        if (!aggressiveNetworkCutActive) {
                            withContext(NonCancellable) {
                                com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                            }
                            aggressiveNetworkCutActive = true
                        }
                        aggressiveTrackKey = currentTrackKey
                        if (manager != null) {
                            traceDispatch("notify.aggressive.cut")
                            manager.notify(NOTIFICATION_ID, notification)
                        } else if (isFirst) {
                            traceDispatch("startForeground.aggressive.cutFallback")
                            service.startForegroundTracked(
                                NOTIFICATION_ID,
                                notification,
                                reason = "superIsland.aggressive.cutFallback.$firstReason"
                            )
                        }
                    }
                }
            }
            XmsfBypassMode.STANDARD,
            XmsfBypassMode.CUSTOM -> {
            networkCutJob?.cancel()
            val seq = ++networkCutSeq
            val cutDurationMs = if (cachedXmsfBypassMode == XmsfBypassMode.CUSTOM) {
                cachedXmsfCustomDurationMs.toLong()
            } else {
                XmsfBypassMode.STANDARD_DURATION_MS.toLong()
            }
            networkCutJob = scope.launch(Dispatchers.IO) {
                networkCutMutex.withLock {
                    // Avoid cancelling Shizuku bind on slower devices (e.g., MTK) by running in NonCancellable.
                    withContext(NonCancellable) {
                        com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, false)
                    }
                    // During the blind window we must go through NotificationManager directly.
                    // startForeground() adds extra service/AMS hops, which can let HyperOS finish
                    // the whitelist scan after the network has already been restored.
                    if (manager != null) {
                        traceDispatch("notify.standard.cut")
                        manager.notify(NOTIFICATION_ID, notification)
                    } else if (isFirst) {
                        traceDispatch("startForeground.standard.cutFallback")
                        service.startForegroundTracked(
                            NOTIFICATION_ID,
                            notification,
                            reason = "superIsland.standard.cutFallback.$firstReason"
                        )
                    }
                    // Keep offline for a brief moment; if a new send happens within this window,
                    // the previous restore will be cancelled.
                    try {
                        kotlinx.coroutines.delay(cutDurationMs)
                    } catch (_: CancellationException) {
                        // A newer job will handle restore.
                    }
                    if (seq == networkCutSeq) {
                        withContext(NonCancellable) {
                            com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
                        }
                    }
                }
            }
            }
            XmsfBypassMode.DISABLED -> {
                if (canUsePrimedNotify) {
                    traceDispatch("notify.disabled.primed")
                    manager.notify(NOTIFICATION_ID, notification)
                } else if (isFirst) {
                    traceDispatch("startForeground.disabled")
                    service.startForegroundTracked(
                        NOTIFICATION_ID,
                        notification,
                        reason = "superIsland.disabled.$firstReason"
                    )
                } else {
                    traceDispatch("notify.disabled")
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun applyImmediateForegroundBehavior(builder: Notification.Builder): Notification.Builder {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }

    private fun restoreXmsfNetworkingAsync() {
        restoreXmsfNetworkingAsync(expectedGeneration = null)
    }

    private fun restoreXmsfNetworkingAsync(expectedGeneration: Long?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                networkCutMutex.withLock {
                    if (expectedGeneration != null && expectedGeneration != aggressiveCutGeneration) {
                        return@withLock
                    }
                    aggressiveTrackKey = null
                    aggressiveNetworkCutActive = false
                    withContext(NonCancellable) {
                        com.example.islandlyrics.integration.shizuku.XmsfNetworkHelper.setXmsfNetworkingEnabled(context, true)
                    }
                }
            }
        }
    }

    private fun buildTrackKey(state: UIState): String {
        return listOf(state.mediaPackage, state.title, state.artist).joinToString("|")
    }

    private fun renderButtonIcon(
        resourceId: Int,
        size: Int,
        iconScale: Float,
        bgColorHex: String? = null,
        iconTint: Int = android.graphics.Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        if (bgColorHex != null) {
            paint.color = android.graphics.Color.parseColor(bgColorHex)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        }

        val drawable = context.getDrawable(resourceId) ?: return bitmap
        drawable.mutate().setTint(iconTint)
        val iconSize = (size * iconScale).toInt()
        val margin = (size - iconSize) / 2
        drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
        drawable.draw(canvas)

        return bitmap
    }

    private fun scaleBitmap(src: Bitmap, targetSize: Int): Bitmap {
        if (src.isRecycled) {
            return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }
        val cacheKey = "${System.identityHashCode(src)}:${src.width}x${src.height}:$targetSize"
        scaledBitmapCache[cacheKey]?.let { return it }

        val needsScale = src.width != targetSize || src.height != targetSize
        val scaled = if (!needsScale) src 
                     else Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
                     
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
        
        val cornerRadius = targetSize * 0.2f
        
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        
        // Recycle the intermediate scaled bitmap to prevent Native Heap fragmentation
        if (needsScale) {
            scaled.recycle()
        }
        
        putBitmapCacheEntry(scaledBitmapCache, cacheKey, output, maxEntries = 8)
        return output
    }

    private fun getAppIcon(packageName: String?): Bitmap? {
        if (packageName == null) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun createContentIntent(): PendingIntent {
        return if (cachedClickStyle == "media_controls") {
            val intent = Intent(context, MediaControlActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun createFocusBroadcastAction(
        key: String,
        requestCode: Int,
        action: String,
        iconResId: Int,
        title: String
    ): String {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationAction = Notification.Action.Builder(
            Icon.createWithResource(context, iconResId),
            title,
            pendingIntent
        ).build()
        return createAction(key, notificationAction)
    }

    private fun createMiPlayIntent(): PendingIntent? {
        return runCatching {
            PendingIntent.getActivity(
                context,
                4,
                Intent().apply {
                    component = android.content.ComponentName(
                        "miui.systemui.plugin",
                        "miui.systemui.miplay.MiPlayDetailActivity"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }.getOrNull()
    }

    private fun createCustomExpandRemoteViews(
        state: UIState,
        subText: String,
        progressPercent: Int,
        progressBarColor: String,
        darkMode: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.super_island_custom_expand)
        val primaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#FFFFFFFF" else "#FF111111")
        val secondaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#B3FFFFFF" else "#99000000")
        val iconTintColor = primaryTextColor
        views.setTextViewText(
            R.id.custom_expand_title,
            resolvePrimaryLyricText(state)
        )
        views.setTextViewText(R.id.custom_expand_subtitle, subText.ifEmpty { context.getString(R.string.channel_live_lyrics) })
        views.setTextColor(R.id.custom_expand_title, primaryTextColor)
        views.setTextColor(R.id.custom_expand_subtitle, secondaryTextColor)
        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        if (albumArt != null) {
            views.setImageViewBitmap(R.id.custom_expand_cover, scaleBitmap(albumArt, 116))
        } else {
            views.setImageViewResource(R.id.custom_expand_cover, R.drawable.ic_music_note)
        }
        views.setImageViewBitmap(
            R.id.custom_expand_progress,
            createProgressBarBitmap(progressPercent, progressBarColor, darkMode)
        )
        views.setImageViewResource(
            R.id.custom_expand_play_pause,
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        views.setInt(R.id.custom_expand_prev, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_play_pause, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_next, "setColorFilter", iconTintColor)
        views.setInt(R.id.custom_expand_miplay, "setColorFilter", iconTintColor)
        views.setOnClickPendingIntent(
            R.id.custom_expand_prev,
            PendingIntent.getBroadcast(
                context,
                2100,
                Intent("com.example.islandlyrics.ACTION_MEDIA_PREV")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        views.setOnClickPendingIntent(
            R.id.custom_expand_play_pause,
            PendingIntent.getBroadcast(
                context,
                2101,
                Intent("com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        views.setOnClickPendingIntent(
            R.id.custom_expand_next,
            PendingIntent.getBroadcast(
                context,
                2102,
                Intent("com.example.islandlyrics.ACTION_MEDIA_NEXT")
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        cachedMiPlayIntent?.let { views.setOnClickPendingIntent(R.id.custom_expand_miplay, it) }
        return views
    }

    private fun createProgressBarBitmap(
        progressPercent: Int,
        progressColor: String,
        darkMode: Boolean
    ): Bitmap {
        val cacheKey = "wide:$progressPercent:$progressColor:$darkMode"
        progressBitmapCache[cacheKey]?.let { return it }

        val width = dpToPx(240)
        val height = dpToPx(6)
        val radius = height / 2f
        val progress = progressPercent.coerceIn(0, 100)
        val progressWidth = if (progress <= 0) 0f else (width * (progress / 100f)).coerceAtLeast(radius * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())

        paint.color = android.graphics.Color.parseColor(if (darkMode) "#33FFFFFF" else "#26000000")
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (progressWidth > 0f) {
            paint.color = android.graphics.Color.parseColor(progressColor)
            canvas.drawRoundRect(
                android.graphics.RectF(0f, 0f, progressWidth, height.toFloat()),
                radius,
                radius,
                paint
            )
        }

        putBitmapCacheEntry(progressBitmapCache, cacheKey, bitmap, maxEntries = 12)
        return bitmap
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun createCustomTinyRemoteViews(
        state: UIState,
        subText: String,
        progressPercent: Int,
        progressBarColor: String,
        darkMode: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.super_island_custom_tiny)
        val primaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#FFFFFFFF" else "#FF111111")
        val secondaryTextColor = android.graphics.Color.parseColor(if (darkMode) "#B3FFFFFF" else "#99000000")
        views.setTextViewText(
            R.id.custom_tiny_title,
            resolveCompactLyricText(state)
        )
        views.setTextViewText(
            R.id.custom_tiny_subtitle,
            subText.ifEmpty { context.getString(R.string.channel_live_lyrics) }
        )
        views.setTextColor(R.id.custom_tiny_title, primaryTextColor)
        views.setTextColor(R.id.custom_tiny_subtitle, secondaryTextColor)
        views.setImageViewBitmap(
            R.id.custom_tiny_progress,
            createTinyProgressBarBitmap(progressPercent, progressBarColor, darkMode)
        )

        val albumArt = LyricRepository.getInstance().liveAlbumArt.value
        if (albumArt != null) {
            views.setImageViewBitmap(R.id.custom_tiny_cover, scaleBitmap(albumArt, 64))
        } else {
            views.setImageViewResource(R.id.custom_tiny_cover, R.drawable.ic_music_note)
        }
        return views
    }

    private fun createTinyProgressBarBitmap(
        progressPercent: Int,
        progressColor: String,
        darkMode: Boolean
    ): Bitmap {
        val cacheKey = "tiny:$progressPercent:$progressColor:$darkMode"
        progressBitmapCache[cacheKey]?.let { return it }

        val width = dpToPx(44)
        val height = dpToPx(4)
        val radius = height / 2f
        val progress = progressPercent.coerceIn(0, 100)
        val progressWidth = if (progress <= 0) 0f else (width * (progress / 100f)).coerceAtLeast(radius * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())

        paint.color = android.graphics.Color.parseColor(if (darkMode) "#33FFFFFF" else "#26000000")
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (progressWidth > 0f) {
            paint.color = android.graphics.Color.parseColor(progressColor)
            canvas.drawRoundRect(
                android.graphics.RectF(0f, 0f, progressWidth, height.toFloat()),
                radius,
                radius,
                paint
            )
        }

        putBitmapCacheEntry(progressBitmapCache, cacheKey, bitmap, maxEntries = 12)
        return bitmap
    }

    private fun putBitmapCacheEntry(
        cache: LinkedHashMap<String, Bitmap>,
        key: String,
        bitmap: Bitmap,
        maxEntries: Int
    ) {
        cache.put(key, bitmap)?.let { old ->
            if (old !== bitmap) recycleBitmap(old)
        }
        while (cache.size > maxEntries) {
            cache.remove(cache.entries.first().key)?.let { recycleBitmap(it) }
        }
    }

    private fun isLyricPlaceholder(text: String): Boolean {
        return text.isBlank() || text.trim() == "♪"
    }

    private fun resolvePrimaryLyricText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.fullLyric, state.displayLyric)
        } else {
            sequenceOf(state.fullLyric, state.displayLyric, state.title)
        }
        return candidates
            .firstOrNull { !isLyricPlaceholder(it) }
            ?: "♪"
    }

    private fun resolveCompactLyricText(state: UIState): String {
        val candidates = if (state.isTimingGapPlaceholder && !state.preferMetadataLayout) {
            sequenceOf(state.displayLyric)
        } else {
            sequenceOf(state.displayLyric, state.title)
        }
        return candidates
            .firstOrNull { !isLyricPlaceholder(it) }
            ?: "♪"
    }

    private fun clearBitmapCaches() {
        scaledBitmapCache.values.forEach { recycleBitmap(it) }
        progressBitmapCache.values.forEach { recycleBitmap(it) }
        scaledBitmapCache.clear()
        progressBitmapCache.clear()
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun createAction(key: String, value: Notification.Action): String {
        return key.also {
            standardActionBundle.putParcelable(it, value)
        }
    }

    private val standardActionBundle = android.os.Bundle()

    private fun com.xzakota.hyper.notification.island.model.BigIslandArea.applyBigIslandLyrics(
        preferMetadataLayout: Boolean,
        isTimingGapPlaceholder: Boolean,
        fullLyric: String,
        displayLyric: String,
        titleWithArtist: String,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String = "",
        artist: String = ""
    ) {
        if (cachedSuperIslandLyricMode == "full") {
            val resolvedLyric = sequenceOf(fullLyric, displayLyric).firstOrNull { !isLyricPlaceholder(it) }.orEmpty()
            val hasLyric = resolvedLyric.isNotBlank()
            val showLeftCover = cachedSuperIslandFullLyricShowLeftCover && islandKey != null

            val shouldShowMetadataFallback = preferMetadataLayout || (!hasLyric && !isTimingGapPlaceholder)
            if (shouldShowMetadataFallback) {
                val leftText = SuperIslandLyricLayout.takeByWeight(
                    title.ifBlank { "♪" },
                    if (showLeftCover) cachedSuperIslandLeftWithCoverTextWeight else cachedSuperIslandLeftNoCoverTextWeight
                ).ifEmpty { "♪" }
                val rightText = SuperIslandLyricLayout.takeByWeight(
                    artist.ifBlank { "♪" },
                    cachedSuperIslandRightTextWeight
                ).ifEmpty { "♪" }

                imageTextInfoLeft {
                    type = 1
                    if (showLeftCover) {
                        picInfo {
                            type = 1
                            pic = islandKey
                        }
                    }
                    textInfo {
                        this.title = leftText
                        this.showHighlightColor = showHighlightColor
                        narrowFont = false
                    }
                }
                this.textInfo = com.xzakota.hyper.notification.island.model.TextInfo().apply {
                    this.title = rightText
                    this.showHighlightColor = showHighlightColor
                    narrowFont = false
                }
                return
            }

            val split = SuperIslandLyricLayout.splitFullLyric(
                text = resolvedLyric,
                showLeftCover = showLeftCover,
                leftMaxWeight = if (showLeftCover) {
                    cachedSuperIslandLeftWithCoverTextWeight
                } else {
                    cachedSuperIslandLeftNoCoverTextWeight
                },
                rightMaxWeight = cachedSuperIslandRightTextWeight
            )

            imageTextInfoLeft {
                type = 1
                if (showLeftCover) {
                    picInfo {
                        type = 1
                        pic = islandKey
                    }
                }
                textInfo {
                    this.title = split.left.ifEmpty { "♪" }
                    this.showHighlightColor = showHighlightColor
                    narrowFont = false
                }
            }
            this.textInfo = com.xzakota.hyper.notification.island.model.TextInfo().apply {
                this.title = split.right.ifEmpty { "♪" }
                this.showHighlightColor = showHighlightColor
                narrowFont = false
            }
            return
        }

        val showLeftCover = islandKey != null
        if (preferMetadataLayout) {
            val leftText = SuperIslandLyricLayout.takeByWeight(
                title.ifBlank { "♪" },
                if (showLeftCover) cachedSuperIslandLeftWithCoverTextWeight else cachedSuperIslandLeftNoCoverTextWeight
            ).ifEmpty { "♪" }
            val rightText = SuperIslandLyricLayout.takeByWeight(
                artist.ifBlank { "♪" },
                cachedSuperIslandRightTextWeight
            ).ifEmpty { "♪" }

            imageTextInfoLeft {
                type = 1
                if (showLeftCover) {
                    picInfo {
                        type = 1
                        pic = islandKey
                    }
                }
                textInfo {
                    this.title = leftText
                    this.showHighlightColor = showHighlightColor
                }
            }
            this.textInfo = com.xzakota.hyper.notification.island.model.TextInfo().apply {
                this.title = rightText
                this.showHighlightColor = showHighlightColor
                narrowFont = false
            }
            return
        }

        val leftTitle = SuperIslandLyricLayout.takeByWeight(
            titleWithArtist.ifBlank { "♪" },
            if (showLeftCover) cachedSuperIslandLeftWithCoverTextWeight else cachedSuperIslandLeftNoCoverTextWeight
        ).ifEmpty { "♪" }
        val rightLyric = SuperIslandLyricLayout.takeByWeight(
            displayLyric.ifBlank { "♪" },
            cachedSuperIslandRightTextWeight
        ).ifEmpty { "♪" }

        imageTextInfoLeft {
            type = 1
            if (showLeftCover) {
                picInfo {
                    type = 1
                    pic = islandKey
                }
            }
            textInfo {
                this.title = leftTitle
                this.showHighlightColor = showHighlightColor
            }
        }
        this.textInfo = com.xzakota.hyper.notification.island.model.TextInfo().apply {
            this.title = rightLyric
            this.showHighlightColor = showHighlightColor
            narrowFont = false
        }
    }

    private fun buildStandardFocusBundle(
        state: UIState,
        displayLyric: String,
        subText: String,
        progressPercent: Int,
        hexColor: String,
        showHighlightColor: Boolean,
        ringColor: String,
        progressBarColor: String,
        packageName: String,
        titleWithArtist: String
    ): android.os.Bundle {
        standardActionBundle.clear()
        val bundle = FocusNotification.buildV3 {
        business = "lyric_display"
        isShowNotification = true
        enableFloat = false
        updatable = true
        islandFirstFloat = false
        aodTitle = displayLyric.take(20).ifEmpty { "♪" }
        val avatarKey = cachedAvatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
        val appKey = cachedAppIcon?.let { createPicture("miui.focus.pic_app", it) }
        val islandKey = cachedIslandIcon?.let { createPicture("miui.focus.pic_island", it) }
        val islandSmallKey = cachedIslandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
        val shareKey = cachedShareIcon?.let { createPicture("miui.focus.pic_share", it) }

        ticker = displayLyric.ifEmpty { subText.ifEmpty { state.title.ifEmpty { "♪" } } }
        tickerPic = appKey ?: islandSmallKey ?: avatarKey

        chatInfo {
            picProfile = avatarKey
            title = resolvePrimaryLyricText(state)
            content = subText
            appIconPkg = packageName
        }

        if (cachedActionStyle == "media_controls") {
            actions {
                val effectiveButtonLayout = if (cachedSuperIslandNotificationStyle == "advanced_beta") "three_button" else cachedMediaButtonLayout
                val showPrevButton = effectiveButtonLayout == "three_button"
                val prevActionKey = if (showPrevButton) {
                    createFocusBroadcastAction(
                        key = "miui.focus.action_prev",
                        requestCode = 3100,
                        action = "com.example.islandlyrics.ACTION_MEDIA_PREV",
                        iconResId = R.drawable.ic_skip_previous,
                        title = "Previous"
                    )
                } else {
                    null
                }
                val playPauseActionKey = createFocusBroadcastAction(
                    key = "miui.focus.action_play_pause",
                    requestCode = 3101,
                    action = "com.example.islandlyrics.ACTION_MEDIA_PLAY_PAUSE",
                    iconResId = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    title = if (state.isPlaying) "Pause" else "Play"
                )
                val nextActionKey = createFocusBroadcastAction(
                    key = "miui.focus.action_next",
                    requestCode = 3102,
                    action = "com.example.islandlyrics.ACTION_MEDIA_NEXT",
                    iconResId = R.drawable.ic_skip_next,
                    title = "Next"
                )

                if (showPrevButton) {
                    addActionInfo {
                        type = 0
                        action = prevActionKey
                        actionIcon = cachedPrevIcon?.let { createPicture("miui.focus.pic_btn_prev", it) }
                        actionIconDark = cachedPrevIconDark?.let { createPicture("miui.focus.pic_btn_prev_dark", it) }
                        clickWithCollapse = false
                    }
                }
                addActionInfo {
                    type = 0
                    action = playPauseActionKey
                    actionIcon = cachedPlayPauseIcon?.let { createPicture("miui.focus.pic_btn_play_pause", it) }
                    actionIconDark = cachedPlayPauseIconDark?.let { createPicture("miui.focus.pic_btn_play_pause_dark", it) }
                    clickWithCollapse = false
                }
                addActionInfo {
                    type = 0
                    action = nextActionKey
                    actionIcon = cachedNextIcon?.let { createPicture("miui.focus.pic_btn_next", it) }
                    actionIconDark = cachedNextIconDark?.let { createPicture("miui.focus.pic_btn_next_dark", it) }
                    clickWithCollapse = false
                }
            }
        } else {
            progressInfo {
                progress = progressPercent
                colorProgress = progressBarColor
                colorProgressEnd = progressBarColor
            }
        }

        island {
            islandProperty = 1
            if (showHighlightColor) {
                this.highlightColor = hexColor
            }

            bigIslandArea {
                applyBigIslandLyrics(
                    preferMetadataLayout = state.preferMetadataLayout,
                    isTimingGapPlaceholder = state.isTimingGapPlaceholder,
                    fullLyric = state.fullLyric,
                    displayLyric = displayLyric,
                    titleWithArtist = titleWithArtist,
                    islandKey = islandKey,
                    showHighlightColor = showHighlightColor,
                    title = state.title,
                    artist = state.artist
                )
            }

            if (cachedSuperIslandShareEnabled) {
                shareData {
                    pic = shareKey
                    title = state.title.ifEmpty { "♪" }
                    content = resolvePrimaryLyricText(state)
                    val shareArtist = if (state.artist.isNotBlank()) state.artist else "未知歌手"
                    val shareSong = state.title.ifEmpty { "未知歌曲" }
                    this.shareContent = when (cachedSuperIslandShareFormat) {
                        "format_2" -> "${resolvePrimaryLyricText(state)} -$shareArtist\uff0c$shareSong"
                        "format_3" -> "${resolvePrimaryLyricText(state)}\n$shareArtist\uff0c$shareSong"
                        else -> "${resolvePrimaryLyricText(state)}\n$shareSong by $shareArtist"
                    }
                }
            }

            smallIslandArea {
                combinePicInfo {
                    if (islandSmallKey != null) {
                        picInfo {
                            type = 1
                            pic = islandSmallKey
                        }
                    }
                    progressInfo {
                        progress = progressPercent
                        colorReach = ringColor
                        colorUnReach = "#333333"
                    }
                }
            }
        }
    }
        if (standardActionBundle.keySet().isNotEmpty()) {
            bundle.putBundle("miui.focus.actions", android.os.Bundle(standardActionBundle))
        }
        return bundle
    }

    private fun mergeCustomFocusWithStandardIsland(customExtras: android.os.Bundle, standardExtras: android.os.Bundle): android.os.Bundle {
        val merged = android.os.Bundle(customExtras)
        val customJson = customExtras.getString("miui.focus.param.custom") ?: return merged
        val standardJson = standardExtras.getString("miui.focus.param") ?: return merged

        runCatching {
            val customRoot = JSONObject(customJson)
            val standardRoot = JSONObject(standardJson)
            val island = standardRoot.optJSONObject("param_v2")?.optJSONObject("param_island")
            if (island != null) {
                customRoot.put("param_island", island)
                merged.putString("miui.focus.param.custom", customRoot.toString())
            }
        }

        return merged
    }

    companion object {
        private const val CHANNEL_ID = "lyric_capsule_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_NOTIFY_STEP_PERCENT = 2
    }
}
