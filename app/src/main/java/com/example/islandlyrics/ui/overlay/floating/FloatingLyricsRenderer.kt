package com.example.islandlyrics.ui.overlay.floating
import com.example.islandlyrics.ui.overlay.model.UIState
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isNotEmpty
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.lyrics.state.LyricRepository

/**
 * FloatingLyricsRenderer
 *
 * Two-state System Alert Window overlay.
 *
 * MINIMAL  — transparent background (or dark subtle background if enabled), centered lyrics with optional album art on left.
 * EXPANDED — dark rounded pill, title/artist header, lyrics with optional album art on left, one control row:
 *   Row 1: media, prev, play/pause, next, close
 *
 * Font size, color, stroke, background style, and album art visibility are driven by SharedPreferences.
 * Clicking empty space in EXPANDED state -> switches back to MINIMAL.
 * Dragging uses [FloatingLyricsDraggableFrameLayout] (threshold-based, non-conflicting with clicks).
 */
class FloatingLyricsRenderer(private val context: Context) {

    companion object {
        private const val TAG = "FloatingLyricsRenderer"
        const val PREF_KEY             = AppPreferences.Keys.FLOATING_LYRICS_ENABLED
        const val PREF_TEXT_SIZE       = FloatingLyricsStyleStore.KEY_TEXT_SIZE
        const val PREF_TEXT_COLOR      = FloatingLyricsStyleStore.KEY_TEXT_COLOR
        const val PREF_FOLLOW_ALBUM_COLOR = FloatingLyricsStyleStore.KEY_FOLLOW_ALBUM_COLOR
        const val PREF_SHOW_ALBUM_ART  = FloatingLyricsStyleStore.KEY_SHOW_ALBUM_ART
        const val PREF_TEXT_STROKE     = FloatingLyricsStyleStore.KEY_TEXT_STROKE
        const val PREF_TEXT_BACKGROUND = FloatingLyricsStyleStore.KEY_TEXT_BACKGROUND
        const val PREF_DISPLAY_MODE = FloatingLyricsDisplayConfig.KEY_DISPLAY_MODE
        const val PREF_SHOW_NEIGHBOR_LINE = FloatingLyricsDisplayConfig.KEY_SHOW_NEIGHBOR_LINE
        const val PREF_NEIGHBOR_ALIGNMENT = FloatingLyricsDisplayConfig.KEY_NEIGHBOR_ALIGNMENT
        const val PREF_WORD_HIGHLIGHT = FloatingLyricsDisplayConfig.KEY_WORD_HIGHLIGHT
        const val PREF_POS_X           = FloatingLyricsWindowPositionStore.KEY_POS_X
        const val PREF_POS_Y           = FloatingLyricsWindowPositionStore.KEY_POS_Y

        private const val EXPANDED_TIMEOUT_MS = 4000L
        private const val POPUP_DISMISS_DEBOUNCE_MS = 250L
        private val POPUP_TEXT_COLORS = intArrayOf(
            Color.WHITE,
            0xFFFFFFCC.toInt(),
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
    private var wm: WindowManager? = null
    private var rootView: FloatingLyricsDraggableFrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var settingsPopup: PopupWindow? = null
    private var settingsPopupDismissedAtMs: Long = 0L

    private val styleStore = FloatingLyricsStyleStore(context)
    private val positionStore = FloatingLyricsWindowPositionStore(context)
    private val actionController = FloatingLyricsActionController(
        context = context,
        onAfterAction = { resetCollapseTimer() },
        onDisabled = { stop() }
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
    private var lastAppliedSnapshot: ViewSnapshot? = null
    private var displayConfig = FloatingLyricsDisplayConfig.from(prefs())
    private var chrome = FloatingLyricsChrome.from(prefs())

    var isRunning = false
        private set

    private val collapseRunnable = Runnable { switchToMinimal() }

    private data class ViewSnapshot(
        val lyric: String,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val textSizeSp: Float,
        val textColor: Int,
        val showAlbumArt: Boolean,
        val albumArtHash: Int,
        val enableTextStroke: Boolean,
        val enableTextBackground: Boolean,
        val displayConfig: FloatingLyricsDisplayConfig,
        val presentationHash: Int
    )

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_TEXT_SIZE, PREF_TEXT_COLOR, PREF_FOLLOW_ALBUM_COLOR, 
            PREF_SHOW_ALBUM_ART, PREF_TEXT_STROKE, PREF_TEXT_BACKGROUND,
            PREF_DISPLAY_MODE, PREF_SHOW_NEIGHBOR_LINE, PREF_NEIGHBOR_ALIGNMENT,
            PREF_WORD_HIGHLIGHT -> {
                mainHandler.post {
                    loadPrefs()
                    lastState?.let { applyState(it) }
                }
            }
            PREF_POS_X, PREF_POS_Y -> {
                mainHandler.post { moveWindowToStoredPosition() }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted"); return
        }
        mainHandler.post {
            prefs().registerOnSharedPreferenceChangeListener(prefChangeListener)
            loadPrefs()
            attachWindow()
            switchToExpanded()
        }
    }

    fun stop() {
        if (!isRunning) return
        mainHandler.post {
            prefs().unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            detachWindow() 
        }
    }

    fun render(state: UIState) {
        if (!isRunning) return
        lastState = state
        mainHandler.post { applyState(state) }
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

    private fun attachWindow() {
        if (isRunning) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager

        val screenW = context.resources.displayMetrics.widthPixels
        val overlayW = (screenW * 0.88f).toInt()

        val params = WindowManager.LayoutParams(
            overlayW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
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
                windowManager.updateViewLayout(rootView!!, params)
            },
            onDragEnd = {
                val clampedPosition = positionStore.clamp(params.x, params.y, params.width)
                if (params.x != clampedPosition.x || params.y != clampedPosition.y) {
                    params.x = clampedPosition.x
                    params.y = clampedPosition.y
                    windowManager.updateViewLayout(rootView!!, params)
                }
                positionStore.save(params.x, params.y)
            }
        )
        rootView = root
        val minimal  = buildMinimalView();  minimalContainer  = minimal
        val expanded = buildExpandedView(); expandedContainer = expanded

        root.addView(minimal,  ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(expanded, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        windowManager.addView(root, params)
        isRunning = true
        Log.i(TAG, "Floating lyrics overlay attached")
    }

    private fun detachWindow() {
        mainHandler.removeCallbacks(collapseRunnable)
        settingsPopup?.dismiss()
        try { rootView?.let { wm?.removeView(it) } } catch (_: Exception) {}
        rootView = null; wm = null; windowParams = null
        settingsPopup = null
        minimalContentView = null; expandedTitleTv = null; expandedArtistTv = null
        expandedContentView = null; btnPlayPause = null
        minimalAlbumArtIv = null; expandedAlbumArtIv = null
        minimalContainer = null; expandedContainer = null
        minimalTextBackgroundContainer = null
        lastState = null; currentState = DisplayState.MINIMAL
        lastAppliedSnapshot = null
        isRunning = false
        Log.i(TAG, "Floating lyrics overlay detached")
    }

    private fun moveWindowToStoredPosition() {
        val params = windowParams ?: return
        val root = rootView ?: return
        val windowManager = wm ?: return
        val position = positionStore.load(params.width)
        params.x = position.x
        params.y = position.y
        try {
            windowManager.updateViewLayout(root, params)
            if (position.fromPrefs) positionStore.save(params.x, params.y)
        } catch (e: Exception) {
            Log.w(TAG, "moveWindowToStoredPosition: ${e.message}")
        }
    }

    // ── View builders ─────────────────────────────────────────────────────────

    private fun buildMinimalView(): LinearLayout {
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
            setOnClickListener { switchToExpanded() }
        }
    }

    private fun buildExpandedView(): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                dpToPx(chrome.expandedHorizontalPaddingDp),
                dpToPx(chrome.expandedVerticalPaddingDp),
                dpToPx(chrome.expandedHorizontalPaddingDp),
                dpToPx(chrome.expandedVerticalPaddingDp)
            )
            background = buildPillBackground()
            visibility = View.GONE
            
            // Click empty area to collapse
            setOnClickListener { switchToMinimal() }
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
        val settingsButton = makeSettingsButton()
        mediaRow.addView(expandedAlbumArtIv)
        mediaRow.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mediaRow.addView(settingsButton)
        box.addView(mediaRow, matchW())

        val lyricPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(10), dpToPx(14), dpToPx(10), dpToPx(12))
            background = roundedRect(0x21FFFFFF, chrome.innerPanelRadiusDp)
        }

        expandedContentView = FloatingLyricsContentView(context)
        lyricPanel.addView(expandedContentView, matchW())

        mediaRow.setOnClickListener { switchToMinimal() }
        lyricPanel.setOnClickListener { switchToMinimal() }
        title.setOnClickListener { switchToMinimal() }
        artist.setOnClickListener { switchToMinimal() }
        
        box.addView(lyricPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(12)
        })

        box.addView(buildControlsRow(), centerRow())

        return box
    }

    private fun buildControlsRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setOnClickListener { resetCollapseTimer() }
        }
        val btnMedia = makeIconButton(R.drawable.ic_music_note) { actionController.openMediaControl() }
        val btnPrev  = makeIconButton(R.drawable.ic_skip_previous) { actionController.sendMediaAction("ACTION_MEDIA_PREV") }
        val btnPlay  = makeIconButton(R.drawable.ic_play_arrow) {
            actionController.sendMediaAction(if (lastState?.isPlaying == true) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY")
        }
        val btnNext  = makeIconButton(R.drawable.ic_skip_next) { actionController.sendMediaAction("ACTION_MEDIA_NEXT") }
        val btnClose = makeIconButton(R.drawable.ic_cancel) { actionController.disableFloatingLyrics() }
        btnPlayPause = btnPlay
        row.addView(btnMedia); row.addView(hSpace(2))
        row.addView(btnPrev);  row.addView(hSpace(2))
        row.addView(btnPlay);  row.addView(hSpace(2))
        row.addView(btnNext);  row.addView(hSpace(2))
        row.addView(btnClose)
        return row
    }

    private fun makeSettingsButton(): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(Color.WHITE)
            background = roundedRect(0x1FFFFFFF, 14)
            contentDescription = context.getString(R.string.settings_floating_lyrics)
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(38)).apply {
                marginStart = dpToPx(10)
            }
            setOnClickListener {
                resetCollapseTimer()
                toggleSettingsPopup(this)
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
        val width = settingsPopupWidth()
        val popup = PopupWindow(buildSettingsPopupContent(anchor), width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = dpToPx(10).toFloat()
            setOnDismissListener {
                settingsPopupDismissedAtMs = SystemClock.uptimeMillis()
                if (settingsPopup === this) settingsPopup = null
            }
        }
        settingsPopup = popup
        popup.showAsDropDown(anchor, 0, dpToPx(6), Gravity.END)
    }

    private fun refreshSettingsPopup(anchor: View) {
        val popup = settingsPopup ?: return
        if (!popup.isShowing) return
        val width = settingsPopupWidth()
        val previousScrollY = (popup.contentView as? ScrollView)?.scrollY ?: 0
        popup.contentView = buildSettingsPopupContent(anchor).apply {
            post { (this as? ScrollView)?.scrollTo(0, previousScrollY) }
        }
        popup.width = width
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.update(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun dismissSettingsPopup() {
        settingsPopup?.dismiss()
        settingsPopup = null
        settingsPopupDismissedAtMs = SystemClock.uptimeMillis()
    }

    private fun buildSettingsPopupContent(anchor: View): View {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(6), 0, dpToPx(6))
            background = roundedRect(0xF21D1D1F.toInt(), 16)
        }
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
        addSettingsPopupRow(list, buildDisplayModePopupRow(anchor))
        addSettingsPopupRow(list, buildBooleanPopupRow(
            title = context.getString(R.string.settings_floating_show_neighbor_line),
            currentValue = { displayConfig.showNeighborLine },
            onToggle = { value -> prefs().edit { putBoolean(PREF_SHOW_NEIGHBOR_LINE, value) } },
            anchor = anchor
        ))
        addSettingsPopupRow(list, buildNeighborAlignmentPopupRow(anchor))
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
        addSettingsPopupRow(list, buildResetPositionPopupRow())
        return ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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
            minimumHeight = dpToPx(70)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))

            addView(buildSettingsPopupTitle(context.getString(R.string.settings_floating_text_color)), matchW())

            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            controls.addView(buildPopupChip(
                text = context.getString(R.string.floating_settings_color_album),
                selected = style.followAlbumColor,
                onClick = {
                    applyPopupSetting(anchor) {
                        prefs().edit { putBoolean(PREF_FOLLOW_ALBUM_COLOR, true) }
                    }
                }
            ))
            controls.addView(hSpace(8))
            POPUP_TEXT_COLORS.forEach { color ->
                controls.addView(buildColorSwatch(
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
                ), LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                    marginEnd = dpToPx(6)
                })
            }
            addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
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

    private fun buildDisplayModePopupRow(anchor: View): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dpToPx(58)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))

            addView(buildSettingsPopupTitle(context.getString(R.string.settings_floating_display_mode)), matchW())

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            FloatingLyricsDisplayMode.optionOrder.forEach { mode ->
                row.addView(buildPopupChip(
                    text = displayModeLabel(mode),
                    selected = mode in displayConfig.displayModes,
                    onClick = {
                        val nextModes = FloatingLyricsDisplayMode.toggledModes(
                            displayConfig.displayModes,
                            mode,
                            mode !in displayConfig.displayModes
                        ) ?: return@buildPopupChip
                        applyPopupSetting(anchor) {
                            prefs().edit {
                                putString(PREF_DISPLAY_MODE, FloatingLyricsDisplayMode.preferenceValue(nextModes))
                                putBoolean(PREF_SHOW_NEIGHBOR_LINE, displayConfig.showNeighborLine)
                            }
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
        val style = styleStore.style
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(52)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)

            addView(TextView(context).apply {
                text = context.getString(R.string.settings_floating_text_size)
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

            addView(buildPopupStepButton("-", enabled = style.textSizeSp > 10f) {
                applyPopupSetting(anchor) { styleStore.adjustTextSize(-2f) }
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.floating_settings_text_size_value, style.textSizeSp.toInt())
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dpToPx(58), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(buildPopupStepButton("+", enabled = style.textSizeSp < 32f) {
                applyPopupSetting(anchor) { styleStore.adjustTextSize(2f) }
            })
        }
    }

    private fun buildResetPositionPopupRow(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(44)
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            isClickable = true
            setOnClickListener {
                resetPosition(context)
                moveWindowToStoredPosition()
                android.widget.Toast
                    .makeText(context, R.string.settings_floating_position_reset_toast, android.widget.Toast.LENGTH_SHORT)
                    .show()
                resetCollapseTimer()
                dismissSettingsPopup()
            }

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

    private fun settingsPopupWidth(): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        return minOf(dpToPx(320), screenWidth - dpToPx(48))
    }

    private fun applyPopupSetting(anchor: View, block: () -> Unit) {
        block()
        loadPrefs()
        lastAppliedSnapshot = null
        lastState?.let { applyState(it) }
        resetCollapseTimer()
        refreshSettingsPopup(anchor)
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

    private fun displayModeLabel(mode: FloatingLyricsDisplayMode): String {
        return when (mode) {
            FloatingLyricsDisplayMode.LYRIC -> context.getString(R.string.settings_floating_mode_single)
            FloatingLyricsDisplayMode.ROMANIZATION -> context.getString(R.string.settings_floating_mode_romanization)
            FloatingLyricsDisplayMode.TRANSLATION -> context.getString(R.string.settings_floating_mode_translation)
        }
    }

    private fun neighborAlignmentLabel(alignment: FloatingLyricsNeighborAlignment): String {
        return when (alignment) {
            FloatingLyricsNeighborAlignment.CENTER -> context.getString(R.string.settings_floating_alignment_center)
            FloatingLyricsNeighborAlignment.SPLIT_START_END -> context.getString(R.string.settings_floating_alignment_split)
        }
    }

    // ── State switching ───────────────────────────────────────────────────────

    private fun switchToMinimal() {
        dismissSettingsPopup()
        currentState = DisplayState.MINIMAL
        minimalContainer?.visibility  = View.VISIBLE
        expandedContainer?.visibility = View.GONE
        mainHandler.removeCallbacks(collapseRunnable)
    }

    private fun switchToExpanded() {
        currentState = DisplayState.EXPANDED
        minimalContainer?.visibility  = View.GONE
        expandedContainer?.visibility = View.VISIBLE
        lastState?.let { applyState(it) }
        mainHandler.removeCallbacks(collapseRunnable)
        mainHandler.postDelayed(collapseRunnable, EXPANDED_TIMEOUT_MS)
    }

    // ── Data binding ──────────────────────────────────────────────────────────

    private fun applyState(state: UIState) {
        val lyric = resolveFloatingFallbackLyric(state)
        val style = styleStore.style
        val actualColor = style.textColor(state.albumColor)
        val snapshot = ViewSnapshot(
            lyric = lyric,
            title = state.title,
            artist = state.artist,
            isPlaying = state.isPlaying,
            textSizeSp = style.textSizeSp,
            textColor = actualColor,
            showAlbumArt = style.showAlbumArt,
            albumArtHash = state.albumArt?.hashCode() ?: 0,
            enableTextStroke = style.enableTextStroke,
            enableTextBackground = style.enableTextBackground,
            displayConfig = displayConfig,
            presentationHash = state.lyricPresentation.hashCode()
        )
        if (snapshot == lastAppliedSnapshot) {
            return
        }
        lastAppliedSnapshot = snapshot

        minimalContentView?.render(state, style, displayConfig, actualColor, lyric)
        expandedContentView?.render(state, style, displayConfig, actualColor, lyric)

        expandedTitleTv?.text = state.title.ifBlank { context.getString(R.string.media_control_unknown_title) }
        expandedArtistTv?.text = state.artist.ifBlank { context.getString(R.string.media_control_unknown_artist) }
        
        // Album art check
        val hasArt = state.albumArt != null && style.showAlbumArt
        if (hasArt) {
            minimalAlbumArtIv?.setImageBitmap(state.albumArt)
            expandedAlbumArtIv?.setImageBitmap(state.albumArt)
            minimalAlbumArtIv?.visibility = View.VISIBLE
            expandedAlbumArtIv?.visibility = View.VISIBLE
        } else {
            minimalAlbumArtIv?.visibility = View.GONE
            expandedAlbumArtIv?.visibility = View.GONE
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

        val isPlaying = state.isPlaying
        btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        btnPlayPause?.setOnClickListener {
            actionController.sendMediaAction(if (isPlaying) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY")
        }
    }

    private fun resolveFloatingFallbackLyric(state: UIState): String {
        if (state.timelineCapability == LyricRepository.TimelineCapability.MULTI_LINE &&
            state.lyricPresentation.currentLine == null &&
            state.isTimingGapPlaceholder &&
            state.preferMetadataLayout
        ) {
            return "●●●"
        }

        return state.fullLyric.ifBlank { state.displayLyric.ifBlank { "♪" } }
    }

    private fun resetCollapseTimer() {
        mainHandler.removeCallbacks(collapseRunnable)
        mainHandler.postDelayed(collapseRunnable, EXPANDED_TIMEOUT_MS)
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private fun makeIconButton(drawableRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(drawableRes)
            setColorFilter(Color.WHITE)
            background = roundedRect(0x1FFFFFFF, 14)
            val p = dpToPx(chrome.iconButtonPaddingDp)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dpToPx(chrome.iconButtonSizeDp), dpToPx(chrome.iconButtonSizeDp))
            setOnClickListener { onClick() }
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

    private fun buildPillBackground(): GradientDrawable = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(chrome.expandedRadiusDp).toFloat()
        setColor(chrome.expandedBackgroundColor)
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
