package com.example.islandlyrics.ui.overlay.superisland.calibration

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.islandlyrics.R

internal interface RightCalibrationControlWindow {
    fun attach()
    fun close()
    fun render(probe: CalibrationProbe, presentationToken: String, count: Int, detailed: Boolean, ring: Boolean, summary: String? = null)
    fun setAnswering(enabled: Boolean)
    fun showMissing()
}

/** A non-focusable control window: no Activity or keyboard is brought to the foreground. */
internal class RightCalibrationOverlay(
    context: Context,
    private val answer: (String, Boolean) -> Unit,
    private val missing: () -> Unit,
    private val replay: () -> Unit,
    private val back: () -> Unit,
    private val pause: () -> Unit,
    private val discard: () -> Unit,
    private val save: () -> Unit,
    private val more: () -> Unit
) : RightCalibrationControlWindow {
    private val context = android.view.ContextThemeWrapper(context, R.style.Theme_IslandLyrics)
    private val manager = context.getSystemService(WindowManager::class.java)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun color(attr: Int): Int {
        val value = TypedValue()
        this.context.theme.resolveAttribute(attr, value, true)
        return if (value.resourceId != 0) this.context.getColor(value.resourceId) else value.data
    }
    private val foregroundColor = color(android.R.attr.textColorPrimary)
    private val root = LinearLayout(this.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(color(com.google.android.material.R.attr.colorSurface))
        }
        elevation = dp(8).toFloat()
    }
    private fun text() = TextView(this.context).apply { setTextColor(foregroundColor); textSize = 14f }
    private val title = text().apply { textSize = 16f; setPadding(0, dp(6), 0, dp(6)) }
    private val description = text()
    private val sample = text().apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val ending = text().apply { setPadding(0, dp(4), 0, dp(4)) }
    private val answers = LinearLayout(this.context)
    private val actions = LinearLayout(this.context)
    private val footer = LinearLayout(this.context)
    private var token = ""
    private var attached = false
    private var answerButtons = emptyList<Button>()
    private val params = WindowManager.LayoutParams(
        minOf(context.resources.displayMetrics.widthPixels - dp(24), dp(400)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(32) }

    init {
        root.addView(title); root.addView(description); root.addView(sample); root.addView(ending)
        root.addView(answers); root.addView(actions); root.addView(footer)
        installDrag()
    }

    private fun button(row: LinearLayout, label: Int, onClick: () -> Unit): Button = Button(context).apply {
        text = context.getString(label)
        isAllCaps = false
        minHeight = dp(48)
        setTextColor(foregroundColor)
        backgroundTintList = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorSurfaceVariant))
        setOnClickListener { onClick() }
        row.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    override fun attach() { manager.addView(root, params); attached = true }
    override fun close() {
        if (attached) runCatching { manager.removeView(root) }
        attached = false
    }

    override fun render(probe: CalibrationProbe, presentationToken: String, count: Int, detailed: Boolean, ring: Boolean, summary: String?) {
        token = presentationToken
        title.text = context.getString(stageTitle(probe.stage)) + " · " + context.getString(if (ring) R.string.wizard_ring_on else R.string.wizard_ring_off)
        description.text = when {
            summary != null -> summary
            probe.blocked -> context.getString(R.string.wizard_blocked)
            probe.scrolling -> context.getString(R.string.wizard_scroll_instruction)
            else -> context.getString(R.string.wizard_judge_instruction, count + 1)
        }
        sample.text = probe.sample
        sample.visibility = if (summary == null) View.VISIBLE else View.GONE
        ending.text = context.getString(R.string.wizard_expected_ending, Glyphs.parts(probe.sample).takeLast(8).joinToString(""))
        ending.visibility = if (summary == null) View.VISIBLE else View.GONE
        answers.removeAllViews(); actions.removeAllViews(); footer.removeAllViews()
        if (summary == null) {
            val capturedToken = presentationToken
            answerButtons = listOf(
                button(answers, R.string.wizard_complete) { answer(capturedToken, true) },
                button(answers, R.string.wizard_incomplete) { answer(capturedToken, false) }
            )
            button(actions, R.string.wizard_missing, missing)
            button(actions, R.string.wizard_replay, replay)
            button(actions, R.string.wizard_back, back).isEnabled = count > 0
            setAnswering(false)
        } else {
            answerButtons = emptyList()
            button(answers, R.string.wizard_save, save)
            if (!detailed) button(answers, R.string.wizard_more, more)
        }
        button(footer, R.string.wizard_pause, pause)
        button(footer, R.string.wizard_discard, discard)
    }

    override fun setAnswering(enabled: Boolean) { answerButtons.forEach { it.isEnabled = enabled } }
    override fun showMissing() { description.text = context.getString(R.string.wizard_missing_help) }

    @SuppressLint("ClickableViewAccessibility")
    private fun installDrag() {
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0
        title.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val metrics = context.resources.displayMetrics
                    params.x = (originX + event.rawX - downX).toInt()
                        .coerceIn(-(metrics.widthPixels - params.width) / 2, (metrics.widthPixels - params.width) / 2)
                    params.y = (originY + downY - event.rawY).toInt().coerceIn(dp(16),
                        (metrics.heightPixels - root.height - dp(80)).coerceAtLeast(dp(16)))
                    if (attached) runCatching { manager.updateViewLayout(root, params) }
                }
                MotionEvent.ACTION_UP -> view.performClick()
            }
            true
        }
    }

    companion object {
        fun stageTitle(stage: CalibrationStage) = when (stage) {
            CalibrationStage.VISIBILITY -> R.string.wizard_stage_visibility
            CalibrationStage.RANGE -> R.string.wizard_stage_range
            CalibrationStage.LETTERS -> R.string.wizard_stage_letters
            CalibrationStage.DIGITS -> R.string.wizard_stage_digits
            CalibrationStage.MIXED -> R.string.wizard_stage_mixed
            CalibrationStage.TAIL -> R.string.wizard_stage_tail
            CalibrationStage.DETAIL -> R.string.wizard_stage_detail
            CalibrationStage.COMPLETE -> R.string.wizard_stage_done
        }
    }
}
