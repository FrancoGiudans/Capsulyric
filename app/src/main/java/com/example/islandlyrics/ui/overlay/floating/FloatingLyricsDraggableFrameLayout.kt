package com.example.islandlyrics.ui.overlay.floating
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * FrameLayout with gesture-discriminating touch handling:
 * movement below the threshold lets children handle clicks; larger movement drags the window.
 */
@SuppressLint("ViewConstructor")
internal class FloatingLyricsDraggableFrameLayout(
    context: Context,
    dragThresholdPx: Int,
    private val onDrag: (dx: Int, dy: Int) -> Unit,
    private val onDragEnd: () -> Unit
) : FrameLayout(context) {
    private val dragThreshold = dragThresholdPx
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = when (ev.action) {
        MotionEvent.ACTION_DOWN -> {
            startX = ev.rawX
            startY = ev.rawY
            lastX = ev.rawX
            lastY = ev.rawY
            isDragging = false
            false
        }
        MotionEvent.ACTION_MOVE -> {
            if (!isDragging && (
                    abs(ev.rawX - startX) > dragThreshold ||
                        abs(ev.rawY - startY) > dragThreshold
                    )
            ) {
                isDragging = true
            }
            isDragging
        }
        else -> false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX
                lastY = ev.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                onDrag((ev.rawX - lastX).toInt(), (ev.rawY - lastY).toInt())
                lastX = ev.rawX
                lastY = ev.rawY
                true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    onDragEnd()
                } else {
                    performClick()
                }
                isDragging = false
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) onDragEnd()
                isDragging = false
                true
            }
            else -> super.onTouchEvent(ev)
        }
    }
}
