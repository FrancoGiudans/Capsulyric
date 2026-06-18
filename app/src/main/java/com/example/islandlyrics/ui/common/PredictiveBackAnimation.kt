package com.example.islandlyrics.ui.common

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.core.content.edit
import com.example.islandlyrics.R

const val PREF_PREDICTIVE_BACK_ENABLED = "predictive_back_enabled"
const val PREF_PREDICTIVE_BACK_ANIMATION_MODE = "predictive_back_animation_mode"
const val PREF_PREDICTIVE_BACK_ANIMATION_STYLE = "predictive_back_animation_style"

enum class PredictiveBackAnimationMode(
    val prefValue: String,
    @param:StringRes val labelRes: Int
) {
    PageSpecific("page_specific", R.string.predictive_back_animation_mode_page_specific),
    Consistent("consistent", R.string.predictive_back_animation_mode_consistent);

    companion object {
        val options: List<PredictiveBackAnimationMode> = entries
        val default: PredictiveBackAnimationMode = PageSpecific

        fun fromPrefValue(value: String?): PredictiveBackAnimationMode {
            return entries.firstOrNull { it.prefValue == value } ?: default
        }

        fun read(prefs: SharedPreferences): PredictiveBackAnimationMode {
            return fromPrefValue(prefs.getString(PREF_PREDICTIVE_BACK_ANIMATION_MODE, default.prefValue))
        }

        fun write(prefs: SharedPreferences, mode: PredictiveBackAnimationMode) {
            prefs.edit { putString(PREF_PREDICTIVE_BACK_ANIMATION_MODE, mode.prefValue) }
        }
    }
}

enum class PredictiveBackAnimationStyle(
    val prefValue: String,
    @param:StringRes val labelRes: Int
) {
    EdgeShrink("edge_shrink", R.string.predictive_back_animation_edge_shrink),
    ScaleSlide("scale_slide", R.string.predictive_back_animation_scale_slide),
    HorizontalSlide("horizontal_slide", R.string.predictive_back_animation_horizontal_slide),
    Fade("fade", R.string.predictive_back_animation_fade),
    VerticalSlide("vertical_slide", R.string.predictive_back_animation_vertical_slide),
    Zoom("zoom", R.string.predictive_back_animation_zoom),
    Depth("depth", R.string.predictive_back_animation_depth),
    Flip("flip", R.string.predictive_back_animation_flip);

    companion object {
        val options: List<PredictiveBackAnimationStyle> = entries
        val default: PredictiveBackAnimationStyle = EdgeShrink

        fun fromPrefValue(value: String?): PredictiveBackAnimationStyle {
            return entries.firstOrNull { it.prefValue == value } ?: default
        }

        fun read(prefs: SharedPreferences): PredictiveBackAnimationStyle {
            return fromPrefValue(prefs.getString(PREF_PREDICTIVE_BACK_ANIMATION_STYLE, default.prefValue))
        }

        fun write(prefs: SharedPreferences, style: PredictiveBackAnimationStyle) {
            prefs.edit { putString(PREF_PREDICTIVE_BACK_ANIMATION_STYLE, style.prefValue) }
        }
    }
}

@Composable
internal fun rememberPredictiveBackEnabledState(prefs: SharedPreferences): Boolean {
    var enabled by remember {
        mutableStateOf(prefs.getBoolean(PREF_PREDICTIVE_BACK_ENABLED, true))
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREF_PREDICTIVE_BACK_ENABLED) {
                enabled = prefs.getBoolean(PREF_PREDICTIVE_BACK_ENABLED, true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun rememberPredictiveBackAnimationModeState(prefs: SharedPreferences): PredictiveBackAnimationMode {
    var mode by remember {
        mutableStateOf(PredictiveBackAnimationMode.read(prefs))
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREF_PREDICTIVE_BACK_ANIMATION_MODE) {
                mode = PredictiveBackAnimationMode.read(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}

@Composable
internal fun rememberPredictiveBackAnimationStyleState(prefs: SharedPreferences): PredictiveBackAnimationStyle {
    var style by remember {
        mutableStateOf(PredictiveBackAnimationStyle.read(prefs))
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREF_PREDICTIVE_BACK_ANIMATION_STYLE) {
                style = PredictiveBackAnimationStyle.read(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return style
}

internal fun predictiveBackExitDirection(isLeftEdge: Boolean): Float {
    return if (isLeftEdge) 1f else -1f
}

internal fun predictiveBackPivotY(touchY: Float?, containerHeightPx: Int): Float {
    return if (touchY != null && containerHeightPx > 0) {
        (touchY / containerHeightPx).coerceIn(0.1f, 0.9f)
    } else {
        0.5f
    }
}

internal fun GraphicsLayerScope.applyPredictiveBackFrontTransform(
    style: PredictiveBackAnimationStyle,
    progress: Float,
    direction: Float,
    pivotY: Float = 0.5f,
    completionProgress: Float? = null
) {
    val p = progress.coerceIn(0f, 1f)
    val completion = completionProgress?.coerceIn(0f, 1f)
    val width = size.width
    val height = size.height
    val pivotX = if (direction >= 0f) 0.8f else 0.2f

    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    rotationX = 0f
    rotationY = 0f
    transformOrigin = TransformOrigin(pivotX, pivotY)

    when (style) {
        PredictiveBackAnimationStyle.EdgeShrink -> {
            val scale = 1f - 0.1f * p
            scaleX = scale
            scaleY = scale
            completion?.let {
                translationX = direction * width * it
            }
        }
        PredictiveBackAnimationStyle.ScaleSlide -> {
            val scale = 1f - 0.1f * p
            scaleX = scale
            scaleY = scale
            translationX = direction * width * p
            alpha = 1f - 0.04f * p
        }
        PredictiveBackAnimationStyle.HorizontalSlide -> {
            translationX = direction * width * p
        }
        PredictiveBackAnimationStyle.Fade -> {
            alpha = 1f - p
            val scale = 1f - 0.04f * p
            scaleX = scale
            scaleY = scale
        }
        PredictiveBackAnimationStyle.VerticalSlide -> {
            translationY = height * p
            alpha = 1f - 0.08f * p
        }
        PredictiveBackAnimationStyle.Zoom -> {
            val scale = 1f - 0.18f * p
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.35f * p
        }
        PredictiveBackAnimationStyle.Depth -> {
            val scale = 1f - 0.18f * p
            scaleX = scale
            scaleY = scale
            translationX = direction * width * 0.22f * p
            alpha = 1f - 0.3f * p
        }
        PredictiveBackAnimationStyle.Flip -> {
            cameraDistance = width.coerceAtLeast(height).coerceAtLeast(1f) * 8f
            rotationY = -direction * 70f * p
            translationX = direction * width * 0.45f * p
            alpha = 1f - 0.7f * p
        }
    }
}

internal fun GraphicsLayerScope.applyPredictiveBackUnderlayTransform(
    style: PredictiveBackAnimationStyle,
    dismissProgress: Float,
    direction: Float
) {
    val p = dismissProgress.coerceIn(0f, 1f)
    val coveredProgress = 1f - p
    val width = size.width
    val height = size.height

    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    rotationX = 0f
    rotationY = 0f
    transformOrigin = TransformOrigin.Center

    when (style) {
        PredictiveBackAnimationStyle.EdgeShrink -> {
            alpha = 1f - 0.04f * coveredProgress
        }
        PredictiveBackAnimationStyle.ScaleSlide -> {
            translationX = -direction * width * 0.105f * coveredProgress
            val scale = 1f - 0.035f * coveredProgress
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.04f * coveredProgress
        }
        PredictiveBackAnimationStyle.HorizontalSlide -> {
            translationX = -direction * width * 0.08f * coveredProgress
            alpha = 1f - 0.03f * coveredProgress
        }
        PredictiveBackAnimationStyle.Fade -> {
            alpha = 0.72f + 0.28f * p
        }
        PredictiveBackAnimationStyle.VerticalSlide -> {
            translationY = -height * 0.04f * coveredProgress
            alpha = 1f - 0.04f * coveredProgress
        }
        PredictiveBackAnimationStyle.Zoom -> {
            val scale = 0.94f + 0.06f * p
            scaleX = scale
            scaleY = scale
            alpha = 0.8f + 0.2f * p
        }
        PredictiveBackAnimationStyle.Depth -> {
            val scale = 0.9f + 0.1f * p
            scaleX = scale
            scaleY = scale
            translationX = -direction * width * 0.04f * coveredProgress
            alpha = 0.78f + 0.22f * p
        }
        PredictiveBackAnimationStyle.Flip -> {
            cameraDistance = width.coerceAtLeast(height).coerceAtLeast(1f) * 8f
            rotationY = direction * 8f * coveredProgress
            val scale = 0.96f + 0.04f * p
            scaleX = scale
            scaleY = scale
            alpha = 0.82f + 0.18f * p
        }
    }
}

internal fun predictiveBackUnderlayScrimAlpha(dismissProgress: Float): Float {
    return 0.06f * (1f - dismissProgress.coerceIn(0f, 1f))
}
