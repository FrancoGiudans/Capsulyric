package com.example.islandlyrics.ui.overlay.floating

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.os.SystemClock
import android.provider.Settings
import android.text.Spanned
import android.view.Choreographer
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.example.islandlyrics.feature.settings.SettingsActivity
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.rules.ParserRule
import com.example.islandlyrics.rules.ParserRuleHelper
import com.example.islandlyrics.runtime.service.MediaMonitorService
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.views.OutlineTextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FloatingLyricsWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = AppPreferences.of(context)
    private val automation get() = instrumentation.uiAutomation
    private val repo get() = LyricRepository.getInstance()

    @Test
    fun realWindowRendersStableCompletionAndSeparatesCollapseFromClose() {
        assumeTrue("Grant display-over-other-apps permission to run this window test", Settings.canDrawOverlays(context))
        val trackedKeys = listOf(
            AppPreferences.Keys.UI_USE_MIUIX, AppPreferences.Keys.CARD_BLUR_ENABLED,
            AppPreferences.Keys.THEME_FOLLOW_SYSTEM, AppPreferences.Keys.THEME_DARK_MODE,
            LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED, FloatingLyricsRenderer.PREF_TEXT_STROKE,
            FloatingLyricsRenderer.PREF_TEXT_COLOR, FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR,
            FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT,
            FloatingLyricsRenderer.PREF_POS_X, FloatingLyricsRenderer.PREF_POS_Y,
            FloatingLyricsVisibility.RECOVERY, FloatingLyricsVisibility.MANUALLY_HIDDEN,
            FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE
        )
        val oldPrefs = prefs.all.filterKeys { it in trackedKeys }
        val serviceInfo = automation.serviceInfo
        val oldFlags = serviceInfo.flags
        serviceInfo.flags = oldFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val renderer = FloatingLyricsRenderer(context)
        val fixturePlayer = InstrumentationRegistry.getArguments().getString("playerPackage")
        val track = FloatingLyricsTrack(fixturePlayer ?: "test-player", "one", "", "Window test", "Capsulyric")
        val lyric = "逐字歌词完成！"
        val lyricColor = 0xFF184F9B.toInt()
        val artColor = 0xFF12EF67.toInt()
        val cover = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(artColor) }
        val state = UIState(
            title = track.title,
            artist = track.artist,
            fullLyric = lyric,
            albumArt = cover,
            lyricPresentation = LyricPresentation(
                currentLine = LyricPresentation.DisplayLine(
                    text = lyric,
                    startTime = 1_000,
                    endTime = 4_500,
                    syllables = listOf(SyllableInfo(1_000, 1_500, "逐字"), SyllableInfo(1_500, 2_500, "歌词完成"))
                )
            )
        )
        try {
            onMain {
                prefs.edit {
                    putBoolean(AppPreferences.Keys.UI_USE_MIUIX, true)
                    putBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, false)
                    putBoolean(AppPreferences.Keys.THEME_FOLLOW_SYSTEM, false)
                    putBoolean(AppPreferences.Keys.THEME_DARK_MODE, false)
                    putBoolean(LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED, true)
                    putBoolean(FloatingLyricsRenderer.PREF_TEXT_STROKE, true)
                    putBoolean(FloatingLyricsRenderer.PREF_TEXT_BACKGROUND, true)
                    putBoolean(FloatingLyricsRenderer.PREF_WORD_HIGHLIGHT, true)
                    putBoolean(FloatingLyricsRenderer.PREF_SHOW_ALBUM_ART, false)
                    putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, false)
                    putBoolean(FloatingLyricsRenderer.PREF_FOLLOW_ALBUM_COLOR, false)
                    putInt(FloatingLyricsRenderer.PREF_TEXT_COLOR, lyricColor)
                    putInt(FloatingLyricsRenderer.PREF_POS_X, 0)
                    putInt(FloatingLyricsRenderer.PREF_POS_Y, (84 * context.resources.displayMetrics.density).toInt())
                    putString(FloatingLyricsVisibility.RECOVERY, FloatingLyricsRecovery.NEXT_TRACK.value)
                }
                FloatingLyricsVisibility.updatePlayback(FloatingLyricsPlayback.PAUSED, track)
                renderer.updateTrack(track)
                renderer.start()
                renderer.render(state)
            }
            val expand = context.getString(R.string.floating_settings_expand)
            val openPlayer = context.getString(R.string.floating_window_open_player)
            val close = context.getString(R.string.floating_window_close)
            val beforeDrag = boundsOf(expand)
            screenshot("miuix-compact").let {
                assertFalse(it.containsColor(artColor))
                it.recycle()
            }
            dragDown(beforeDrag)
            waitUntil("Window position did not move after drag") { (findBounds(expand)?.top ?: 0) > beforeDrag.top + 20 }
            updatePosition(renderer, 2_500)
            val compactLyric = lyricInkBounds("miuix-compact-before-expand", lyricColor)
            val expandedAt = SystemClock.uptimeMillis()
            tap(boundsOf(expand))
            boundsOf(openPlayer)
            waitUntil("Expanded lyrics did not collapse after inactivity") { findBounds(expand) != null && findBounds(close) == null }
            assertTrue("Collapsed before the four-second inactivity period", SystemClock.uptimeMillis() - expandedAt >= 3_900)
            assertSamePosition(compactLyric, lyricInkBounds("miuix-compact-after-timeout", lyricColor))

            if (findBounds("Timer") != null) {
                tap(boundsOf(expand))
                boundsOf(openPlayer)
                tap(boundsOf("Timer"))
                waitUntil("Tapping the app outside did not collapse the overlay") { findBounds(expand) != null }
                waitUntil("The underlying Clock tab did not receive the tap") {
                    findBounds("0 hours, 0 minutes, 0 seconds") != null
                }
            }
            tap(boundsOf(expand))
            val playerBounds = boundsOf(openPlayer)
            val closeBounds = boundsOf(close)
            boundsOf(context.getString(R.string.action_play))

            val lyricBounds = boundsOf(lyric)
            updatePosition(renderer, 1_950)
            val singing = screenshot("miuix-light-singing")
            updatePosition(renderer, 2_500)
            val complete = screenshot("miuix-light-complete")
            updatePosition(renderer, 3_500)
            val after = screenshot("miuix-light-after")
            val backgroundX = playerBounds.left + playerBounds.width() / 6
            val backgroundY = playerBounds.top + playerBounds.height() / 6
            val panelColor = after.getPixel(backgroundX, backgroundY)
            try {
                assertTrue(after.containsColor(artColor))
                assertFalse(samePixels(singing, complete, lyricBounds))
                assertTrue(samePixels(complete, after, lyricBounds))
                assertSamePosition(compactLyric, checkNotNull(after.colorBounds(lyricColor)))
            } finally {
                singing.recycle()
                complete.recycle()
                after.recycle()
            }

            val settings = context.getString(R.string.settings_floating_lyrics)
            val expandedBounds = windowBoundsOf(openPlayer)
            tap(boundsOf(settings))
            val colorSetting = context.getString(R.string.settings_floating_follow_album_color)
            val colorTitle = context.getString(R.string.settings_floating_text_color)
            val popupBounds = windowBoundsOf(colorSetting)
            assertEquals(expandedBounds.left, popupBounds.left)
            assertEquals(expandedBounds.right, popupBounds.right)
            assertEquals(null, findBounds(colorTitle))
            screenshot("miuix-quick-settings").let { screen ->
                assertEquals(panelColor, screen.getPixel(popupBounds.left + 32, popupBounds.top + 32))
                screen.recycle()
            }
            SystemClock.sleep(4_100)
            boundsOf(colorSetting)
            val nextLine = context.getString(R.string.super_island_secondary_text_next_lyric)
            assertEquals(null, findBounds(nextLine))
            onMain { prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, true) } }
            screenshot("miuix-quick-settings-second-line").recycle()
            scrollTo(nextLine, popupBounds)
            onMain { prefs.edit { putBoolean(FloatingLyricsRenderer.PREF_SHOW_NEIGHBOR_LINE, false) } }
            waitUntil { findBounds(nextLine) == null }

            if (!popupBounds.contains(closeBounds.centerX(), closeBounds.centerY())) {
                tap(closeBounds)
                waitUntil { findBounds(colorSetting) == null && findBounds(openPlayer) != null }
                SystemClock.sleep(300)
                tap(boundsOf(settings))
                boundsOf(colorSetting)
            }
            automation.executeShellCommand("input keyevent 4").close()
            waitUntil { findBounds(colorSetting) == null && findBounds(openPlayer) != null }

            onMain { prefs.edit { putBoolean(AppPreferences.Keys.THEME_DARK_MODE, true) } }
            waitUntil("The expanded Miuix surface did not switch to dark colors") {
                val screen = checkNotNull(automation.takeScreenshot())
                try {
                    Color.luminance(screen.getPixel(backgroundX, backgroundY)) < 0.25f
                } finally {
                    screen.recycle()
                }
            }
            screenshot("miuix-dark").recycle()
            tap(boundsOf(lyric))
            boundsOf(expand)
            assertEquals(null, findBounds(close))
            assertSamePosition(compactLyric, lyricInkBounds("miuix-compact-after-collapse", lyricColor))

            onMain { prefs.edit { putBoolean(AppPreferences.Keys.UI_USE_MIUIX, false) } }
            val materialExpand = boundsOf(expand)
            val materialLyric = lyricInkBounds("material-compact-before-expand", lyricColor)
            tap(materialExpand)
            boundsOf(close)
            screenshot("material-expanded").let {
                assertTrue(it.containsColor(artColor))
                assertSamePosition(materialLyric, checkNotNull(it.colorBounds(lyricColor)))
                it.recycle()
            }
            val materialBounds = windowBoundsOf(openPlayer)
            click(settings)
            val albumColor = context.getString(R.string.floating_settings_color_album)
            val materialSettingsBounds = windowBoundsOf(albumColor)
            assertEquals(materialBounds.left, materialSettingsBounds.left)
            assertEquals(materialBounds.right, materialSettingsBounds.right)
            assertEquals(null, findBounds(colorTitle))
            screenshot("material-quick-settings").recycle()
            runInput("input keyevent 4")
            waitUntil { findBounds(albumColor) == null && findBounds(openPlayer) != null }
            tap(boundsOf(lyric))
            boundsOf(expand)
            assertSamePosition(materialLyric, lyricInkBounds("material-compact-after-collapse", lyricColor))
            val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
            val bottomInset = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            ).bottom
            val bottom = metrics.bounds.height() - bottomInset
            val edgeY = bottom - (64 * context.resources.displayMetrics.density).toInt()
            onMain { prefs.edit { putInt(FloatingLyricsRenderer.PREF_POS_Y, edgeY) } }
            val edgeLyric = lyricInkBounds("material-compact-near-bottom", lyricColor)
            click(expand)
            val edgeScreenshot = screenshot("material-expanded-near-bottom")
            try {
                val edgeExpanded = checkNotNull(edgeScreenshot.colorBounds(lyricColor))
                assertTrue("Lyrics must move up when the controls would exceed the screen", edgeExpanded.top < edgeLyric.top)
                val controlWidth = (24 * context.resources.displayMetrics.density).toInt()
                val playArea = Rect(
                    edgeExpanded.centerX() - controlWidth, edgeExpanded.bottom,
                    edgeExpanded.centerX() + controlWidth, bottom
                )
                assertTrue("The play icon must be visible above the navigation bar",
                    !playArea.isEmpty && edgeScreenshot.containsColor(Color.WHITE, playArea))
            } finally {
                edgeScreenshot.recycle()
            }
            click(close)
            waitUntil("The hide button did not remove the floating window") {
                FloatingLyricsVisibility.session.value.isHidden && findBounds(close) == null && findBounds(expand) == null
            }
            assertTrue(prefs.getBoolean(LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED, false))
            assertTrue(FloatingLyricsVisibility.session.value.isHidden)

            onMain {
                FloatingLyricsVisibility.showNow(prefs)
                renderer.start()
            }
            tap(boundsOf(expand))
            onMain { prefs.edit { putString(FloatingLyricsVisibility.RECOVERY, FloatingLyricsRecovery.OFF.value) } }
            click(close)
            waitUntil("Manual hide did not take effect") {
                FloatingLyricsVisibility.session.value.isHidden && findBounds(close) == null
            }
            assertTrue(prefs.getBoolean(LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED, false))
            assertTrue(prefs.getBoolean(FloatingLyricsVisibility.MANUALLY_HIDDEN, false))
            onMain {
                FloatingLyricsVisibility.showNow(prefs)
                renderer.start()
            }
            boundsOf(expand)
            assertFalse(prefs.getBoolean(FloatingLyricsVisibility.MANUALLY_HIDDEN, false))
            if (fixturePlayer != null) {
                click(expand)
                click(openPlayer)
                waitUntil("The current player did not open") {
                    automation.rootInActiveWindow?.packageName?.toString() == fixturePlayer
                }
                boundsOf(expand)
                assertEquals(null, findBounds(openPlayer))
            }
        } finally {
            onMain {
                renderer.stop()
                FloatingLyricsVisibility.showNow(prefs)
                FloatingLyricsVisibility.updatePlayback(FloatingLyricsPlayback.INACTIVE)
                prefs.edit {
                    trackedKeys.forEach { key ->
                        when (val value = oldPrefs[key]) {
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is String -> putString(key, value)
                            null -> remove(key)
                        }
                    }
                }
            }
            serviceInfo.flags = oldFlags
            automation.serviceInfo = serviceInfo
        }
    }

    @Test
    fun alternatingLyricsKeepTheirPositionAndSizeWhenTheyBecomeActive() = onMain {
        val view = FloatingLyricsContentView(context)
        val upper = (view.getChildAt(0) as ViewGroup).getChildAt(1) as TextView
        val lower = (view.getChildAt(1) as ViewGroup).getChildAt(1) as TextView
        val config = FloatingLyricsDisplayConfig(
            listOf(SecondaryTextMode.NEXT_LYRIC), true, FloatingLyricsNeighborAlignment.SPLIT_START_END, true
        )
        val style = FloatingLyricsStyleStore(context).style.copy(textSizeSp = 20f, enableTextStroke = true)
        val first = LyricPresentation.DisplayLine("First", startTime = 100)
        val second = LyricPresentation.DisplayLine("Second", startTime = 200)
        val third = LyricPresentation.DisplayLine("Third", startTime = 300)
        val progress = LyricPresentation.WordProgress("Sec", 1, 2, 0.5f)
        fun render(presentation: LyricPresentation) {
            view.render(UIState(lyricPresentation = presentation), style, config, Color.WHITE, "")
        }

        render(LyricPresentation(currentLine = first, nextLine = second, currentLineIndex = 0))
        assertEquals("First", upper.text.toString())
        assertEquals("Second", lower.text.toString())
        assertEquals(Gravity.START, upper.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        assertEquals(Gravity.END, lower.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        assertEquals(upper.textSize, lower.textSize, 0f)
        val upcomingColor = lower.currentTextColor

        render(LyricPresentation(currentLine = second, previousLine = first, nextLine = third, currentLineIndex = 1))
        view.renderWordProgress(progress)
        assertEquals("Third", upper.text.toString())
        assertEquals("Second", lower.text.toString())
        assertEquals(upcomingColor, upper.currentTextColor)
        assertEquals(Color.WHITE, lower.currentTextColor)
        assertTrue(lower.text is Spanned)

        render(LyricPresentation(currentLine = third, previousLine = second, currentLineIndex = 2))
        assertEquals("Third", upper.text.toString())
        assertEquals(Color.WHITE, upper.currentTextColor)
        render(LyricPresentation(currentLine = first, nextLine = second, currentLineIndex = 0))
        assertEquals("First", upper.text.toString())
        assertEquals("Second", lower.text.toString())
    }

    @Test
    fun extraOutlineFollowsTheSystemThemeAndStaysThin() = onMain {
        val light = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val dark = Configuration(light).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val view = OutlineTextView(context.createConfigurationContext(light))
        val outline = view.getChildAt(0) as TextView
        view.setStyle(Color.BLACK, 20f, Gravity.CENTER, stroke = true)
        assertEquals(0, outline.currentTextColor and 0xFFFFFF)
        assertTrue(Color.alpha(outline.currentTextColor) in 1..127)
        assertTrue(outline.paint.strokeWidth in 0f..(context.resources.displayMetrics.density * 0.65f))

        view.dispatchConfigurationChanged(dark)
        view.setStyle(Color.WHITE, 20f, Gravity.CENTER, stroke = true)
        assertEquals(0xFFFFFF, outline.currentTextColor and 0xFFFFFF)
        view.dispatchConfigurationChanged(light)
        assertEquals(0, outline.currentTextColor and 0xFFFFFF)

        view.setStyle(Color.WHITE, 20f, Gravity.CENTER, stroke = false)
        assertEquals(View.GONE, outline.visibility)
    }

    @Test
    fun desktopLyricsPreviewAndAppearanceStayAvailableWhenDisabled() {
        val keys = listOf(AppPreferences.Keys.UI_USE_MIUIX, LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED)
        val oldPrefs = prefs.all.filterKeys { it in keys }
        val serviceInfo = automation.serviceInfo
        val oldFlags = serviceInfo.flags
        serviceInfo.flags = oldFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        try {
            for (miuix in listOf(false, true)) {
                onMain {
                    prefs.edit {
                        putBoolean(AppPreferences.Keys.UI_USE_MIUIX, miuix)
                        putBoolean(LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED, false)
                    }
                }
                val activity = instrumentation.startActivitySync(
                    Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                try {
                    val desktopLyrics = context.getString(R.string.settings_floating_lyrics)
                    tap(boundsOf(desktopLyrics))
                    val preview = context.getString(R.string.settings_floating_preview)
                    val previewBounds = boundsOf(preview)
                    assertEquals(preview, findNode(preview)?.contentDescription?.toString())
                    tap(previewBounds)
                    boundsOf(context.getString(R.string.media_control_unknown_title))
                    screenshot(if (miuix) "miuix-settings-preview" else "material-settings-preview").recycle()
                    assertFalse(LabFeatureManager.isFloatingLyricsEnabled(prefs))
                    val settingsWindow = windowBoundsOf(preview)
                    scrollTo(context.getString(R.string.settings_floating_text_stroke), settingsWindow)
                } finally {
                    onMain { activity.finish() }
                }
            }
        } finally {
            onMain {
                prefs.edit {
                    for (key in keys) {
                        val value = oldPrefs[key] as? Boolean
                        if (value == null) remove(key) else putBoolean(key, value)
                    }
                }
            }
            serviceInfo.flags = oldFlags
            automation.serviceInfo = serviceInfo
        }
    }

    @Test
    fun mediaSessionKeepsThePausedWindowAndReopensOnTheNextTrack() {
        val notifications = context.getSystemService(NotificationManager::class.java)
        assumeTrue(Settings.canDrawOverlays(context))
        assumeTrue(notifications.isNotificationListenerAccessGranted(ComponentName(context, MediaMonitorService::class.java)))
        val keys = listOf(
            AppPreferences.Keys.PARSER_RULES_JSON, AppPreferences.Keys.UI_USE_MIUIX,
            AppPreferences.Keys.FULLY_OFFLINE_MODE, LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED,
            FloatingLyricsVisibility.RECOVERY, FloatingLyricsVisibility.MANUALLY_HIDDEN,
            FloatingLyricsVisibility.KEEP_ON_PAUSE, "service_enabled", "notification_dismiss_delay"
        )
        val oldPrefs = prefs.all.filterKeys { it in keys }
        val serviceInfo = automation.serviceInfo
        val oldFlags = serviceInfo.flags
        serviceInfo.flags = oldFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val media = MediaSession(context, "desktop-lyrics-window-test")
        try {
            onMain {
                prefs.edit {
                    putBoolean(AppPreferences.Keys.UI_USE_MIUIX, false)
                    putBoolean(AppPreferences.Keys.FULLY_OFFLINE_MODE, true)
                    putBoolean(LabFeatureManager.KEY_FLOATING_LYRICS_ENABLED, true)
                    putString(FloatingLyricsVisibility.RECOVERY, FloatingLyricsRecovery.NEXT_TRACK.value)
                    putBoolean(FloatingLyricsVisibility.KEEP_ON_PAUSE, true)
                    putBoolean("service_enabled", true)
                    putLong("notification_dismiss_delay", 0)
                }
                ParserRuleHelper.saveRules(context, listOf(ParserRule(context.packageName, usesCarProtocol = false)))
                media.setCallback(object : MediaSession.Callback() {
                    override fun onPlay() = playback(media, PlaybackState.STATE_PLAYING)
                    override fun onPause() = playback(media, PlaybackState.STATE_PAUSED)
                })
                media.setMetadata(metadata("one", "First track"))
                playback(media, PlaybackState.STATE_PLAYING)
                media.isActive = true
            }
            val expand = context.getString(R.string.floating_settings_expand)
            val pause = context.getString(R.string.action_pause)
            val play = context.getString(R.string.action_play)
            val close = context.getString(R.string.floating_window_close)
            tap(boundsOf(expand))
            tap(boundsOf(pause))
            waitUntil("The paused window should remain visible after the lyric notification is removed") {
                FloatingLyricsVisibility.session.value.playback == FloatingLyricsPlayback.PAUSED &&
                    findBounds(play) != null &&
                    notifications.activeNotifications.none { it.id == 1001 }
            }
            tap(boundsOf(play))
            boundsOf(pause)
            tap(boundsOf(close))
            waitUntil { FloatingLyricsVisibility.session.value.isHidden && findBounds(expand) == null }

            onMain { media.setMetadata(metadata("one", "First track")) }
            assertTrue(FloatingLyricsVisibility.session.value.isHidden)
            onMain { media.setMetadata(metadata("two", "Second track")) }
            boundsOf(expand)
            assertFalse(FloatingLyricsVisibility.session.value.isHidden)

            onMain { prefs.edit { putString(FloatingLyricsVisibility.RECOVERY, FloatingLyricsRecovery.ON_RESUME.value) } }
            click(expand)
            click(close)
            waitUntil { FloatingLyricsVisibility.session.value.isHidden && findBounds(expand) == null }
            onMain { playback(media, PlaybackState.STATE_PAUSED) }
            waitUntil { FloatingLyricsVisibility.session.value.playback == FloatingLyricsPlayback.PAUSED }
            assertTrue(FloatingLyricsVisibility.session.value.isHidden)
            onMain { playback(media, PlaybackState.STATE_PLAYING) }
            boundsOf(expand)
            assertFalse(FloatingLyricsVisibility.session.value.isHidden)

            onMain { prefs.edit { putString(FloatingLyricsVisibility.RECOVERY, FloatingLyricsRecovery.OFF.value) } }
            val restore = context.getString(R.string.settings_floating_lyrics_show)
            for (miuix in listOf(false, true)) {
                onMain { prefs.edit { putBoolean(AppPreferences.Keys.UI_USE_MIUIX, miuix) } }
                tap(boundsOf(expand))
                tap(boundsOf(close))
                waitUntil { FloatingLyricsVisibility.session.value.isHidden && findBounds(expand) == null }
                context.startActivity(Intent(context, MediaControlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                boundsOf(restore)
                tap(boundsOf(restore))
                boundsOf(expand)
                assertFalse(FloatingLyricsVisibility.session.value.isHidden)
                waitUntil { findBounds(restore) == null }
                waitUntil { automation.rootInActiveWindow?.packageName?.toString() != context.packageName }
            }

            onMain {
                prefs.edit { putBoolean(FloatingLyricsVisibility.KEEP_ON_PAUSE, false) }
                playback(media, PlaybackState.STATE_PAUSED)
            }
            waitUntil { FloatingLyricsVisibility.session.value.playback == FloatingLyricsPlayback.PAUSED && findBounds(expand) == null }
            onMain {
                playback(media, PlaybackState.STATE_PLAYING)
            }
            boundsOf(expand)
            onMain { media.release() }
            waitUntil { FloatingLyricsVisibility.session.value.playback == FloatingLyricsPlayback.INACTIVE && findBounds(expand) == null }
        } finally {
            onMain {
                media.release()
                FloatingLyricsVisibility.showNow(prefs)
                prefs.edit {
                    for (key in keys) {
                        when (val value = oldPrefs[key]) {
                            is Boolean -> putBoolean(key, value)
                            is String -> putString(key, value)
                            is Long -> putLong(key, value)
                            null -> remove(key)
                        }
                    }
                }
            }
            serviceInfo.flags = oldFlags
            automation.serviceInfo = serviceInfo
        }
    }

    private fun playback(session: MediaSession, state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY)
                .setState(state, 1_000, if (state == PlaybackState.STATE_PLAYING) 1f else 0f)
                .build()
        )
    }

    private fun metadata(id: String, title: String): MediaMetadata = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Capsulyric")
        .putLong(MediaMetadata.METADATA_KEY_DURATION, 90_000)
        .build()

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun updatePosition(renderer: FloatingLyricsRenderer, position: Long) = onMain {
        repo.updateProgress(position, 5_000)
        renderer.onPlaybackPositionChanged()
    }

    private fun screenshot(name: String): Bitmap {
        instrumentation.waitForIdleSync()
        automation.waitForIdle(100, 5_000)
        val nextDraw = CountDownLatch(1)
        onMain {
            val choreographer = Choreographer.getInstance()
            choreographer.postFrameCallback { choreographer.postFrameCallback { nextDraw.countDown() } }
        }
        check(nextDraw.await(3, TimeUnit.SECONDS))
        SystemClock.sleep(100)
        val bitmap = checkNotNull(automation.takeScreenshot())
        if (InstrumentationRegistry.getArguments().getString("capture") == "true") {
            val directory = File(context.filesDir, "floating-lyrics-qa").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }

    private fun crop(bitmap: Bitmap, bounds: Rect): Bitmap =
        Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())

    private fun Bitmap.containsColor(color: Int, bounds: Rect = Rect(0, 0, width, height)): Boolean {
        val row = IntArray(bounds.width())
        for (y in bounds.top until bounds.bottom) {
            getPixels(row, 0, row.size, bounds.left, y, row.size, 1)
            if (color in row) return true
        }
        return false
    }

    private fun lyricInkBounds(name: String, color: Int): Rect {
        val bitmap = screenshot(name)
        return try {
            checkNotNull(bitmap.colorBounds(color)) { "No lyric pixels in $name" }
        } finally {
            bitmap.recycle()
        }
    }

    private fun Bitmap.colorBounds(color: Int): Rect? {
        val result = Rect()
        val row = IntArray(width)
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            val first = row.indexOf(color)
            if (first >= 0) result.union(first, y, row.lastIndexOf(color) + 1, y + 1)
        }
        return result.takeUnless { it.isEmpty }
    }

    private fun assertSamePosition(before: Rect, after: Rect) {
        assertTrue("Lyric moved from $before to $after", kotlin.math.abs(before.left - after.left) <= 1 &&
            kotlin.math.abs(before.top - after.top) <= 1)
    }

    private fun samePixels(first: Bitmap, second: Bitmap, bounds: Rect): Boolean {
        val firstCrop = crop(first, bounds)
        val secondCrop = crop(second, bounds)
        return try {
            firstCrop.sameAs(secondCrop)
        } finally {
            firstCrop.recycle()
            secondCrop.recycle()
        }
    }

    private fun boundsOf(label: String): Rect {
        instrumentation.waitForIdleSync()
        automation.waitForIdle(150, 5_000)
        var bounds: Rect? = null
        try {
            waitUntil("Missing accessibility node: $label") {
                val previous = bounds
                bounds = findBounds(label)
                bounds != null && bounds == previous
            }
        } catch (e: AssertionError) {
            val nodes = buildList {
                automation.windows.forEach { window -> window.root?.let { collectLabels(it, this) } }
            }
            throw AssertionError("Missing accessibility node: $label. Visible labels: $nodes", e)
        }
        return checkNotNull(bounds)
    }

    private fun collectLabels(node: AccessibilityNodeInfo, labels: MutableList<String>) {
        if (node.isVisibleToUser && (node.text != null || node.contentDescription != null)) {
            labels.add("${node.text} / ${node.contentDescription}")
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collectLabels(it, labels) }
    }

    private fun findBounds(label: String): Rect? {
        val node = findNode(label) ?: return null
        return Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty }
    }

    private fun windowBoundsOf(label: String): Rect {
        boundsOf(label)
        return checkNotNull(automation.windows.firstNotNullOfOrNull { window ->
            if (window.root?.let { findNode(it, label) } == null) null
            else Rect().also(window::getBoundsInScreen)
        })
    }

    private fun findNode(label: String): AccessibilityNodeInfo? {
        automation.clearCache()
        return automation.windows.firstNotNullOfOrNull { window ->
            window.root?.let { findNode(it, label) }
        }
    }

    private fun click(label: String) {
        // A recently moved overlay can still expose its old node coordinates.
        val action = AccessibilityNodeInfo.ACTION_CLICK
        waitUntil("Click action unavailable: $label") { findNode(label)?.performAction(action) == true }
    }

    private fun findNode(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val match = node.getChild(i)?.let { findNode(it, label) }
            if (match != null) return match
        }
        return null
    }

    private fun waitUntil(message: String = "Window did not reach the expected state", predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(80)
        }
        assertTrue(message, predicate())
    }

    private fun tap(bounds: Rect) {
        runInput("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }

    private fun dragDown(bounds: Rect) {
        val now = SystemClock.uptimeMillis()
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        touch(now, MotionEvent.ACTION_DOWN, x, y)
        for (step in 1..8) {
            SystemClock.sleep(20)
            touch(now, MotionEvent.ACTION_MOVE, x, y + step * 12)
        }
        touch(now, MotionEvent.ACTION_UP, x, y + 96)
    }

    private fun scrollTo(label: String, window: Rect): Rect {
        repeat(4) {
            findBounds(label)?.let { return it }
            val x = window.centerX()
            val start = window.top + window.height() * 3 / 4
            val end = window.top + window.height() / 4
            runInput("input swipe $x $start $x $end 350")
            SystemClock.sleep(350)
        }
        return boundsOf(label)
    }

    private fun runInput(command: String) {
        AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun touch(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(automation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }
}
