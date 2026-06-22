package com.example.islandlyrics.ui.overlay.floating
import com.example.islandlyrics.ui.overlay.model.UIState
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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.example.islandlyrics.R
import com.example.islandlyrics.core.settings.AppPreferences
import com.example.islandlyrics.ui.overlay.views.OutlineTextView

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
        const val PREF_POS_X           = FloatingLyricsWindowPositionStore.KEY_POS_X
        const val PREF_POS_Y           = FloatingLyricsWindowPositionStore.KEY_POS_Y

        private const val EXPANDED_TIMEOUT_MS = 4000L

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

    private val styleStore = FloatingLyricsStyleStore(context)
    private val positionStore = FloatingLyricsWindowPositionStore(context)
    private val actionController = FloatingLyricsActionController(
        context = context,
        onAfterAction = { resetCollapseTimer() },
        onDisabled = { stop() }
    )

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
            styleStore.reload()
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

    // ── Quick Settings logic ──────────────────────────────────────────────────

    private fun adjustTextSize(delta: Float) {
        styleStore.adjustTextSize(delta)
        lastState?.let { applyState(it) }
        resetCollapseTimer()
    }

    private fun cycleColor() {
        styleStore.cycleColor()
        lastState?.let { applyState(it) }
        resetCollapseTimer()
    }

    private fun loadStylePrefs() {
        styleStore.reload()
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
            enableTextBackground = style.enableTextBackground
        )
        if (snapshot == lastAppliedSnapshot) {
            return
        }
        lastAppliedSnapshot = snapshot

        // Apply to minimal
        minimalLyricTv?.text = lyric
        minimalLyricTv?.textSize = style.textSizeSp
        minimalLyricTv?.setTextColor(actualColor)
        minimalLyricTv?.setStroke(style.enableTextStroke)

        // Apply to expanded
        expandedLyricTv?.text = lyric
        expandedLyricTv?.textSize = style.textSizeSp
        expandedLyricTv?.setTextColor(actualColor)
        expandedLyricTv?.setStroke(style.enableTextStroke)

        expandedTitleTv?.text = buildString {
            append(state.title)
            if (state.artist.isNotBlank()) append(" · ${state.artist}")
        }
        
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
            minimalTextBackgroundContainer?.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2)) // default padding if no bg
        }

        val isPlaying = state.isPlaying
        btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        btnPlayPause?.setOnClickListener {
            actionController.sendMediaAction(if (isPlaying) "ACTION_MEDIA_PAUSE" else "ACTION_MEDIA_PLAY")
        }
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
}
