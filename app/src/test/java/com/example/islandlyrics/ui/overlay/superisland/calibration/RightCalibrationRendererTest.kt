package com.example.islandlyrics.ui.overlay.superisland.calibration

import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricRenderConfig
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandBigAreaRenderer.applyLyrics
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandBigAreaRenderer.applyDualLineLyrics
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandDualLineText
import com.xzakota.hyper.notification.island.model.BigIslandArea
import org.junit.Assert.*
import org.junit.Test

class RightCalibrationRendererTest {
    private val policy = RightTextWidthPolicy(RightTextCalibration(capacity = 3f)) { it.length * 0.5f }
    private val base = SuperIslandLyricRenderConfig("standard", true, true, 14, 12, 16)

    private fun render(config: SuperIslandLyricRenderConfig, ring: Boolean = false, metadata: Boolean = false) =
        BigIslandArea().apply {
            applyLyrics(config, preferMetadataLayout = metadata, isTimingGapPlaceholder = false,
                fullLyric = "一二三四五六七八九十", displayLyric = "一二三四五六七", titleWithArtist = "歌名 - 歌手",
                islandKey = "cover-key", showHighlightColor = false,
                title = "歌名", artist = "歌手歌手歌手", rightProgressRingEnabled = ring)
        }

    @Test fun standardCalibrationChangesRightTextAndPreservesLeftPayload() {
        val original = render(base)
        val calibrated = render(base.copy(rightTextPolicy = policy))
        assertEquals("一二三", calibrated.textInfo?.title)
        assertEquals(original.imageTextInfoLeft?.textInfo?.title, calibrated.imageTextInfoLeft?.textInfo?.title)
        assertEquals(original.imageTextInfoLeft?.picInfo?.pic, calibrated.imageTextInfoLeft?.picInfo?.pic)
        assertEquals(original.imageTextInfoLeft?.textInfo?.narrowFont, calibrated.imageTextInfoLeft?.textInfo?.narrowFont)
    }

    @Test fun rawPreviewDoesNotGoThroughTheNormalRightTruncationAgain() {
        val sample = "WWWWWWWWWWWWWWWWWWWW"
        val original = render(base)
        val preview = render(base.copy(rightTextPolicy = policy, rightPreviewText = sample))
        assertEquals(sample, preview.textInfo?.title)
        assertEquals(original.imageTextInfoLeft?.textInfo?.title, preview.imageTextInfoLeft?.textInfo?.title)
    }

    @Test fun rightRingUsesTheSameCalibratedText() {
        val result = render(base.copy(rightTextPolicy = policy), ring = true)
        assertEquals("一二三", result.progressTextInfo?.textInfo?.title)
        assertNull(result.textInfo)
    }

    @Test fun ringCalibrationPresentsTheUncutSampleAndVisibleNativeProgress() {
        val sample = "一二三四五六七八九 W1"
        val config = base.copy(rightTextPolicy = policy, rightPreviewText = sample)
        val original = render(base, ring = true)
        val result = BigIslandArea().apply {
            applyLyrics(config, preferMetadataLayout = false, isTimingGapPlaceholder = false,
                fullLyric = "歌词", displayLyric = "歌词", titleWithArtist = "歌名 - 歌手",
                islandKey = "cover-key", showHighlightColor = false,
                rightProgressRingEnabled = true, progressPercent = 50)
        }
        assertEquals(sample, result.progressTextInfo?.textInfo?.title)
        assertEquals(50, result.progressTextInfo?.progressInfo?.progress)
        assertEquals(original.imageTextInfoLeft?.textInfo?.title, result.imageTextInfoLeft?.textInfo?.title)
        assertNull(result.textInfo)
    }

    @Test fun metadataFallbackIsNotSubjectToLyricCalibration() {
        val original = render(base, metadata = true)
        val calibrated = render(base.copy(rightTextPolicy = policy), metadata = true)
        assertEquals(original.textInfo?.title, calibrated.textInfo?.title)
    }

    @Test fun dualLineModeCalibratesOnlySecondaryText() {
        val result = BigIslandArea().apply {
            applyDualLineLyrics(base.copy(rightTextPolicy = policy),
                SuperIslandDualLineText("左侧歌词", "一二三四五六七"), false)
        }
        assertEquals("左侧歌词", result.imageTextInfoLeft?.textInfo?.title)
        assertEquals("一二三", result.textInfo?.title)
    }
}
