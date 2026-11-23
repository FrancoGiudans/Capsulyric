/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.lerp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.islandlyrics.core.theme.ensureColorContrast
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val PlaybackCoverSampleSize = 64
private const val FlowDownscale = 4f
private const val FlowFramePeriodNanos = 1_000_000_000L / 30
private const val CoverHueGroups = 12
private const val BackgroundContrast = 8.0

internal data class PlaybackCoverAppearance(
    val colors: List<Int?>,
    val flowTexture: Bitmap?,
    val tone: PlaybackCoverTone,
    val playbackShader: RuntimeShader? = null,
)

internal data class PlaybackCoverTone(
    val scrimAlpha: Float,
    val brightnessBias: Float,
)

@Composable
internal fun rememberPlaybackCoverAppearance(cover: Bitmap?): State<PlaybackCoverAppearance?> =
    produceState<PlaybackCoverAppearance?>(null, cover) {
        value = if (cover == null) null else withContext(Dispatchers.Default) {
            try {
                preparePlaybackCover(cover)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }
    }

internal fun playbackAlbumAccent(colors: List<Int?>?): Color {
    val sample = mostSaturatedPlaybackColor(colors) ?: return Color.White
    // Match the brightest background that the palette can produce against white.
    val brightestBackground = 0xFF515151.toInt()
    return Color(ensureColorContrast(sample, brightestBackground, Color.White.toArgb(), 4.5, 0.24f))
}

internal fun mostSaturatedPlaybackColor(colors: List<Int?>?): Int? {
    val hsl = FloatArray(3)
    return colors?.filterNotNull()?.maxByOrNull {
        ColorUtils.colorToHSL(it, hsl)
        hsl[1]
    }
}

@Composable
internal fun LyricsPlaybackBackground(
    appearance: PlaybackCoverAppearance?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        !LocalView.current.isHardwareAccelerated || !ValueAnimator.areAnimatorsEnabled()
    ) {
        StaticPlaybackBackground(appearance?.colors, modifier)
        return
    }

    val flowTime = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val animateFlow = playing && appearance?.playbackShader != null
    LaunchedEffect(lifecycle, animateFlow) {
        if (!animateFlow) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val accumulated = flowTime.longValue
            val start = withFrameNanos { it }
            var frame = start
            while (isActive) {
                // Leave the display-rate frame clock available to lyric and control animations.
                val remaining = frame + FlowFramePeriodNanos - System.nanoTime()
                delay((remaining / 1_000_000L - 1).coerceAtLeast(1L))
                frame = withFrameNanos { it }
                flowTime.longValue = accumulated + frame - start
            }
        }
    }
    val transition = updateTransition(appearance, label = "playback cover flow")
    transition.AnimatedContent(
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(450)) togetherWith ExitTransition.KeepUntilTransitionsFinished).using(null)
        },
    ) { target ->
        val shader = target?.playbackShader
        if (shader == null) {
            StaticPlaybackBackground(target?.colors)
        } else {
            FlowPlaybackBackground(shader, flowTime, current = target === transition.targetState)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun FlowPlaybackBackground(shader: RuntimeShader, time: LongState, current: Boolean) {
    val layer = remember(shader) { PlaybackFlowLayer(shader) }
    DisposableEffect(layer) {
        onDispose { layer.node.discardDisplayList() }
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        // Only the incoming layer observes VSYNC. The outgoing layer retains its last recorded frame.
        val phase = if (current) time.longValue else layer.recordedTime
        val targetW = (size.width / FlowDownscale).toInt().coerceAtLeast(1)
        val targetH = (size.height / FlowDownscale).toInt().coerceAtLeast(1)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (native.isHardwareAccelerated) {
                layer.record(targetW, targetH, phase)
                val saved = native.save()
                native.scale(size.width / targetW, size.height / targetH)
                native.drawRenderNode(layer.node)
                native.restoreToCount(saved)
            } else {
                shader.setPlaybackFlowFrame(size.width, size.height, phase / 1_000_000_000.0)
                drawRect(layer.brush)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PlaybackFlowLayer(private val shader: RuntimeShader) {
    val node = RenderNode("PlaybackFlowQuarterSize").apply {
        // A scaled display list alone still runs its shader at framebuffer resolution.
        setUseCompositingLayer(true, Paint(Paint.FILTER_BITMAP_FLAG))
        setHasOverlappingRendering(false)
    }
    val brush = ShaderBrush(shader)
    var recordedTime = 0L
        private set
    private val paint = Paint().apply { this.shader = this@PlaybackFlowLayer.shader }

    fun record(width: Int, height: Int, time: Long) {
        if (node.hasDisplayList() && node.width == width && node.height == height && recordedTime == time) return
        node.setPosition(0, 0, width, height)
        shader.setPlaybackFlowFrame(width.toFloat(), height.toFloat(), time / 1_000_000_000.0)
        val recording = node.beginRecording()
        try {
            recording.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } finally {
            node.endRecording()
        }
        recordedTime = time
    }
}

@Composable
internal fun StaticPlaybackBackground(colors: List<Int?>?, modifier: Modifier = Modifier) {
    val surface = MiuixTheme.colorScheme.surface
    val palette = remember(colors, surface) {
        playbackBackgroundPalette(colors, surface.toArgb()).map { Color(it) }
    }
    val topLeft by animateColorAsState(palette[0], tween(450), label = "cover top left")
    val topRight by animateColorAsState(palette[1], tween(450), label = "cover top right")
    val bottomLeft by animateColorAsState(palette[2], tween(450), label = "cover bottom left")
    val bottomRight by animateColorAsState(palette[3], tween(450), label = "cover bottom right")
    Box(modifier.fillMaxSize().drawWithCache {
        val gradient = Brush.horizontalGradient(listOf(bottomLeft, bottomRight))
        onDrawBehind { drawRect(gradient) }
    }) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val gradient = Brush.horizontalGradient(listOf(topLeft, topRight))
                    val mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
                    onDrawBehind {
                        drawRect(gradient)
                        drawRect(mask, blendMode = BlendMode.DstIn)
                    }
                },
        )
    }
}

internal fun playbackBackgroundPalette(colors: List<Int?>?, surface: Int): List<Int> =
    List(4) { index ->
        val sampled = colors?.getOrNull(index) ?: return@List surface
        ensureColorContrast(sampled, android.graphics.Color.WHITE, surface, BackgroundContrast, 0.06f)
    }

private fun preparePlaybackCover(bitmap: Bitmap): PlaybackCoverAppearance? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        bitmap
    }
    val size = PlaybackCoverSampleSize
    val sample = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    return try {
        val side = min(software.width, software.height)
        val left = (software.width - side) / 2
        val top = (software.height - side) / 2
        Canvas(sample).drawBitmap(
            software,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        sample.getPixels(pixels, 0, size, 0, 0, size, size)
        val colors = sampleCoverColors(pixels)
        if (colors.all { it == null }) return null
        val tone = playbackCoverTone(pixels)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appearance = PlaybackCoverAppearance(colors, preBlurFlowTexture(sample), tone)
            // Card export creates its own shader so it cannot mutate the live uniforms.
            appearance.copy(playbackShader = createPlaybackFlowShader(appearance))
        } else {
            PlaybackCoverAppearance(colors, null, tone)
        }
    } finally {
        sample.recycle()
        if (software !== bitmap) software.recycle()
    }
}

internal fun playbackCoverTone(pixels: IntArray): PlaybackCoverTone {
    // Use the full downsampled image and composite transparency over black, like the flow texture.
    var brightnessSum = 0.0
    for (pixel in pixels) {
        val red = pixel shr 16 and 255
        val green = pixel shr 8 and 255
        val blue = pixel and 255
        val opacity = (pixel ushr 24) / 255.0
        brightnessSum += (0.299 * red + 0.587 * green + 0.114 * blue) * opacity
    }
    val brightness = (brightnessSum / (pixels.size.coerceAtLeast(1) * 255.0)).toFloat()
    return when {
        brightness > 0.70f -> PlaybackCoverTone(lerp(0.26f, 0.30f, (brightness - 0.70f) / 0.30f), 0f)
        brightness >= 0.35f -> PlaybackCoverTone(lerp(0.12f, 0.16f, (brightness - 0.35f) / 0.35f), 0f)
        else -> {
            val fraction = brightness / 0.35f
            PlaybackCoverTone(lerp(0.04f, 0.08f, fraction), lerp(12f, 8f, fraction) / 255f)
        }
    }
}

private fun sampleCoverColors(pixels: IntArray): List<Int?> =
    List(4) { quadrant ->
        val half = PlaybackCoverSampleSize / 2
        val xStart = (quadrant % 2) * half
        val yStart = (quadrant / 2) * half
        val average = CoverColorGroup()
        val hues = Array(CoverHueGroups) { CoverColorGroup() }
        val hsl = FloatArray(3)
        for (y in yStart until yStart + half) {
            for (x in xStart until xStart + half) {
                val pixel = pixels[y * PlaybackCoverSampleSize + x]
                val alpha = android.graphics.Color.alpha(pixel).toFloat()
                if (alpha == 0f) continue
                average.add(pixel, alpha)
                ColorUtils.colorToHSL(pixel, hsl)
                val saturation = hsl[1]
                if (saturation >= 0.24f && hsl[2] in 0.12f..0.9f) {
                    val index = (hsl[0] / (360f / CoverHueGroups)).toInt().coerceIn(0, CoverHueGroups - 1)
                    hues[index].add(pixel, alpha * saturation * saturation)
                }
            }
        }
        val dominant = hues.maxBy { it.weight }
        if (dominant.weight > average.weight * 0.025f) {
            dominant.color()?.let {
                ColorUtils.colorToHSL(it, hsl)
                hsl[1] = (hsl[1] * 1.16f).coerceAtMost(1f)
                ColorUtils.HSLToColor(hsl)
            }
        } else {
            average.color()
        }
    }

internal fun preBlurFlowTexture(source: Bitmap, radius: Int = 5): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val blurred = blurPlaybackCover(pixels, width, height, radius)
    return Bitmap.createBitmap(blurred, width, height, Bitmap.Config.ARGB_8888)
}

internal fun blurPlaybackCover(pixels: IntArray, width: Int, height: Int = width, radius: Int = 5): IntArray {
    val result = IntArray(pixels.size) { index ->
        val pixel = pixels[index]
        val alpha = pixel ushr 24
        val red = (pixel shr 16 and 255) * alpha / 255
        val green = (pixel shr 8 and 255) * alpha / 255
        val blue = (pixel and 255) * alpha / 255
        (255 shl 24) or (red shl 16) or (green shl 8) or blue
    }
    val scratch = IntArray(pixels.size)
    val boundedRadius = radius.coerceIn(0, maxOf(width, height) - 1)
    if (boundedRadius > 0) {
        repeat(3) {
            blurScanlines(result, scratch, height, width, width, 1, boundedRadius)
            blurScanlines(scratch, result, width, height, 1, width, boundedRadius)
        }
    }
    return result
}

private fun blurScanlines(
    source: IntArray,
    target: IntArray,
    lines: Int,
    length: Int,
    lineStride: Int,
    pixelStride: Int,
    radius: Int,
) {
    val taps = 2 * radius + 1
    val half = taps / 2
    for (line in 0 until lines) {
        val offset = line * lineStride
        var red = 0
        var green = 0
        var blue = 0
        for (tap in -radius..radius) {
            val pixel = source[offset + tap.coerceIn(0, length - 1) * pixelStride]
            red += pixel shr 16 and 255
            green += pixel shr 8 and 255
            blue += pixel and 255
        }
        for (i in 0 until length) {
            target[offset + i * pixelStride] = (255 shl 24) or
                ((red + half) / taps shl 16) or ((green + half) / taps shl 8) or ((blue + half) / taps)
            val leaving = source[offset + (i - radius).coerceAtLeast(0) * pixelStride]
            val entering = source[offset + (i + radius + 1).coerceAtMost(length - 1) * pixelStride]
            red += (entering shr 16 and 255) - (leaving shr 16 and 255)
            green += (entering shr 8 and 255) - (leaving shr 8 and 255)
            blue += (entering and 255) - (leaving and 255)
        }
    }
}

private class CoverColorGroup {
    var weight = 0f
        private set
    private var red = 0f
    private var green = 0f
    private var blue = 0f

    fun add(pixel: Int, contribution: Float) {
        weight += contribution
        red += android.graphics.Color.red(pixel) * contribution
        green += android.graphics.Color.green(pixel) * contribution
        blue += android.graphics.Color.blue(pixel) * contribution
    }

    fun color(): Int? = if (weight > 0f) {
        android.graphics.Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
    } else {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun createPlaybackFlowShader(appearance: PlaybackCoverAppearance): RuntimeShader? {
    val texture = appearance.flowTexture?.takeUnless { it.isRecycled } ?: return null
    return try {
        RuntimeShader(PlaybackFlowShader).apply {
            val bitmapShader = BitmapShader(texture, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            bitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            setInputShader("cover", bitmapShader)
            setFloatUniform("textureSide", PlaybackCoverSampleSize.toFloat())
            setFloatUniform("tone", appearance.tone.scrimAlpha, appearance.tone.brightnessBias)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.setPlaybackFlowFrame(width: Float, height: Float, seconds: Double) {
    val shortSide = minOf(width, height)
    val longSide = maxOf(width, height)
    val inverseScale = 1f / (shortSide * 0.35f + longSide * 0.65f).coerceAtLeast(1f)
    setFloatUniform("viewport", width / 2f, height / 2f, inverseScale)
    setFloatUniform("wavePhase", ((seconds % 24.0) / 24.0 * 2.0 * PI).toFloat())
    setFlowLayer("layerA", seconds, period = 20.0, zoom = 1.55, direction = 1, initialAngle = 0.0)
    setFlowLayer("layerB", seconds, period = 29.0, zoom = 1.75, direction = -1, initialAngle = 1.3)
    setFlowLayer("layerC", seconds, period = 41.0, zoom = 1.95, direction = 1, initialAngle = 2.6)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setFlowLayer(
    name: String,
    seconds: Double,
    period: Double,
    zoom: Double,
    direction: Int,
    initialAngle: Double,
) {
    val phase = seconds % period / period * 2.0 * PI
    val angle = initialAngle + direction * (phase + 0.18 * sin(phase))
    setFloatUniform(
        name,
        (cos(angle) / zoom).toFloat(),
        (sin(angle) / zoom).toFloat(),
        (0.09 * cos(phase + initialAngle)).toFloat(),
        (0.08 * sin(phase + initialAngle)).toFloat(),
    )
}

private const val PlaybackFlowShader = """
    uniform shader cover;
    uniform float textureSide;
    uniform float3 viewport;
    uniform float wavePhase;
    uniform float4 layerA;
    uniform float4 layerB;
    uniform float4 layerC;
    uniform float2 tone;

    float3 sampleCoverLinear(float2 position, float4 transform) {
        float2 rotated = float2(
            position.x * transform.x - position.y * transform.y,
            position.x * transform.y + position.y * transform.x
        );
        float2 center = rotated + transform.zw + 0.5;

        return toLinearSrgb(float3(cover.eval(center * textureSide).rgb));
    }

    half4 main(float2 position) {
        float2 rawUv = (position - viewport.xy) * viewport.z;
        float2 screenUv = clamp(position / max(viewport.xy * 2.0, float2(1.0)), 0.0, 1.0);

        float2 warp = float2(
            sin(rawUv.y * 1.6 + wavePhase) + 0.4 * cos(rawUv.x * 1.3 - wavePhase),
            cos(rawUv.x * 1.6 - wavePhase) + 0.4 * sin(rawUv.y * 1.3 + wavePhase)
        ) * 0.045;
        float2 uv = rawUv + warp;

        float3 colA = sampleCoverLinear(uv, layerA);
        float3 colB = sampleCoverLinear(uv, layerB);
        float3 colC = sampleCoverLinear(uv, layerC);

        // 全屏余弦平滑过渡（C∞ 连续），消除 smoothstep 边界处的二阶导数折带
        float diag = clamp(screenUv.x * 0.58 + screenUv.y * 0.42, 0.0, 1.0);
        float splitWeight = 0.5 - 0.5 * cos(diag * 3.14159265);
        float3 linearRgb = mix(colA, colB, splitWeight);

        float glowCurve = 0.5 - 0.5 * cos(screenUv.y * 3.14159265);
        float glowMask = mix(0.18, 0.36, glowCurve);
        float3 glow = colC * glowMask;
        linearRgb = 1.0 - (1.0 - linearRgb) * (1.0 - glow);

        const float3 lumaWeights = float3(0.2126, 0.7152, 0.0722);
        float rawLuma = dot(linearRgb, lumaWeights);
        linearRgb = clamp(mix(float3(rawLuma), linearRgb, 1.55), 0.0, 1.0);

        float luma = dot(linearRgb, lumaWeights);
        float maxCh = max(linearRgb.r, max(linearRgb.g, linearRgb.b));
        float metric = mix(luma, maxCh, 0.35);
        const float knee = 0.09;
        const float headroom = 0.16;
        float highlights = max(0.0, metric - knee);
        float limited = min(metric, knee) + headroom * highlights / (headroom + highlights);
        linearRgb *= limited / max(metric, 0.0001);

        // 正交双路 IGN 三角分布抖动（TPDF, ±1.5 LSB），消除平行条纹并抹平暗部 8-bit 色阶断层
        float2 pixel = floor(position);
        float ign1 = fract(52.9829189 * fract(dot(pixel, float2(0.06711056, 0.00583715))));
        float ign2 = fract(52.9829189 * fract(dot(pixel, float2(0.00583715, 0.06711056))));
        float dither = (ign1 - ign2) * (1.5 / 255.0);

        float3 outputRgb = (fromLinearSrgb(linearRgb) + tone.y) * (1.0 - tone.x);
        return half4(clamp(outputRgb + dither, 0.0, 1.0), 1.0);
    }
"""
