/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.debug

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.palette.graphics.Palette
import com.example.islandlyrics.R
import top.yukonga.miuix.kmp.basic.Text

private const val HaloColorCount = 4
private const val HaloCycleNanos = 42_000_000_000L

internal fun prepareDebugHaloPalette(cover: Bitmap): List<Color>? {
    if (cover.isRecycled) return null
    val source = if (cover.config == Bitmap.Config.HARDWARE) {
        cover.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        cover
    }
    try {
        val swatches = Palette.from(source)
            .clearFilters()
            .maximumColorCount(16) // 扩大桶数，避免鲜艳色与黑白背景在粗桶里平均发灰
            .generate()
            .swatches
        if (swatches.isEmpty()) return null

        val maxPop = swatches.maxOf { it.population }.coerceAtLeast(1).toFloat()
        data class Candidate(val lab: Color, val chroma: Float, val score: Float)

        val candidates = swatches.map { swatch ->
            val lab = Color(swatch.rgb).convert(ColorSpaces.Oklab)
            val l = lab.red
            val chroma = kotlin.math.hypot(lab.green, lab.blue)
            val popFactor = kotlin.math.sqrt(swatch.population / maxPop)
            // 降低极端纯黑/纯白权重，兼顾主面积色与点缀色
            val lightnessFactor = when {
                l < 0.16f -> 0.25f
                l > 0.90f -> 0.35f
                else -> 1.0f
            }
            val score = popFactor * (0.35f + chroma * 4.0f) * lightnessFactor
            Candidate(lab, chroma, score)
        }.sortedByDescending { it.score }

        // 贪心选出 4 个在 OKLab 空间有一定差异的颜色，避免全选成近似色
        val picked = mutableListOf<Candidate>()
        for (c in candidates) {
            if (picked.size >= HaloColorCount) break
            val distinct = picked.all { prev ->
                val dl = c.lab.red - prev.lab.red
                val da = c.lab.green - prev.lab.green
                val db = c.lab.blue - prev.lab.blue
                dl * dl + da * da + db * db > 0.012f
            }
            if (distinct) picked += c
        }
        // 若封面本身偏单色导致不足 4 个，按得分补齐
        for (c in candidates) {
            if (picked.size >= HaloColorCount) break
            if (c !in picked) picked += c
        }

        return List(HaloColorCount) { index ->
            val raw = picked[index % picked.size].lab
            normalizeOklabSwatch(raw)
        }
    } finally {
        if (source !== cover) source.recycle()
    }
}

private fun normalizeOklabSwatch(lab: Color): Color {
    val oldL = lab.red.coerceAtLeast(0.0001f)
    // 同时约束亮度上下限：压住高光防发白，托起纯黑防左上角黑洞
    val targetL = oldL.coerceIn(0.28f, 0.62f)
    // 压暗时等比收色度防出 sRGB 色域；提亮时不强拉色度防噪点放大
    val chromaScale = if (targetL < oldL) (targetL / oldL) else 1.0f
    // 温和补偿多色混合带来的饱和度损耗
    val boost = 1.15f
    val a = (lab.green * chromaScale * boost).coerceIn(-0.22f, 0.22f)
    val b = (lab.blue * chromaScale * boost).coerceIn(-0.22f, 0.22f)
    return Color(targetL, a, b, 1f, ColorSpaces.Oklab)
}

@Composable
internal fun DebugOklabHalo(colors: List<Color>, modifier: Modifier) {
    val hardwareAccelerated = LocalView.current.isHardwareAccelerated
    val shader = remember(colors, hardwareAccelerated) {
        if (!hardwareAccelerated) return@remember null
        try {
            RuntimeShader(OklabHaloShader).apply {
                colors.forEachIndexed { index, color ->
                    // Scaling Lab together dims the swatch while preserving its linear RGB ratios.
                    val tone = minOf(1f, 0.68f / maxOf(color.red, 0.0001f))
                    setFloatUniform("uColor$index", color.red * tone, color.green * tone, color.blue * tone)
                }
            }
        } catch (error: IllegalArgumentException) {
            Log.e("BackgroundBlurDebug", "Could not initialize OKLab halo shader", error)
            null
        }
    }
    if (shader == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.debug_background_blur_shader_unavailable))
        }
        return
    }
    val time = rememberDebugBlurTime(HaloCycleNanos)
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uPhase", 6.2831853f * (time.longValue / HaloCycleNanos.toFloat()))
        drawRect(brush)
    }
}

private const val OklabHaloShader = """
    uniform float3 uColor0;
    uniform float3 uColor1;
    uniform float3 uColor2;
    uniform float3 uColor3;
    uniform float2 uSize;
    uniform float uPhase;

    float gaussian(float2 point, float2 center, float sigma) {
        float2 delta = point - center;
        return exp(-0.5 * dot(delta, delta) / (sigma * sigma));
    }

    float3 oklabToLinearSrgb(float3 lab) {
        float3 lms = lab.x + lab.y * float3(0.3963377774, -0.1055613458, -0.0894841775)
                          + lab.z * float3(0.2158037573, -0.0638541728, -1.2914855480);
        lms = lms * lms * lms;
        return float3(
            dot(lms, float3(4.0767416621, -3.3077115913, 0.2309699292)),
            dot(lms, float3(-1.2684380046, 2.6097574011, -0.3413193965)),
            dot(lms, float3(-0.0041960863, -0.7034186147, 1.7076147010))
        );
    }

    half4 main(float2 position) {
        // 用归一化 UV 坐标计算距离，保证 1:1 预览、20:9 竖屏和 16:10 横屏光斑覆盖比例一致
        float2 uv = position / max(uSize, float2(1.0));

        // 轻度低频流场扭曲（保持 uPhase 的 2pi 周期无缝循环）
        float2 p = uv + float2(
            0.065 * sin(uv.y * 3.14159 + uPhase) + 0.03 * cos(uv.x * 2.6 - 2.0 * uPhase),
            0.065 * cos(uv.x * 3.14159 - uPhase) + 0.03 * sin(uv.y * 2.6 + 2.0 * uPhase)
        );

        // 整数倍谐波轨道：既打破 4 点同步转圈的机械感，又保证 42s 完美闭环
        float2 c0 = float2(0.22 + 0.22 * sin(uPhase),            0.22 + 0.18 * cos(2.0 * uPhase));
        float2 c1 = float2(0.78 + 0.20 * cos(uPhase + 1.2),      0.26 + 0.22 * sin(uPhase + 0.5));
        float2 c2 = float2(0.24 + 0.22 * cos(2.0 * uPhase + 2.1),0.78 + 0.18 * sin(uPhase + 1.8));
        float2 c3 = float2(0.76 + 0.18 * sin(uPhase + 3.4),      0.74 + 0.22 * cos(uPhase + 2.7));

        // 放宽 sigma 至 0.55~0.62，消除独立圆斑感，让色域大面积融通
        float w0 = gaussian(p, c0, 0.58);
        float w1 = gaussian(p, c1, 0.54);
        float w2 = gaussian(p, c2, 0.60);
        float w3 = gaussian(p, c3, 0.55);

        // 用 4 色均值压暗作为带色相倾向的基底，替代原本发灰的 float3(0.19, 0.0, 0.0)
        float3 avg = (uColor0 + uColor1 + uColor2 + uColor3) * 0.25;
        float3 base = float3(min(avg.x * 0.72, 0.28), avg.y * 0.85, avg.z * 0.85);

        float3 lab = (base * 0.18 + uColor0 * w0 + uColor1 * w1 + uColor2 * w2 + uColor3 * w3)
                   / (0.18 + w0 + w1 + w2 + w3);

        float3 srgb = fromLinearSrgb(clamp(oklabToLinearSrgb(lab), 0.0, 1.0));
        // 微量交错梯度噪声消除 8-bit 渐变色阶断层
        float ign = fract(52.9829189 * fract(dot(position, float2(0.06711056, 0.00583715))));
        srgb += (ign - 0.5) / 255.0;

        return half4(clamp(srgb, 0.0, 1.0), 1.0);
    }

"""
