package com.example.islandlyrics.ui.overlay.superisland.render

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Bundle
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferencesCache
import com.example.islandlyrics.ui.overlay.superisland.config.toLyricRenderConfig
import com.example.islandlyrics.ui.overlay.superisland.render.SuperIslandBigAreaRenderer.applyLyrics
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplate
import com.xzakota.hyper.notification.focus.template.CustomFocusTemplateV3
import org.json.JSONObject

internal class SuperIslandCustomFocusBuilder(
    private val iconCache: SuperIslandIconCache,
    private val preferences: SuperIslandPreferencesCache,
    private val remoteViewsFactory: SuperIslandRemoteViewsFactory
) {
    fun build(
        state: UIState,
        displayLyric: String,
        subText: String,
        progressPercent: Int,
        hexColor: String,
        showHighlightColor: Boolean,
        progressBarColor: String,
        titleWithArtist: String,
        albumArt: Bitmap?,
        miPlayIntent: PendingIntent?,
        standardExtras: Bundle
    ): Bundle {
        val customExtras = FocusNotification.buildCustomV3 {
            business = "lyric_display"
            isShowNotification = true
            enableFloat = false
            updatable = true
            islandFirstFloat = false
            hideDeco = true
            aodTitle = displayLyric.take(20).ifEmpty { "♪" }
            val avatarKey = iconCache.avatarIcon?.let { createPicture("miui.focus.pic_avatar", it) }
            val appKey = iconCache.appIcon?.let { createPicture("miui.focus.pic_app", it) }
            val islandKey = iconCache.islandIcon?.let { createPicture("miui.focus.pic_island", it) }
            val islandSmallKey = iconCache.islandSmallIcon?.let { createPicture("miui.land.pic_island", it) }
            val shareKey = iconCache.shareIcon?.let { createPicture("miui.focus.pic_share", it) }

            ticker = displayLyric.ifEmpty { subText.ifEmpty { state.title.ifEmpty { "♪" } } }
            tickerPic = appKey ?: islandSmallKey ?: avatarKey

            val customLightViews = remoteViewsFactory.createExpand(
                state = state,
                subText = subText,
                progressPercent = progressPercent,
                progressBarColor = progressBarColor,
                darkMode = false,
                albumArt = albumArt,
                miPlayIntent = miPlayIntent
            )
            val customDarkViews = remoteViewsFactory.createExpand(
                state = state,
                subText = subText,
                progressPercent = progressPercent,
                progressBarColor = progressBarColor,
                darkMode = true,
                albumArt = albumArt,
                miPlayIntent = miPlayIntent
            )
            val tinyViews = remoteViewsFactory.createTiny(
                state = state,
                subText = subText,
                progressPercent = progressPercent,
                progressBarColor = progressBarColor,
                darkMode = true,
                albumArt = albumArt
            )
            createRemoteViews(CustomFocusTemplate.LAYOUT, customLightViews)
            createRemoteViews(CustomFocusTemplate.LAYOUT_NIGHT, customDarkViews)
            createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY, tinyViews)
            createRemoteViews(CustomFocusTemplate.LAYOUT_FLIP_TINY_NIGHT, tinyViews)
            createRemoteViews(CustomFocusTemplateV3.LAYOUT_ISLAND_EXPAND, customDarkViews)

            island {
                islandProperty = 1
                if (showHighlightColor) {
                    highlightColor = hexColor
                }

                bigIslandArea {
                    applyLyrics(
                        config = preferences.toLyricRenderConfig(),
                        preferMetadataLayout = state.preferMetadataLayout,
                        isTimingGapPlaceholder = state.isTimingGapPlaceholder,
                        fullLyric = state.fullLyric,
                        displayLyric = displayLyric,
                        titleWithArtist = titleWithArtist,
                        islandKey = islandKey,
                        showHighlightColor = showHighlightColor,
                        title = state.title,
                        artist = state.artist
                    )
                }

                if (preferences.shareEnabled) {
                    shareData {
                        pic = shareKey
                        title = state.title.ifEmpty { "♪" }
                        content = SuperIslandTextResolver.primaryText(state)
                        this.shareContent = SuperIslandTextResolver.shareContent(state, preferences.shareFormat)
                    }
                }

                smallIslandArea {
                    combinePicInfo {
                        if (islandSmallKey != null) {
                            picInfo {
                                type = 1
                                pic = islandSmallKey
                            }
                        }
                    }
                }
            }
        }
        return mergeCustomFocusWithStandardIsland(customExtras, standardExtras)
    }

    private fun mergeCustomFocusWithStandardIsland(customExtras: Bundle, standardExtras: Bundle): Bundle {
        val merged = Bundle(customExtras)
        val customJson = customExtras.getString("miui.focus.param.custom") ?: return merged
        val standardJson = standardExtras.getString("miui.focus.param") ?: return merged

        runCatching {
            val customRoot = JSONObject(customJson)
            val standardRoot = JSONObject(standardJson)
            val island = standardRoot.optJSONObject("param_v2")?.optJSONObject("param_island")
            if (island != null) {
                customRoot.put("param_island", island)
                merged.putString("miui.focus.param.custom", customRoot.toString())
            }
        }

        return merged
    }
}
