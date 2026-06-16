package com.example.islandlyrics.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.example.islandlyrics.R
import com.example.islandlyrics.feature.mediacontrol.MediaControlActivity
import com.example.islandlyrics.service.LyricService
import com.example.islandlyrics.ui.common.views.OutlineTextView
import kotlin.math.abs

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
 * Dragging uses [DraggableFrameLayout] (threshold-based, non-conflicting with clicks).
 */
class FloatingLyricsRenderer(private val context: Context) {

    companion object {
        private const val TAG = "FloatingLyricsRenderer"
        const val PREF_KEY             = "floating_lyrics_enabled"
        const val PREF_TEXT_SIZE       = "floating_text_size_sp"
        const val PREF_TEXT_COLOR      = "floating_text_color"
        const val PREF_FOLLOW_ALBUM_COLOR = "floating_follow_album_color"
        const val PREF_SHOW_ALBUM_ART  = "floating_show_album_art"
        const val PREF_TEXT_STROKE     = "floating_text_stroke"
        const val PREF_TEXT_BACKGROUND = "floating_text_background"
        const val PREF_POS_X           = "floating_pos_x"
        const val PREF_POS_Y           = "floating_pos_y"

        private const val EXPANDED_TIMEOUT_MS = 4000L
        private const val DEFAULT_Y_DP = 140
        private const val MIN_VISIBLE_DP = 48
        private const val SIZE_DEFAULT = 15f

        fun resetPosition(context: Context) {
            context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
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
    private var rootView: DraggableFrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    // Style settings (loaded from prefs)
    private var textSizeSp: Float = SIZE_DEFAULT
    private var baseTextColor: Int = Color.WHITE
    private var followAlbumColor: Boolean = true
    private var showAlbumArt: Boolean = true
    private var enableTextStroke: Boolean = true
    private var enableTextBackground: Boolean = false

    // View refs
    private var minimalLyricTv: OutlineTextView? = null
    private var expandedTitleTv: TextView? = null
    private var expandedLyricTv: OutlineTextView? = null
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
        val enableTextBackground: Boolean
    )

    private data class WindowPosition(
        val x: Int,
        val y: Int,
        val fromPrefs: Boolean
    )

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PREF_TEXT_SIZE, PREF_TEXT_COLOR, PREF_FOLLOW_ALBUM_COLOR, 
            PREF_SHOW_ALBUM_ART, PREF_TEXT_STROKE, PREF_TEXT_BACKGROUND -> {
                mainHandler.post {
                    loadStylePrefs()
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
            loadStylePrefs()
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
        context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)

    // ── Quick Settings logic ──────────────────────────────────────────────────

    private fun adjustTextSize(delta: Float) {
        var size = prefs().getFloat(PREF_TEXT_SIZE, 15f)
        size += delta
        size = size.coerceIn(10f, 32f)
        textSizeSp = size
        prefs().edit { putFloat(PREF_TEXT_SIZE, size) }
        lastState?.let { applyState(it) }
        resetCollapseTimer()
    }

    private val presetColors = intArrayOf(
        Color.WHITE, 0xFFFFFFCC.toInt(), 0xFFCCFFFF.toInt(), 0xFFCCFFCC.toInt(), 0xFFFFCC99.toInt(), 0xFFFF99CC.toInt()
    )

    private fun cycleColor() {
        val current = prefs().getInt(PREF_TEXT_COLOR, Color.WHITE)
        val idx = presetColors.indexOf(current)
        val nextIdx = if (idx == -1) 0 else (idx + 1) % presetColors.size
        val newColor = presetColors[nextIdx]
        baseTextColor = newColor
        
        // Turn off follow album color when manually changing color from UI
        if (followAlbumColor) {
            followAlbumColor = false
            prefs().edit { putBoolean(PREF_FOLLOW_ALBUM_COLOR, false) }
        }
        
        prefs().edit { putInt(PREF_TEXT_COLOR, newColor) }
        lastState?.let { applyState(it) }
        resetCollapseTimer()
    }

    private fun loadStylePrefs() {
        val p = prefs()
        textSizeSp           = p.getFloat(PREF_TEXT_SIZE, SIZE_DEFAULT)
        baseTextColor        = p.getInt(PREF_TEXT_COLOR, Color.WHITE)
        followAlbumColor     = p.getBoolean(PREF_FOLLOW_ALBUM_COLOR, true)
        showAlbumArt         = p.getBoolean(PREF_SHOW_ALBUM_ART, true)
        enableTextStroke     = p.getBoolean(PREF_TEXT_STROKE, true)
        enableTextBackground = p.getBoolean(PREF_TEXT_BACKGROUND, false)
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
            val position = loadWindowPosition(overlayW)
            x = position.x
            y = position.y
            if (position.fromPrefs) saveWindowPosition(x, y)
        }
        windowParams = params

        val root = DraggableFrameLayout(context,
            onDrag = { dx, dy ->
                params.x += dx
                params.y += dy
                windowManager.updateViewLayout(rootView!!, params)
            },
            onDragEnd = {
                val clampedPosition = clampWindowPosition(params.x, params.y, params.width)
                if (params.x != clampedPosition.x || params.y != clampedPosition.y) {
                    params.x = clampedPosition.x
                    params.y = clampedPosition.y
                    windowManager.updateViewLayout(rootView!!, params)
                }
                saveWindowPosition(params.x, params.y)
            }
        )
        rootView = root

        val minimal  = buildMinimalView();  minimalContainer  = minimal
        val expanded = buildExpandedView(); expandedContainer = expanded

        root.addView(minimal,  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(expanded, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        windowManager.addView(root, params)
        isRunning = true
        Log.i(TAG, "Floating lyrics overlay attached")
    }

    private fun detachWindow() {
        mainHandler.removeCallbacks(collapseRunnable)
        try { rootView?.let { wm?.removeView(it) } } catch (_: Exception) {}
        rootView = null; wm = null; windowParams = null
        minimalLyricTv = null; expandedTitleTv = null
        expandedLyricTv = null; btnPlayPause = null
        minimalAlbumArtIv = null; expandedAlbumArtIv = null
        minimalContainer = null; expandedContainer = null
        minimalTextBackgroundContainer = null
        lastState = null; currentState = DisplayState.MINIMAL
        lastAppliedSnapshot = null
        isRunning = false
        Log.i(TAG, "Floating lyrics overlay detached")
    }

    private fun defaultWindowPosition(): WindowPosition =
        WindowPosition(0, dpToPx(DEFAULT_Y_DP), fromPrefs = false)

    private fun loadWindowPosition(overlayWidth: Int): WindowPosition {
        val p = prefs()
        val storedPosition = if (p.contains(PREF_POS_X) && p.contains(PREF_POS_Y)) {
            WindowPosition(
                x = p.getInt(PREF_POS_X, 0),
                y = p.getInt(PREF_POS_Y, dpToPx(DEFAULT_Y_DP)),
                fromPrefs = true
            )
        } else {
            defaultWindowPosition()
        }

        val clamped = clampWindowPosition(storedPosition.x, storedPosition.y, overlayWidth)
        return storedPosition.copy(x = clamped.x, y = clamped.y)
    }

    private fun moveWindowToStoredPosition() {
        val params = windowParams ?: return
        val root = rootView ?: return
        val windowManager = wm ?: return
        val position = loadWindowPosition(params.width)
        params.x = position.x
        params.y = position.y
        try {
            windowManager.updateViewLayout(root, params)
            if (position.fromPrefs) saveWindowPosition(params.x, params.y)
        } catch (e: Exception) {
            Log.w(TAG, "moveWindowToStoredPosition: ${e.message}")
        }
    }

    private fun saveWindowPosition(x: Int, y: Int) {
        val p = prefs()
        if (p.contains(PREF_POS_X) && p.contains(PREF_POS_Y) &&
            p.getInt(PREF_POS_X, 0) == x && p.getInt(PREF_POS_Y, 0) == y) {
            return
        }
        p.edit {
            putInt(PREF_POS_X, x)
            putInt(PREF_POS_Y, y)
        }
    }

    private fun clampWindowPosition(x: Int, y: Int, overlayWidth: Int): WindowPosition {
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val minVisible = dpToPx(MIN_VISIBLE_DP)
        val overlayLeftWhenCentered = (screenWidth - overlayWidth) / 2
        val minX = -overlayWidth + minVisible - overlayLeftWhenCentered
        val maxX = screenWidth - minVisible - overlayLeftWhenCentered
        val maxY = (screenHeight - minVisible).coerceAtLeast(0)

        return WindowPosition(
            x = x.coerceIn(minX, maxX),
            y = y.coerceIn(0, maxY),
            fromPrefs = false
        )
    }

    // ── View builders ─────────────────────────────────────────────────────────

    private fun buildMinimalView(): LinearLayout {
        // Horizontal container for optional album art + text
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
        }
        minimalTextBackgroundContainer = contentRow
        
        // Album Art
        minimalAlbumArtIv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                marginEnd = dpToPx(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(6).toFloat())
                }
            }
            visibility = View.GONE
        }
        
        // Lyric Text
        minimalLyricTv = OutlineTextView(context).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 8
        }

        contentRow.addView(minimalAlbumArtIv)
        contentRow.addView(minimalLyricTv)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(contentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { switchToExpanded() }
        }
    }

    private fun buildExpandedView(): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            background = buildPillBackground()
            visibility = View.GONE
            
            // Click empty area to collapse
            setOnClickListener { switchToMinimal() }
        }

        // Title · Artist
        val title = TextView(context).apply {
            textSize = 11f
            setTextColor("#AAFFFFFF".toColorInt())
            maxLines = 1
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        expandedTitleTv = title
        box.addView(title, matchW())

        // Lyric row (Album Art + Text)
        val lyricRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(6))
        }

        expandedAlbumArtIv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                marginEnd = dpToPx(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(6).toFloat())
                }
            }
            visibility = View.GONE
        }

        val lyric = OutlineTextView(context).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 8
        }
        expandedLyricTv = lyric
        
        lyricRow.addView(expandedAlbumArtIv)
        lyricRow.addView(expandedLyricTv)

        val resetTimer = View.OnClickListener {
            mainHandler.removeCallbacks(collapseRunnable)
            mainHandler.postDelayed(collapseRunnable, EXPANDED_TIMEOUT_MS)
        }
        lyricRow.setOnClickListener(resetTimer)
        title.setOnClickListener(resetTimer)
        
        box.addView(lyricRow, matchW())

        // Quick Settings Row (A-, Color, A+)
        val quickSettingsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2), 0, dpToPx(4))
        }
        val btnVMinus = makeTextButton("A-") { adjustTextSize(-2f) }
        val btnColor  = makeTextButton("●") { cycleColor() }
        val btnVPlus  = makeTextButton("A+") { adjustTextSize(2f) }
        
        quickSettingsRow.addView(btnVMinus)
        quickSettingsRow.addView(hSpace(8))
        quickSettingsRow.addView(btnColor)
        quickSettingsRow.addView(hSpace(8))
        quickSettingsRow.addView(btnVPlus)

        box.addView(quickSettingsRow, centerRow())

        // Row 1: media controls
        box.addView(buildControlsRow(), centerRow())

        return box
    }

    private fun buildControlsRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnMedia = makeIconButton(R.drawable.ic_music_note) { openMediaControl() }
        val btnPrev  = makeIconButton(R.drawable.ic_skip_previous) { sendMediaAction("ACTION_MEDIA_PREV") }
        val btnPlay  = makeIconButton(R.drawable.ic_play_arrow) {
            sendMediaAction(if (lastState?.isPlaying == true) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY")
        }
        val btnNext  = makeIconButton(R.drawable.ic_skip_next) { sendMediaAction("ACTION_MEDIA_NEXT") }
        val btnClose = makeIconButton(R.drawable.ic_cancel) { disableFloatingLyrics() }
        btnPlayPause = btnPlay
        row.addView(btnMedia); row.addView(hSpace(2))
        row.addView(btnPrev);  row.addView(hSpace(2))
        row.addView(btnPlay);  row.addView(hSpace(2))
        row.addView(btnNext);  row.addView(hSpace(2))
        row.addView(btnClose)
        return row
    }

    // ── State switching ───────────────────────────────────────────────────────

    private fun switchToMinimal() {
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
        val lyric = state.fullLyric.ifBlank { state.displayLyric.ifBlank { "♪" } }
        val actualColor = if (followAlbumColor) state.albumColor else baseTextColor
        val snapshot = ViewSnapshot(
            lyric = lyric,
            title = state.title,
            artist = state.artist,
            isPlaying = state.isPlaying,
            textSizeSp = textSizeSp,
            textColor = actualColor,
            showAlbumArt = showAlbumArt,
            albumArtHash = state.albumArt?.hashCode() ?: 0,
            enableTextStroke = enableTextStroke,
            enableTextBackground = enableTextBackground
        )
        if (snapshot == lastAppliedSnapshot) {
            return
        }
        lastAppliedSnapshot = snapshot

        // Apply to minimal
        minimalLyricTv?.text = lyric
        minimalLyricTv?.textSize = textSizeSp
        minimalLyricTv?.setTextColor(actualColor)
        minimalLyricTv?.setStroke(enableTextStroke)

        // Apply to expanded
        expandedLyricTv?.text = lyric
        expandedLyricTv?.textSize = textSizeSp
        expandedLyricTv?.setTextColor(actualColor)
        expandedLyricTv?.setStroke(enableTextStroke)

        expandedTitleTv?.text = buildString {
            append(state.title)
            if (state.artist.isNotBlank()) append(" · ${state.artist}")
        }
        
        // Album art check
        val hasArt = state.albumArt != null && showAlbumArt
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
        if (enableTextBackground) {
            minimalTextBackgroundContainer?.background = buildMinimalTextBackground()
        } else {
            minimalTextBackgroundContainer?.background = null
            minimalTextBackgroundContainer?.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2)) // default padding if no bg
        }

        val isPlaying = state.isPlaying
        btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        btnPlayPause?.setOnClickListener {
            sendMediaAction(if (isPlaying) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY")
        }
    }

    // ── Media actions ─────────────────────────────────────────────────────────

    private fun sendMediaAction(action: String) {
        try { context.startService(Intent(context, LyricService::class.java).apply { this.action = action }) }
        catch (e: Exception) { Log.w(TAG, "sendMediaAction: ${e.message}") }
        resetCollapseTimer()
    }

    private fun openMediaControl() {
        try { context.startActivity(Intent(context, MediaControlActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        catch (e: Exception) { Log.w(TAG, "openMediaControl: ${e.message}") }
        resetCollapseTimer()
    }

    private fun disableFloatingLyrics() {
        prefs().edit { putBoolean(PREF_KEY, false) }
        stop()
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
            setBackgroundColor(Color.TRANSPARENT)
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener { onClick() }
        }
    }

    private fun makeTextButton(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(36))
            setOnClickListener { onClick() }
        }
    }

    private fun hSpace(dp: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(dp), 1)
    }

    private fun matchW() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun centerRow() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
        it.gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun buildPillBackground(): GradientDrawable = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(20).toFloat()
        setColor(0xCC111111.toInt())
    }
    
    private fun buildMinimalTextBackground(): GradientDrawable = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(12).toFloat()
        setColor(0x88000000.toInt()) // subtle dark gray background
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    // ── DraggableFrameLayout ──────────────────────────────────────────────────

    /**
     * FrameLayout with gesture-discriminating touch handling:
     *  - movement < 10 dp  → let children handle as a click.
     *  - movement ≥ 10 dp  → intercept and drag the window.
     */
    @SuppressLint("ViewConstructor")
    private inner class DraggableFrameLayout(
        ctx: Context,
        private val onDrag: (dx: Int, dy: Int) -> Unit,
        private val onDragEnd: () -> Unit
    ) : FrameLayout(ctx) {

        private val dragThreshold = dpToPx(10)
        private var startX = 0f; private var startY = 0f
        private var lastX  = 0f; private var lastY  = 0f
        private var isDragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX; startY = ev.rawY
                lastX  = ev.rawX; lastY  = ev.rawY
                isDragging = false; false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && (
                            abs(ev.rawX - startX) > dragThreshold ||
                            abs(ev.rawY - startY) > dragThreshold)) isDragging = true
                isDragging
            }
            else -> false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            return when (ev.action) {
                MotionEvent.ACTION_DOWN -> { lastX = ev.rawX; lastY = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    onDrag((ev.rawX - lastX).toInt(), (ev.rawY - lastY).toInt())
                    lastX = ev.rawX; lastY = ev.rawY; true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        onDragEnd()
                    } else {
                        // Forward click to root if it wasn't dragged
                        performClick()
                    }
                    isDragging = false; true 
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) onDragEnd()
                    isDragging = false; true
                }
                else -> super.onTouchEvent(ev)
            }
        }
    }
}
