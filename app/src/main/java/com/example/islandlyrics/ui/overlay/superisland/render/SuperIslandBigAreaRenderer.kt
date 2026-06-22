package com.example.islandlyrics.ui.overlay.superisland.render

import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricLayout
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandLyricRenderConfig
import com.xzakota.hyper.notification.island.model.BigIslandArea
import com.xzakota.hyper.notification.island.model.TextInfo

internal object SuperIslandBigAreaRenderer {
    fun BigIslandArea.applyLyrics(
        config: SuperIslandLyricRenderConfig,
        preferMetadataLayout: Boolean,
        isTimingGapPlaceholder: Boolean,
        fullLyric: String,
        displayLyric: String,
        titleWithArtist: String,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String = "",
        artist: String = ""
    ) {
        if (config.lyricMode == "full") {
            applyFullMode(
                config = config,
                preferMetadataLayout = preferMetadataLayout,
                isTimingGapPlaceholder = isTimingGapPlaceholder,
                fullLyric = fullLyric,
                displayLyric = displayLyric,
                islandKey = islandKey,
                showHighlightColor = showHighlightColor,
                title = title,
                artist = artist
            )
            return
        }

        val showLeftCover = islandKey != null
        if (preferMetadataLayout) {
            applyMetadataLayout(
                config = config,
                showLeftCover = showLeftCover,
                islandKey = islandKey,
                showHighlightColor = showHighlightColor,
                title = title,
                artist = artist
            )
            return
        }

        val leftTitle = SuperIslandLyricLayout.takeByWeight(
            titleWithArtist.ifBlank { "♪" },
            if (showLeftCover) config.leftWithCoverTextWeight else config.leftNoCoverTextWeight
        ).ifEmpty { "♪" }
        val rightLyric = SuperIslandLyricLayout.takeByWeight(
            displayLyric.ifBlank { "♪" },
            config.rightTextWeight
        ).ifEmpty { "♪" }

        applyTwoColumnText(
            showLeftCover = showLeftCover,
            islandKey = islandKey,
            leftText = leftTitle,
            rightText = rightLyric,
            showHighlightColor = showHighlightColor,
            narrowLeftFont = null
        )
    }

    private fun BigIslandArea.applyFullMode(
        config: SuperIslandLyricRenderConfig,
        preferMetadataLayout: Boolean,
        isTimingGapPlaceholder: Boolean,
        fullLyric: String,
        displayLyric: String,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String,
        artist: String
    ) {
        val resolvedLyric = sequenceOf(fullLyric, displayLyric)
            .firstOrNull { !SuperIslandTextResolver.isPlaceholder(it) }
            .orEmpty()
        val hasLyric = resolvedLyric.isNotBlank()
        val showLeftCover = config.fullLyricShowLeftCover && islandKey != null

        val shouldShowMetadataFallback = preferMetadataLayout || (!hasLyric && !isTimingGapPlaceholder)
        if (shouldShowMetadataFallback) {
            applyMetadataLayout(
                config = config,
                showLeftCover = showLeftCover,
                islandKey = islandKey,
                showHighlightColor = showHighlightColor,
                title = title,
                artist = artist
            )
            return
        }

        val split = SuperIslandLyricLayout.splitFullLyric(
            text = resolvedLyric,
            showLeftCover = showLeftCover,
            leftMaxWeight = if (showLeftCover) {
                config.leftWithCoverTextWeight
            } else {
                config.leftNoCoverTextWeight
            },
            rightMaxWeight = config.rightTextWeight
        )

        applyTwoColumnText(
            showLeftCover = showLeftCover,
            islandKey = islandKey,
            leftText = split.left.ifEmpty { "♪" },
            rightText = split.right.ifEmpty { "♪" },
            showHighlightColor = showHighlightColor,
            narrowLeftFont = false
        )
    }

    private fun BigIslandArea.applyMetadataLayout(
        config: SuperIslandLyricRenderConfig,
        showLeftCover: Boolean,
        islandKey: String?,
        showHighlightColor: Boolean,
        title: String,
        artist: String
    ) {
        val leftText = SuperIslandLyricLayout.takeByWeight(
            title.ifBlank { "♪" },
            if (showLeftCover) config.leftWithCoverTextWeight else config.leftNoCoverTextWeight
        ).ifEmpty { "♪" }
        val rightText = SuperIslandLyricLayout.takeByWeight(
            artist.ifBlank { "♪" },
            config.rightTextWeight
        ).ifEmpty { "♪" }

        applyTwoColumnText(
            showLeftCover = showLeftCover,
            islandKey = islandKey,
            leftText = leftText,
            rightText = rightText,
            showHighlightColor = showHighlightColor,
            narrowLeftFont = false
        )
    }

    private fun BigIslandArea.applyTwoColumnText(
        showLeftCover: Boolean,
        islandKey: String?,
        leftText: String,
        rightText: String,
        showHighlightColor: Boolean,
        narrowLeftFont: Boolean?
    ) {
        imageTextInfoLeft {
            type = 1
            if (showLeftCover) {
                picInfo {
                    type = 1
                    pic = islandKey
                }
            }
            textInfo {
                this.title = leftText
                this.showHighlightColor = showHighlightColor
                if (narrowLeftFont != null) {
                    narrowFont = narrowLeftFont
                }
            }
        }
        this.textInfo = TextInfo().apply {
            this.title = rightText
            this.showHighlightColor = showHighlightColor
            narrowFont = false
        }
    }
}
