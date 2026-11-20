/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.overlay.floating
import com.example.islandlyrics.ui.overlay.model.UIState
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.isNotEmpty
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.core.theme.ThemeHelper
import com.example.islandlyrics.ui.miuix.theme.isMiuixEnabled
import com.example.islandlyrics.lyrics.state.LyricRepository
import com.example.islandlyrics.ui.overlay.model.LyricPresentation
import com.example.islandlyrics.ui.overlay.model.SecondaryTextMode
import java.util.function.Consumer

/**
 * FloatingLyricsRenderer
 *
 * Two-state System Alert Window overlay backed by a transparent Dialog window.
 *
 * MINIMAL  — transparent background (or dark subtle background if enabled), centered lyrics with optional album art on left.
 * EXPANDED — translucent rounded pill with optional system blur behind it, title/artist header,
 *   lyrics with optional album art on left, one control row:
 *   Playback controls, settings, a player shortcut and a hide action.
 *
 * Font size, color, stroke, background style, and album art visibility are driven by SharedPreferences.
 * Dragging uses [FloatingLyricsDraggableFrameLayout] (threshold-based, non-conflicting with clicks).
 */
class FloatingLyricsRenderer(
    private val context: Context,
    dispatchMediaAction: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "FloatingLyricsRenderer"
        const val PREF_TEXT_SIZE       = FloatingLyricsStyleStore.KEY_TEXT_SIZE
        const val PREF_TEXT_WEIGHT     = AppPreferences.Keys.FLOATING_TEXT_WEIGHT
        const val PREF_TEXT_COLOR      = FloatingLyricsStyleStore.KEY_TEXT_COLOR
        const val PREF_FOLLOW_ALBUM_COLOR = FloatingLyricsStyleStore.KEY_FOLLOW_ALBUM_COLOR
        const val PREF_SHOW_ALBUM_ART  = FloatingLyricsStyleStore.KEY_SHOW_ALBUM_ART
        const val PREF_TEXT_STROKE     = FloatingLyricsStyleStore.KEY_TEXT_STROKE
        const val PREF_TEXT_BACKGROUND = FloatingLyricsStyleStore.KEY_TEXT_BACKGROUND
        const val PREF_DISPLAY_MODE = FloatingLyricsDisplayConfig.KEY_DISPLAY_MODE
        const val PREF_SECONDARY_TEXT_MODES = FloatingLyricsDisplayConfig.KEY_SECONDARY_TEXT_MODES
        const val PREF_SHOW_NEIGHBOR_LINE = FloatingLyricsDisplayConfig.KEY_SHOW_NEIGHBOR_LINE
        const val PREF_NEIGHBOR_ALIGNMENT = FloatingLyricsDisplayConfig.KEY_NEIGHBOR_ALIGNMENT
        const val PREF_WORD_HIGHLIGHT = FloatingLyricsDisplayConfig.KEY_WORD_HIGHLIGHT
        const val PREF_POS_X           = FloatingLyricsWindowPositionStore.KEY_POS_X
        const val PREF_POS_Y           = FloatingLyricsWindowPositionStore.KEY_POS_Y

        private const val POPUP_DISMISS_DEBOUNCE_MS = 250L
        private const val EXPANDED_TIMEOUT_MS = 4_000L
        private val POPUP_TEXT_COLORS = intArrayOf(
            Color.WHITE,
            Color.BLACK,
            0xFFCCFFFF.toInt(),
            0xFFCCFFCC.toInt(),
            0xFFFFCC99.toInt(),
            0xFFFF99CC.toInt()
        )

        fun resetPosition(context: Context) {
            AppPreferences.of(context)
                .edit {
                    remove(PREF_POS_X)
                    remove(PREF_POS_Y)
                }
        }
    }

    // ── State ────────────────────────────────────────────────────────────────
    private enum class DisplayState { MINIMAL, EXPANDED }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val collapseAfterIdle = Runnable { switchToMinimal() }
    private var wm: WindowManager? = null
    private var overlayDialog: Dialog? = null
    private var overlayWindow: Window? = null
    private var rootView: FloatingLyricsDraggableFrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var pendingLyricAnchor: Point? = null
    private var compactLyricOffset: Point? = null
    private var settingsPopup: Dialog? = null
    private var settingsComposeHost: FloatingLyricsComposeHost? = null
    private var settingsBlur by mutableStateOf(false)
    private var miuixPanel: FloatingLyricsMiuixPanel? = null
    private var miuixThemeBackground: Int? = null
    private var miuixSurfaceColor: Int? = null
    private var settingsPopupScrollView: ScrollView? = null
    private var settingsPopupDismissedAtMs: Long = 0L
    private var settingsPopupRefreshQueued = false

    private val styleStore = FloatingLyricsStyleStore(context)
    private val positionStore = FloatingLyricsWindowPositionStore(context)
    private val actionController = FloatingLyricsActionController(
        context = context,
        onClosed = { stop() },
        onMediaAction = { action ->
            restartCollapseTimer()
            dispatchMediaAction(action)
        }
    )

    // View refs
    private var minimalContentView: FloatingLyricsContentView? = null
    private var expandedTitleTv: TextView? = null
    private var expandedArtistTv: TextView? = null
    private var expandedContentView: FloatingLyricsContentView? = null
    private var btnPlayPause: ImageButton? = null
    
    // Album Art ImageViews
    private var minimalAlbumArtIv: ImageView? = null
    private var expandedAlbumArtIv: ImageView? = null

    private var minimalContainer: View? = null
    private var minimalTextBackgroundContainer: View? = null
    private var expandedContainer: View? = null

    private var currentState = DisplayState.MINIMAL
    private var lastState: UIState? = null
    private var boundState: UIState? = null
    private var lastAppliedSnapshot: ViewSnapshot? = null
    private var displayConfig by mutableStateOf(FloatingLyricsDisplayConfig.from(prefs()))
    private var chrome = FloatingLyricsChrome.from(prefs())
    private var crossWindowBlurEnabled = false
    /** Desired lifecycle state.  Unlike [isRunning], this includes queued work. */
    private var shouldBeRunning = false

    private val crossWindowBlurListener = Consumer<Boolean> { enabled ->
        mainHandler.post {
            if (!isRunning) return@post
            crossWindowBlurEnabled = enabled
            updateWindowBlur()
            requestLineUpdate()
        }
    }

    var isRunning = false
        private set

    // ── 逐字平滑动画：悬浮窗专属高频刷新队列（30/60 FPS） ────────────────────
    // 与全局 120ms 显示循环解耦：只负责当前句的逐字进度插值渲染。
    private val wordAnimationHandler = Handler(Looper.getMainLooper())
    private var wordAnimationActive = false
    private val wordPlayback = FloatingWordPlayback()
    private var completedWordLine: LyricPresentation.DisplayLine? = null
    private var parsedLyrics: LyricRepository.ParsedLyricsInfo? = null
    private var timeline: FloatingLyricsTimeline? = null
    private var clock: FloatingLyricsClock? = null
    private var track: FloatingLyricsTrack? = null
    private var playing = false
    private val changeLine = Runnable {
        if (isRunning && shouldBeRunning) renderLatestState()
    }

    private val wordAnimationRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (!shouldRunWordAnimation()) {
                stopWordAnimation()
                return
            }
            val progress = renderWordFrame()
            if (progress == null || progress.isFullySung()) {
                stopWordAnimation()
            } else {
                wordAnimationHandler.postDelayed(this, wordAnimationIntervalMs())
            }
        }
    }

    private data class ViewSnapshot(
        val lyric: String,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val style: FloatingLyricsStyle,
        val albumColor: Int,
        val compactBackground: Int,
        val expandedBackground: Int,
        val albumArtHash: Int,
        val displayConfig: FloatingLyricsDisplayConfig,
        val presentation: LyricPresentation
    )

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_TEXT_SIZE, PREF_TEXT_WEIGHT, PREF_TEXT_COLOR, PREF_FOLLOW_ALBUM_COLOR,
            PREF_SHOW_ALBUM_ART, PREF_TEXT_STROKE, PREF_TEXT_BACKGROUND,
            PREF_DISPLAY_MODE, PREF_SECONDARY_TEXT_MODES, PREF_SHOW_NEIGHBOR_LINE, PREF_NEIGHBOR_ALIGNMENT,
            PREF_WORD_HIGHLIGHT, LabFeatureManager.KEY_FLOATING_WORD_HIGH_FPS_ENABLED -> {
                mainHandler.post {
                    if (!isRunning || !shouldBeRunning) return@post
                    loadPrefs()
                    renderLatestState()
                }
            }
            AppPreferences.Keys.CARD_BLUR_ENABLED -> mainHandler.post { updateWindowBlur() }
            AppPreferences.Keys.UI_USE_MIUIX -> mainHandler.post { remountForTheme() }
            AppPreferences.Keys.THEME_FOLLOW_SYSTEM, AppPreferences.Keys.THEME_DARK_MODE,
            AppPreferences.Keys.MATERIAL_THEME_FOLLOW_SYSTEM, AppPreferences.Keys.MATERIAL_THEME_DARK_MODE ->
                requestLineUpdate()
            PREF_POS_X, PREF_POS_Y -> {
                mainHandler.post { moveWindowToStoredPosition() }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        if (shouldBeRunning) return
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted"); return
        }
        shouldBeRunning = true
        mainHandler.post {
            // A stop request may have cancelled this queued start.
            if (!shouldBeRunning || isRunning) return@post
            prefs().registerOnSharedPreferenceChangeListener(prefChangeListener)
            loadPrefs()
            if (!attachWindow()) {
                prefs().unregisterOnSharedPreferenceChangeListener(prefChangeListener)
                shouldBeRunning = false
                return@post
            }
            switchToMinimal()
        }
    }

    fun stop() {
        if (!shouldBeRunning && !isRunning) return
        shouldBeRunning = false
        mainHandler.post {
            // A newer start request supersedes this queued stop.
            if (shouldBeRunning || !isRunning) return@post
            prefs().unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            detachWindow() 
        }
    }

    fun render(state: UIState) {
        // Keep the latest state even while the window is being attached.  The
        // first state often arrives before the asynchronous Dialog mount.
        lastState = state
        if (!isRunning || !shouldBeRunning) return
        mainHandler.post {
            if (isRunning && shouldBeRunning && lastState === state) applyState(state)
        }
    }

    internal fun updatePlayback(session: FloatingLyricsSession) {
        val now = SystemClock.elapsedRealtime()
        val nextTrack = session.track
        val trackChanged = nextTrack != null && track?.let(nextTrack::differsFrom) == true
        track = nextTrack
        if (trackChanged) {
            lastState = null
            lastAppliedSnapshot = null
            completedWordLine = null
            wordPlayback.acceptPosition(null, now)
            stopWordAnimation()
        }
        val isPlaying = session.playback == FloatingLyricsPlayback.PLAYING
        if (!trackChanged && playing == isPlaying && clock == session.clock) return
        playing = isPlaying
        clock = session.clock
        wordPlayback.setPlaying(isPlaying, now)
        requestLineUpdate()
    }

    fun onParsedLyricsChanged(parsed: LyricRepository.ParsedLyricsInfo?) {
        parsedLyrics = parsed
        timeline = FloatingLyricsTimeline.from(parsed)
        requestLineUpdate()
    }

    fun onLyricChanged() {
        if (timeline == null) requestLineUpdate()
    }

    fun onPlaybackPositionChanged() {
        val position = LyricRepository.getInstance().liveProgress.value?.position
        if (wordPlayback.acceptPosition(position, SystemClock.elapsedRealtime())) {
            completedWordLine = null
        }
        requestLineUpdate()
    }

    private fun requestLineUpdate() {
        if (!isRunning || !shouldBeRunning) return
        mainHandler.removeCallbacks(changeLine)
        mainHandler.post(changeLine)
    }

    // ── Prefs ─────────────────────────────────────────────────────────────────

    private fun prefs() =
        AppPreferences.of(context)

    private fun loadPrefs() {
        styleStore.reload()
        displayConfig = FloatingLyricsDisplayConfig.from(prefs())
        chrome = FloatingLyricsChrome.from(prefs())
    }

    // ── Window ────────────────────────────────────────────────────────────────

    private fun attachWindow(): Boolean {
        if (isRunning) return true

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager
        crossWindowBlurEnabled = windowManager.isCrossWindowBlurEnabled

        val screenW = windowManager.currentWindowMetrics.bounds.width()
        val useMiuix = isMiuixEnabled(context)
        val overlayW = overlayWidth(screenW, useMiuix)

        val params = WindowManager.LayoutParams(
            overlayW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val position = positionStore.load(overlayW)
            x = position.x
            y = position.y
            if (position.fromPrefs) positionStore.save(x, y)
        }
        windowParams = params

        val root = FloatingLyricsDraggableFrameLayout(
            context = context,
            dragThresholdPx = dpToPx(10),
            onDrag = { dx, dy ->
                params.x += dx
                params.y += dy
                applyWindowAttributes(params)
            },
            onDragEnd = {
                fitWindowInDisplay()
                val shift = offsetFromCompactPosition()
                val compactPosition = positionStore.clamp(params.x - shift.x, params.y - shift.y, params.width)
                positionStore.save(compactPosition.x, compactPosition.y)
            }
        )
        rootView = root
        if (useMiuix) {
            val minimal = FloatingLyricsContentView(context).also { minimalContentView = it }
            val expanded = FloatingLyricsContentView(context).also { expandedContentView = it }
            val panel = FloatingLyricsMiuixPanel(
                context, minimal, expanded, actionController, ::switchToExpanded, ::switchToMinimal,
                ::toggleSettingsPopup, ::onMiuixBackgroundColorsChanged
            )
            miuixPanel = panel
            panel.installOwners(root)
            root.addView(panel.view, contentLayoutParams())
        } else {
            root.addView(buildMinimalView().also { minimalContainer = it }, contentLayoutParams())
            root.addView(buildExpandedView().also { expandedContainer = it }, contentLayoutParams())
        }
        root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (pendingLyricAnchor == null && bottom - top != oldBottom - oldTop) fitWindowInDisplay()
        }

        val dialog = object : Dialog(context, R.style.Theme_IslandLyrics_Transparent) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (currentState == DisplayState.EXPANDED && settingsPopup == null) switchToMinimal()
                    return false
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) mainHandler.removeCallbacks(collapseAfterIdle)
                val handled = super.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    restartCollapseTimer()
                }
                return handled
            }
        }.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(root)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
        val window = dialog.window ?: return false
        miuixPanel?.installOwners(window.decorView)
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(params.flags)
        window.attributes = params
        window.decorView.setPadding(0, 0, 0, 0)
        overlayDialog = dialog
        overlayWindow = window

        try {
            dialog.show()
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Unable to attach desktop lyrics", e)
            detachWindow()
            return false
        } catch (e: SecurityException) {
            Log.w(TAG, "Overlay permission unavailable", e)
            detachWindow()
            return false
        }
        window.attributes = params
        window.decorView.setPadding(0, 0, 0, 0)
        isRunning = true
        root.viewTreeObserver.addOnPreDrawListener {
            val anchor = pendingLyricAnchor ?: return@addOnPreDrawListener true
            val offset = currentLyricOffset() ?: return@addOnPreDrawListener true
            pendingLyricAnchor = null
            // Wait for WindowManager to apply the new position before drawing the new shape.
            !fitWindowInDisplay(Point(anchor.x - offset.x, anchor.y - offset.y))
        }
        windowManager.addCrossWindowBlurEnabledListener(crossWindowBlurListener)
        Log.i(TAG, "Floating lyrics overlay attached")
        return true
    }

    private fun detachWindow() {
        stopWordAnimation()
        mainHandler.removeCallbacks(changeLine)
        dismissSettingsPopup()
        mainHandler.removeCallbacks(collapseAfterIdle)
        if (isRunning) wm?.removeCrossWindowBlurEnabledListener(crossWindowBlurListener)
        try { overlayDialog?.dismiss() } catch (_: Exception) {}
        overlayDialog = null; overlayWindow = null
        miuixPanel?.close()
        miuixPanel = null
        miuixThemeBackground = null
        miuixSurfaceColor = null
        rootView = null; wm = null; windowParams = null
        pendingLyricAnchor = null
        compactLyricOffset = null
        settingsPopup = null
        settingsPopupScrollView = null
        minimalContentView = null; expandedTitleTv = null; expandedArtistTv = null
        expandedContentView = null; btnPlayPause = null
        minimalAlbumArtIv = null; expandedAlbumArtIv = null
        minimalContainer = null; expandedContainer = null
        minimalTextBackgroundContainer = null
        // Keep lastState so a subsequent asynchronous start can render the
        // latest track immediately even if the display manager emits no new
        // state between stop and start.
        currentState = DisplayState.MINIMAL
        lastAppliedSnapshot = null
        boundState = null
        completedWordLine = null
        crossWindowBlurEnabled = false
        isRunning = false
        Log.i(TAG, "Floating lyrics overlay detached")
    }

    private fun remountForTheme() {
        if (!shouldBeRunning || !isRunning) return
        val wasExpanded = currentState == DisplayState.EXPANDED
        detachWindow()
        if (attachWindow()) {
            if (wasExpanded) switchToExpanded() else switchToMinimal()
        } else {
            prefs().unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            shouldBeRunning = false
        }
    }

    fun onDisplayConfigurationChanged() {
        val manager = wm ?: return
        val params = windowParams ?: return
        dismissSettingsPopup()
        params.width = overlayWidth(manager.currentWindowMetrics.bounds.width(), miuixPanel != null)
        fitWindowInDisplay()
        applyWindowAttributes(params)
        requestLineUpdate()
    }

    private fun overlayWidth(screenWidth: Int, useMiuix: Boolean): Int =
        if (useMiuix) minOf((screenWidth * 0.90f).toInt(), dpToPx(420)) else (screenWidth * 0.88f).toInt()

    private fun contentLayoutParams() =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun visibleDisplayBounds(): Rect? {
        val metrics = wm?.currentWindowMetrics ?: return null
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        return Rect(insets.left, insets.top, metrics.bounds.width() - insets.right, metrics.bounds.height() - insets.bottom)
    }

    private fun fitWindowInDisplay(position: Point? = null): Boolean {
        if (!isRunning) return false
        val manager = wm ?: return false
        val params = windowParams ?: return false
        val bounds = visibleDisplayBounds() ?: return false
        val screenWidth = manager.currentWindowMetrics.bounds.width()
        val centeredLeft = (screenWidth - params.width) / 2
        val visibleWidth = if (currentState == DisplayState.EXPANDED) params.width else dpToPx(48)
        val minX = bounds.left - centeredLeft - params.width + visibleWidth
        val maxX = (bounds.right - centeredLeft - visibleWidth).coerceAtLeast(minX)
        val height = rootView?.height ?: 0
        val x = (position?.x ?: params.x).coerceIn(minX, maxX)
        val y = (position?.y ?: params.y).coerceIn(bounds.top, (bounds.bottom - height).coerceAtLeast(bounds.top))
        if (x == params.x && y == params.y) return false
        params.x = x
        params.y = y
        applyWindowAttributes(params)
        return true
    }

    private fun moveWindowToStoredPosition() {
        val params = windowParams ?: return
        val position = positionStore.load(params.width)
        val shift = offsetFromCompactPosition()
        pendingLyricAnchor = null
        fitWindowInDisplay(Point(position.x + shift.x, position.y + shift.y))
        if (position.fromPrefs) positionStore.save(position.x, position.y)
    }

    // ── View builders ─────────────────────────────────────────────────────────

    internal fun createMaterialPreview(expanded: Boolean): View =
        if (expanded) {
            buildExpandedView(interactive = false).apply {
                visibility = View.VISIBLE
                background = buildPanelBackground()
            }
        } else {
            buildMinimalView(interactive = false)
        }

    internal fun materialPreviewTextColor(
        albumColor: Int,
        style: FloatingLyricsStyle,
        expanded: Boolean,
        themeBackground: Int
    ): Int = style.textColor(albumColor, materialLyricBackground(expanded, style, themeBackground))

    internal fun bindMaterialPreview(
        state: UIState,
        style: FloatingLyricsStyle,
        display: FloatingLyricsDisplayConfig,
        textColor: Int
    ) {
        val lyrics = expandedContentView ?: minimalContentView
        lyrics?.render(state, style, display, textColor, state.fullLyric)
        bindMaterialPanel(state, style, state.isPlaying)
    }

    private fun buildMinimalView(interactive: Boolean = true): LinearLayout {
        // Horizontal container for optional album art + text
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(
                dpToPx(chrome.minimalHorizontalPaddingDp),
                dpToPx(chrome.minimalVerticalPaddingDp),
                dpToPx(chrome.minimalHorizontalPaddingDp),
                dpToPx(chrome.minimalVerticalPaddingDp)
            )
        }
        minimalTextBackgroundContainer = contentRow
        
        // Album Art
        minimalAlbumArtIv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(chrome.albumArtSizeDp), dpToPx(chrome.albumArtSizeDp)).apply {
                marginEnd = dpToPx(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(chrome.albumArtRadiusDp).toFloat())
                }
            }
            visibility = View.GONE
        }
        
        minimalContentView = FloatingLyricsContentView(context)

        contentRow.addView(minimalAlbumArtIv)
        contentRow.addView(minimalContentView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(contentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (interactive) {
                contentDescription = context.getString(R.string.floating_settings_expand)
                setOnClickListener { switchToExpanded() }
            }
        }
    }

    private fun buildExpandedView(interactive: Boolean = true): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                dpToPx(chrome.expandedHorizontalPaddingDp),
                dpToPx(chrome.expandedVerticalPaddingDp),
                dpToPx(chrome.expandedHorizontalPaddingDp),
                dpToPx(chrome.expandedVerticalPaddingDp)
            )
            visibility = View.GONE
            if (interactive) setOnClickListener { switchToMinimal() }
        }

        val mediaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        expandedAlbumArtIv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(chrome.expandedAlbumArtSizeDp), dpToPx(chrome.expandedAlbumArtSizeDp)).apply {
                marginEnd = dpToPx(12)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundedOutline(chrome.expandedAlbumArtRadiusDp)
            visibility = View.GONE
        }

        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_GRAVITY
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val artist = TextView(context).apply {
            textSize = 12f
            setTextColor(0xB3FFFFFF.toInt())
            maxLines = 1
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_GRAVITY
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        expandedTitleTv = title
        expandedArtistTv = artist
        titleColumn.addView(title, matchW())
        titleColumn.addView(artist, matchW())
        val settingsButton = makeSettingsButton(interactive)
        mediaRow.addView(expandedAlbumArtIv)
        mediaRow.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mediaRow.addView(settingsButton)
        mediaRow.addView(makeIconButton(R.drawable.ic_music_note, R.string.floating_window_open_player, interactive) {
            if (actionController.openPlayingApp()) switchToMinimal()
        })
        mediaRow.addView(makeIconButton(R.drawable.ic_cancel, R.string.floating_window_close, interactive, actionController::closeFloatingLyrics))
        box.addView(mediaRow, matchW())

        val lyricPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(14), dpToPx(10), dpToPx(12))
            background = roundedRect(0x21FFFFFF, chrome.innerPanelRadiusDp)
        }

        expandedContentView = FloatingLyricsContentView(context)
        lyricPanel.addView(expandedContentView, matchW())

        
        box.addView(lyricPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(12)
        })

        box.addView(buildControlsRow(interactive), centerRow())

        return box
    }

    private fun buildControlsRow(interactive: Boolean): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnPrev = makeIconButton(R.drawable.ic_skip_previous, R.string.action_previous, interactive) { actionController.sendMediaAction("ACTION_MEDIA_PREV") }
        val btnPlay = makeIconButton(R.drawable.ic_play_arrow, R.string.action_play, interactive) {
            actionController.sendMediaAction(if (playing) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY")
        }
        val btnNext = makeIconButton(R.drawable.ic_skip_next, R.string.action_next, interactive) { actionController.sendMediaAction("ACTION_MEDIA_NEXT") }
        btnPlayPause = btnPlay
        row.addView(btnPrev);  row.addView(hSpace(2))
        row.addView(btnPlay);  row.addView(hSpace(2))
        row.addView(btnNext)
        return row
    }

    private fun makeSettingsButton(interactive: Boolean): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(Color.WHITE)
            background = roundedRect(0x1FFFFFFF, 14)
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(38)).apply {
                marginStart = dpToPx(10)
            }
            isClickable = interactive
            isFocusable = interactive
            if (interactive) {
                contentDescription = context.getString(R.string.settings_floating_lyrics)
                setOnClickListener { toggleSettingsPopup(this) }
            } else {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    }

    private fun toggleSettingsPopup(anchor: View) {
        if (settingsPopup?.isShowing == true) {
            dismissSettingsPopup()
            return
        }
        if (SystemClock.uptimeMillis() - settingsPopupDismissedAtMs < POPUP_DISMISS_DEBOUNCE_MS) {
            return
        }
        showSettingsPopup(anchor)
    }

    private fun showSettingsPopup(anchor: View) {
        val bounds = visibleDisplayBounds() ?: return
        val panel = rootView ?: return
        val width = minOf(panel.width, bounds.width())
        if (width <= 0) return
        val height = minOf(dpToPx(480), (bounds.height() - dpToPx(16)).coerceAtLeast(1))
        val composeHost = if (miuixPanel != null) FloatingLyricsComposeHost(context) else null
        val content = composeHost?.view ?: buildSettingsPopupContent(anchor)
        composeHost?.setContent {
            FloatingLyricsMiuixQuickSettings(prefs(), POPUP_TEXT_COLORS, displayConfig, settingsBlur, ::resetWindowPosition)
        }
        val popup = Dialog(context, R.style.Theme_IslandLyrics_Transparent).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            setOnDismissListener {
                if (settingsPopup !== this) return@setOnDismissListener
                settingsComposeHost?.close()
                settingsComposeHost = null
                settingsPopupRefreshQueued = false
                settingsPopupDismissedAtMs = SystemClock.uptimeMillis()
                settingsPopupScrollView = null
                settingsPopup = null
                restartCollapseTimer()
            }
        }
        val window = popup.window ?: run {
            composeHost?.close()
            return
        }
        composeHost?.installOwners(window.decorView)
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        window.setGravity(Gravity.TOP or Gravity.LEFT)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.decorView.clipToOutline = true
        window.decorView.outlineProvider = roundedOutline(chrome.expandedRadiusDp)
        settingsPopup = popup
        mainHandler.removeCallbacks(collapseAfterIdle)
        settingsComposeHost = composeHost
        settingsPopupScrollView = content as? ScrollView
        updateWindowBlur()

        val location = IntArray(2)
        panel.getLocationOnScreen(location)
        val below = location[1] + panel.height + dpToPx(6)
        val above = location[1] - height - dpToPx(6)
        val margin = dpToPx(8)
        val minY = bounds.top + margin
        val maxY = (bounds.bottom - height - margin).coerceAtLeast(minY)
        val desiredY = if (below > maxY && above >= minY) above else below
        window.attributes = window.attributes.apply {
            this.width = width
            this.height = height
            x = location[0].coerceIn(bounds.left, bounds.right - width)
            y = desiredY.coerceIn(minY, maxY)
        }
        try {
            popup.show()
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Unable to attach desktop lyrics settings", e)
            dismissSettingsPopup()
        } catch (e: SecurityException) {
            Log.w(TAG, "Overlay permission unavailable", e)
            dismissSettingsPopup()
        }
    }

    private fun refreshSettingsPopup(anchor: View) {
        val popup = settingsPopup ?: return
        if (!popup.isShowing) return
        val scrollView = settingsPopupScrollView ?: return
        val list = scrollView.getChildAt(0) as? LinearLayout ?: return
        val previousScrollY = scrollView.scrollY

        list.removeAllViews()
        populateSettingsPopupList(list, anchor)
        scrollView.post {
            scrollView.scrollTo(0, previousScrollY.coerceAtMost(list.height))
        }
    }

    private fun scheduleSettingsPopupRefresh(anchor: View) {
        if (settingsPopupRefreshQueued) return
        settingsPopupRefreshQueued = true
        mainHandler.post {
            settingsPopupRefreshQueued = false
            refreshSettingsPopup(anchor)
        }
    }

    private fun dismissSettingsPopup() {
        val popup = settingsPopup ?: return
        settingsPopup = null
        settingsComposeHost?.close()
        settingsComposeHost = null
        settingsPopupScrollView = null
        settingsPopupRefreshQueued = false
        settingsPopupDismissedAtMs = SystemClock.uptimeMillis()
        popup.dismiss()
        restartCollapseTimer()
    }

    private fun buildSettingsPopupContent(anchor: View): View {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }
        populateSettingsPopupList(list, anchor)
        return ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun populateSettingsPopupList(list: LinearLayout, anchor: View) {
        addSettingsPopupRow(list, buildColorPopupRow(anchor))
        addSettingsPopupRow(list, buildBooleanPopupRow(
            title = context.getString(R.string.settings_floating_show_album_art),
            currentValue = { styleStore.style.showAlbumArt },
            onToggle = { value -> prefs().edit { putBoolean(PREF_SHOW_ALBUM_ART, value) } },
            anchor = anchor
        ))
        addSettingsPopupRow(list, buildBooleanPopupRow(
            title = context.getString(R.string.settings_floating_text_background),
            currentValue = { styleStore.style.enableTextBackground },
            onToggle = { value -> prefs().edit { putBoolean(PREF_TEXT_BACKGROUND, value) } },
            anchor = anchor
        ))
        addSettingsPopupRow(list, buildBooleanPopupRow(
            title = context.getString(R.string.settings_floating_show_second_line),
            currentValue = { displayConfig.showSecondLine },
            onToggle = { value -> prefs().edit { putBoolean(PREF_SHOW_NEIGHBOR_LINE, value) } },
            anchor = anchor
        ))
        if (displayConfig.showSecondLine) {
            for (row in listOf(buildSecondaryTextModePopupRow(anchor), buildNeighborAlignmentPopupRow(anchor))) {
                row.setPaddingRelative(
                    row.paddingStart + dpToPx(12), row.paddingTop, row.paddingEnd, row.paddingBottom
                )
                addSettingsPopupRow(list, row)
            }
        }
        addSettingsPopupRow(list, buildBooleanPopupRow(
            title = context.getString(R.string.settings_floating_word_highlight),
            currentValue = { displayConfig.wordHighlight },
            onToggle = { value -> prefs().edit { putBoolean(PREF_WORD_HIGHLIGHT, value) } },
            anchor = anchor
        ))
        addSettingsPopupRow(list, buildBooleanPopupRow(
            title = context.getString(R.string.settings_floating_text_stroke),
            currentValue = { styleStore.style.enableTextStroke },
            onToggle = { value -> prefs().edit { putBoolean(PREF_TEXT_STROKE, value) } },
            anchor = anchor
        ))
        addSettingsPopupRow(list, buildTextSizePopupRow(anchor))
        addSettingsPopupRow(list, buildTextWeightPopupRow(anchor))
        addSettingsPopupRow(list, buildResetPositionPopupRow())
    }

    private fun addSettingsPopupRow(list: LinearLayout, row: View) {
        if (list.isNotEmpty()) {
            list.addView(buildSettingsPopupDivider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildColorPopupRow(anchor: View): View {
        val style = styleStore.style
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))

            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            addView(buildPopupChip(
                text = context.getString(R.string.floating_settings_color_album),
                selected = style.followAlbumColor,
                onClick = {
                    applyPopupSetting(anchor) {
                        prefs().edit { putBoolean(PREF_FOLLOW_ALBUM_COLOR, true) }
                    }
                }
            ))
            POPUP_TEXT_COLORS.forEach { color ->
                val swatch = buildColorSwatch(
                    color = color,
                    selected = !style.followAlbumColor && style.baseTextColor == color,
                    onClick = {
                        applyPopupSetting(anchor) {
                            prefs().edit {
                                putBoolean(PREF_FOLLOW_ALBUM_COLOR, false)
                                putInt(PREF_TEXT_COLOR, color)
                            }
                        }
                    }
                )
                controls.addView(FrameLayout(context).apply {
                    addView(swatch, FrameLayout.LayoutParams(dpToPx(28), dpToPx(28), Gravity.CENTER))
                }, LinearLayout.LayoutParams(0, dpToPx(36), 1f))
            }
            addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(4)
            })
        }
    }

    private fun buildBooleanPopupRow(
        title: String,
        currentValue: () -> Boolean,
        onToggle: (Boolean) -> Unit,
        anchor: View
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(44)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            isClickable = true
            setOnClickListener {
                loadPrefs()
                val nextValue = !currentValue()
                applyPopupSetting(anchor) {
                    onToggle(nextValue)
                }
            }

            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

            addView(buildPopupValuePill(onOffLabel(currentValue()), currentValue()), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28)).apply {
                marginStart = dpToPx(16)
            })
        }
    }

    private fun buildSecondaryTextModePopupRow(anchor: View): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dpToPx(58)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))

            val order = displayConfig.secondaryTextModes.joinToString(" → ", transform = ::secondaryTextModeLabel)
            addView(buildSettingsPopupTitle(context.getString(R.string.settings_floating_secondary_text_mode)), matchW())
            addView(buildSettingsPopupTitle(context.getString(R.string.settings_floating_secondary_text_mode_summary, order)), matchW())

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            SecondaryTextMode.entries.forEach { mode ->
                row.addView(buildPopupChip(
                    text = secondaryTextModeLabel(mode),
                    selected = mode.preferenceValue in displayConfig.secondaryTextModes.map { it.preferenceValue },
                    onClick = {
                        val nextModes = toggleSecondaryTextMode(
                            displayConfig.secondaryTextModes,
                            mode,
                            mode.preferenceValue in displayConfig.secondaryTextModes.map { it.preferenceValue }
                        ) ?: return@buildPopupChip
                        applyPopupSetting(anchor) {
                            SecondaryTextMode.write(prefs(), PREF_SECONDARY_TEXT_MODES, nextModes)
                        }
                    }
                ), LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply {
                    marginEnd = dpToPx(8)
                })
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            })
        }
    }

    private fun buildNeighborAlignmentPopupRow(anchor: View): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dpToPx(58)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))

            addView(buildSettingsPopupTitle(context.getString(R.string.settings_floating_neighbor_alignment)), matchW())

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            FloatingLyricsNeighborAlignment.entries.forEach { alignment ->
                row.addView(buildPopupChip(
                    text = neighborAlignmentLabel(alignment),
                    selected = displayConfig.neighborAlignment == alignment,
                    onClick = {
                        applyPopupSetting(anchor) {
                            prefs().edit { putString(PREF_NEIGHBOR_ALIGNMENT, alignment.value) }
                        }
                    }
                ), LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply {
                    marginEnd = dpToPx(8)
                })
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            })
        }
    }

    private fun buildTextSizePopupRow(anchor: View): View {
        val size = styleStore.style.textSizeSp
        return buildSteppedPopupRow(
            title = context.getString(R.string.settings_floating_text_size),
            value = context.getString(R.string.floating_settings_text_size_value, size.toInt()),
            canDecrease = size > 10f,
            canIncrease = size < 32f,
            onDecrease = { applyPopupSetting(anchor) { styleStore.adjustTextSize(-2f) } },
            onIncrease = { applyPopupSetting(anchor) { styleStore.adjustTextSize(2f) } }
        )
    }

    private fun buildTextWeightPopupRow(anchor: View): View {
        val weight = styleStore.style.textWeight
        return buildSteppedPopupRow(
            title = context.getString(R.string.settings_floating_text_weight),
            value = context.getString(weight.label),
            canDecrease = weight != FloatingLyricsTextWeight.REGULAR,
            canIncrease = weight != FloatingLyricsTextWeight.BOLD,
            onDecrease = { applyPopupSetting(anchor) { styleStore.adjustTextWeight(-1) } },
            onIncrease = { applyPopupSetting(anchor) { styleStore.adjustTextWeight(1) } }
        )
    }

    private fun buildSteppedPopupRow(
        title: String,
        value: String,
        canDecrease: Boolean,
        canIncrease: Boolean,
        onDecrease: () -> Unit,
        onIncrease: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(52)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)

            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

            addView(buildPopupStepButton("−", canDecrease, onDecrease))
            addView(TextView(context).apply {
                text = value
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                minWidth = dpToPx(58)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(buildPopupStepButton("+", canIncrease, onIncrease))
        }
    }

    private fun buildResetPositionPopupRow(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(44)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            isClickable = true
            setOnClickListener { resetWindowPosition() }

            addView(TextView(context).apply {
                text = context.getString(R.string.settings_floating_position_reset)
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun buildSettingsPopupDivider(): View {
        return View(context).apply {
            setBackgroundColor(0x1FFFFFFF)
        }
    }

    private fun resetWindowPosition() {
        resetPosition(context)
        moveWindowToStoredPosition()
        android.widget.Toast
            .makeText(context, R.string.settings_floating_position_reset_toast, android.widget.Toast.LENGTH_SHORT)
            .show()
        dismissSettingsPopup()
    }

    private fun applyPopupSetting(anchor: View, block: () -> Unit) {
        block()
        loadPrefs()
        lastAppliedSnapshot = null
        renderLatestState()
        scheduleSettingsPopupRefresh(anchor)
    }

    private fun buildSettingsPopupTitle(title: String): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun buildPopupChip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(if (selected) Color.WHITE else 0xCCFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
            background = popupControlBackground(selected)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun buildPopupValuePill(text: String, enabled: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(if (enabled) Color.WHITE else 0x99FFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            background = popupControlBackground(enabled)
        }
    }

    private fun buildPopupStepButton(text: String, enabled: Boolean, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            isEnabled = enabled
            setTextColor(if (enabled) Color.WHITE else 0x66FFFFFF)
            textSize = 20f
            gravity = Gravity.CENTER
            background = popupControlBackground(false)
            isClickable = enabled
            setOnClickListener { if (enabled) onClick() }
            layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34))
        }
    }

    private fun buildColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit): View {
        return View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dpToPx(if (selected) 3 else 1), if (selected) Color.WHITE else 0x66FFFFFF)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun popupControlBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(if (selected) 0x33FFFFFF else 0x14FFFFFF)
            setStroke(dpToPx(1), if (selected) Color.WHITE else 0x2EFFFFFF)
        }
    }

    private fun onOffLabel(value: Boolean): String {
        return context.getString(if (value) R.string.floating_settings_on else R.string.floating_settings_off)
    }

    private fun toggleSecondaryTextMode(
        currentModes: List<SecondaryTextMode>,
        mode: SecondaryTextMode,
        checked: Boolean
    ): List<SecondaryTextMode>? {
        val next = currentModes.toMutableList()
        if (checked) {
            if (mode !in next) next.add(mode)
            return next
        }
        if (next.size <= 1) {
            android.widget.Toast.makeText(
                context,
                R.string.settings_home_lyric_preview_keep_one,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return null
        }
        next.remove(mode)
        return next
    }

    private fun secondaryTextModeLabel(mode: SecondaryTextMode): String {
        return when (mode) {
            SecondaryTextMode.NEXT_LYRIC -> context.getString(R.string.super_island_secondary_text_next_lyric)
            SecondaryTextMode.ROMANIZATION -> context.getString(R.string.super_island_secondary_text_romanization)
            SecondaryTextMode.TRANSLATION -> context.getString(R.string.super_island_secondary_text_translation)
        }
    }

    private fun neighborAlignmentLabel(alignment: FloatingLyricsNeighborAlignment): String {
        return when (alignment) {
            FloatingLyricsNeighborAlignment.CENTER -> context.getString(R.string.settings_floating_alignment_center)
            FloatingLyricsNeighborAlignment.SPLIT_START_END -> context.getString(R.string.settings_floating_alignment_split)
        }
    }

    // ── State switching ───────────────────────────────────────────────────────

    private fun currentLyricOffset(): Point? {
        val root = rootView ?: return null
        val lyrics = if (currentState == DisplayState.MINIMAL) minimalContentView else expandedContentView
        return lyrics?.lyricAnchorIn(root)
    }

    private fun offsetFromCompactPosition(): Point {
        val compact = compactLyricOffset ?: return Point()
        if (currentState == DisplayState.MINIMAL) return Point()
        val current = currentLyricOffset() ?: return Point()
        return Point(compact.x - current.x, compact.y - current.y)
    }

    private fun rememberLyricAnchor() {
        if (pendingLyricAnchor != null) return
        val params = windowParams ?: return
        val offset = currentLyricOffset() ?: return
        if (currentState == DisplayState.MINIMAL) compactLyricOffset = offset
        pendingLyricAnchor = Point(params.x + offset.x, params.y + offset.y)
    }

    private fun switchToMinimal() {
        if (currentState == DisplayState.EXPANDED) rememberLyricAnchor()
        dismissSettingsPopup()
        clearWindowBlur()
        currentState = DisplayState.MINIMAL
        mainHandler.removeCallbacks(collapseAfterIdle)
        minimalContainer?.visibility  = View.VISIBLE
        expandedContainer?.visibility = View.GONE
        miuixPanel?.expanded = false
        updateWindowBlur()
        renderLatestState()
    }

    private fun switchToExpanded() {
        if (currentState == DisplayState.MINIMAL) rememberLyricAnchor()
        clearWindowBlur()
        currentState = DisplayState.EXPANDED
        minimalContainer?.visibility  = View.GONE
        expandedContainer?.visibility = View.VISIBLE
        miuixPanel?.expanded = true
        if (pendingLyricAnchor == null) fitWindowInDisplay()
        updateWindowBlur()
        renderLatestState()
        restartCollapseTimer()
    }

    private fun restartCollapseTimer() {
        mainHandler.removeCallbacks(collapseAfterIdle)
        if (isRunning && shouldBeRunning && currentState == DisplayState.EXPANDED && settingsPopup == null) {
            mainHandler.postDelayed(collapseAfterIdle, EXPANDED_TIMEOUT_MS)
        }
    }

    private fun renderLatestState() {
        val repo = LyricRepository.getInstance()
        val metadata = repo.liveMetadata.value
        applyState(
            lastState ?: UIState(
                title = metadata?.title ?: track?.title.orEmpty(),
                artist = metadata?.artist ?: track?.artist.orEmpty(),
                albumArt = repo.liveAlbumArt.value,
                mediaPackage = metadata?.packageName ?: track?.player.orEmpty()
            )
        )
    }

    // ── Data binding ──────────────────────────────────────────────────────────

    private fun applyState(baseState: UIState) {
        val repo = LyricRepository.getInstance()
        val now = SystemClock.elapsedRealtime()
        val position = clock?.positionAt(now) ?: repo.liveProgress.value?.position
        val presentation = timeline?.presentationAt(position) ?: floatingLiveLine(parsedLyrics, repo.liveLyric.value)
        val state = baseState.copy(lyricPresentation = presentation)
        mainHandler.removeCallbacks(changeLine)
        if (playing) {
            presentation.nextLine?.let { next ->
                clock?.delayUntil(next.startTime, now)?.let { mainHandler.postDelayed(changeLine, it) }
            }
        }
        boundState = state
        if (presentation.currentLine != completedWordLine) completedWordLine = null
        val lyric = presentation.currentLine?.text ?: "♪"
        val style = styleStore.style
        val themeBackground = miuixThemeBackground ?: defaultThemeBackground()
        val surface = miuixSurfaceColor ?: themeBackground
        val compactBackground = if (miuixPanel == null) {
            materialLyricBackground(false, style, themeBackground)
        } else if (style.enableTextBackground) {
            surface
        } else {
            themeBackground
        }
        val expandedBackground = if (miuixPanel == null) materialLyricBackground(true, style, themeBackground) else surface
        val snapshot = ViewSnapshot(
            lyric = lyric,
            title = state.title,
            artist = state.artist,
            isPlaying = playing,
            style = style,
            albumColor = state.albumColor,
            compactBackground = compactBackground,
            expandedBackground = expandedBackground,
            albumArtHash = state.albumArt?.hashCode() ?: 0,
            displayConfig = displayConfig,
            presentation = presentation
        )
        if (snapshot == lastAppliedSnapshot) {
            syncWordAnimation()
            return
        }
        lastAppliedSnapshot = snapshot

        val wordProgress = resolveWordProgress(state)
        val frame = state.copy(lyricPresentation = presentation.copy(wordProgress = wordProgress))
        minimalContentView?.render(frame, style, displayConfig, style.textColor(state.albumColor, compactBackground), lyric)
        expandedContentView?.render(frame, style, displayConfig, style.textColor(state.albumColor, expandedBackground), lyric)
        miuixPanel?.bind(state, style, playing)
        bindMaterialPanel(state, style, playing)
        syncWordAnimation()
    }

    private fun bindMaterialPanel(state: UIState, style: FloatingLyricsStyle, playing: Boolean) {
        expandedTitleTv?.text = state.title.ifBlank { context.getString(R.string.media_control_unknown_title) }
        expandedArtistTv?.text = state.artist.ifBlank { context.getString(R.string.media_control_unknown_artist) }
        
        minimalAlbumArtIv?.apply {
            val art = state.albumArt.takeIf { style.showAlbumArt }
            setImageBitmap(art)
            visibility = if (art == null) View.GONE else View.VISIBLE
        }
        expandedAlbumArtIv?.apply {
            setImageBitmap(state.albumArt)
            visibility = if (state.albumArt == null) View.GONE else View.VISIBLE
        }
        
        // Background for minimal state
        if (style.enableTextBackground) {
            minimalTextBackgroundContainer?.background = buildMinimalTextBackground()
        } else {
            minimalTextBackgroundContainer?.background = null
            minimalTextBackgroundContainer?.setPadding(
                dpToPx(chrome.minimalHorizontalPaddingDp),
                dpToPx(chrome.minimalVerticalPaddingDp),
                dpToPx(chrome.minimalHorizontalPaddingDp),
                dpToPx(chrome.minimalVerticalPaddingDp)
            )
        }

        btnPlayPause?.apply {
            setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            if (isClickable) {
                contentDescription = context.getString(if (playing) R.string.action_pause else R.string.action_play)
            }
        }
    }

    // ── 逐字平滑动画（高频队列） ──────────────────────────────────────────────

    private fun syncWordAnimation() {
        if (!shouldRunWordAnimation()) {
            stopWordAnimation()
            renderWordFrame()
            return
        }
        if (wordAnimationActive) return
        val progress = renderWordFrame() ?: return
        if (!progress.isFullySung()) {
            wordAnimationActive = true
            wordAnimationHandler.postDelayed(wordAnimationRunnable, wordAnimationIntervalMs())
        }
    }

    private fun stopWordAnimation() {
        wordAnimationActive = false
        wordAnimationHandler.removeCallbacks(wordAnimationRunnable)
    }

    private fun shouldRunWordAnimation(): Boolean = isRunning && shouldBeRunning && playing &&
        (clock?.speed ?: 1f) > 0f &&
        displayConfig.wordHighlight && boundState?.lyricPresentation?.currentLine?.hasSyllables == true

    private fun wordAnimationIntervalMs(): Long =
        if (LabFeatureManager.isFloatingWordHighFpsEnabled(prefs())) 16L else 33L

    private fun renderWordFrame(): LyricPresentation.WordProgress? {
        val state = boundState ?: return null
        val progress = resolveWordProgress(state)
        minimalContentView?.renderWordProgress(progress)
        expandedContentView?.renderWordProgress(progress)
        return progress
    }

    private fun resolveWordProgress(state: UIState): LyricPresentation.WordProgress? {
        if (!displayConfig.wordHighlight) return null
        val line = state.lyricPresentation.currentLine ?: return null
        val syllables = line.syllables?.takeIf { it.isNotEmpty() } ?: return null
        val now = SystemClock.elapsedRealtime()
        clock?.let { return line.wordProgressAt(it.positionAt(now)) }
        val position = wordPlayback.positionAt(now) ?: return null
        val end = syllables.maxOf { it.endTime }
        if (position >= end) completedWordLine = line
        return line.wordProgressAt(if (completedWordLine == line) end else position)
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private fun makeIconButton(drawableRes: Int, labelRes: Int, interactive: Boolean, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(drawableRes)
            setColorFilter(Color.WHITE)
            background = roundedRect(0x1FFFFFFF, 14)
            val p = dpToPx(chrome.iconButtonPaddingDp)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dpToPx(chrome.iconButtonSizeDp), dpToPx(chrome.iconButtonSizeDp))
            isClickable = interactive
            isFocusable = interactive
            if (interactive) {
                contentDescription = context.getString(labelRes)
                setOnClickListener { onClick() }
            } else {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    }

    private fun hSpace(dp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(dp), 1)
    }

    private fun matchW() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun centerRow() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
        it.gravity = Gravity.CENTER_HORIZONTAL
        it.topMargin = dpToPx(2)
    }

    private fun applyWindowAttributes(params: WindowManager.LayoutParams) {
        try {
            overlayWindow?.attributes = params
        } catch (e: Exception) {
            Log.w(TAG, "applyWindowAttributes: ${e.message}")
        }
    }

    private fun clearWindowBlur() {
        val window = overlayWindow ?: return
        try {
            window.setBackgroundBlurRadius(0)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.clipToOutline = false
        } catch (e: Exception) {
            Log.w(TAG, "clearWindowBlur: ${e.message}")
        }
    }

    private fun updateWindowBlur() {
        val window = overlayWindow ?: return
        val supportsBlur = crossWindowBlurEnabled &&
            (miuixPanel == null || prefs().getBoolean(AppPreferences.Keys.CARD_BLUR_ENABLED, false))
        val shouldBlur = currentState == DisplayState.EXPANDED && supportsBlur
        miuixPanel?.blur = shouldBlur
        settingsBlur = supportsBlur
        val nextRadius = if (shouldBlur) dpToPx(chrome.expandedBlurRadiusDp) else 0
        try {
            window.setBackgroundBlurRadius(nextRadius)
            window.setBackgroundDrawable(
                if (currentState == DisplayState.EXPANDED) {
                    buildPanelBackground()
                } else {
                    ColorDrawable(Color.TRANSPARENT)
                }
            )
            window.decorView.clipToOutline = shouldBlur
            if (shouldBlur) {
                window.decorView.outlineProvider = roundedOutline(chrome.expandedRadiusDp)
            }
            settingsPopup?.window?.apply {
                setBackgroundDrawable(buildPanelBackground())
                setBackgroundBlurRadius(if (supportsBlur) dpToPx(chrome.expandedBlurRadiusDp) else 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateWindowBlur: ${e.message}")
        }
    }

    private fun buildPanelBackground(): GradientDrawable {
        val color = if (miuixPanel != null) Color.TRANSPARENT else materialPanelColor()
        return roundedRect(color, chrome.expandedRadiusDp)
    }

    private fun materialPanelColor(): Int =
        if (crossWindowBlurEnabled) chrome.expandedBackgroundColor else chrome.expandedFallbackBackgroundColor

    private fun materialLyricBackground(expanded: Boolean, style: FloatingLyricsStyle, themeBackground: Int): Int {
        val panelColor = when {
            expanded -> materialPanelColor()
            style.enableTextBackground -> chrome.minimalBackgroundColor
            else -> Color.TRANSPARENT
        }
        return ColorUtils.compositeColors(panelColor, themeBackground)
    }

    private fun defaultThemeBackground(): Int {
        val dark = if (ThemeHelper.getFollowSystem(context)) {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            ThemeHelper.getDarkMode(context)
        }
        return if (dark) Color.BLACK else Color.WHITE
    }

    private fun onMiuixBackgroundColorsChanged(background: Int, surface: Int) {
        if (miuixThemeBackground == background && miuixSurfaceColor == surface) return
        miuixThemeBackground = background
        miuixSurfaceColor = surface
        requestLineUpdate()
    }

    private fun roundedRect(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(radiusDp).toFloat()
        setColor(color)
    }

    private fun roundedOutline(radiusDp: Int): android.view.ViewOutlineProvider {
        return object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpToPx(radiusDp).toFloat())
            }
        }
    }
    
    private fun buildMinimalTextBackground(): GradientDrawable = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(chrome.minimalBackgroundRadiusDp).toFloat()
        setColor(chrome.minimalBackgroundColor)
    }

private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
