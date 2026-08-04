/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.overlay.superisland.render

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Bundle
import com.example.islandlyrics.core.logging.AppLogger
import com.example.islandlyrics.ui.overlay.model.UIState
import com.example.islandlyrics.ui.overlay.superisland.cache.SuperIslandIconCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandPreferencesCache
import com.example.islandlyrics.ui.overlay.superisland.config.SuperIslandSecondaryTextMode
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

            val useDualLineStyle = preferences.notificationStyle == "advanced_lyrics_dual"
            val dualLineText = SuperIslandDualLineTextResolver.resolve(
                state,
                listOf(
                    SuperIslandSecondaryTextMode.from(preferences.dualLineMode)
                        ?: SuperIslandSecondaryTextMode.TRANSLATION
                )
            )
            val customLightViews = if (useDualLineStyle) {
                remoteViewsFactory.createDualLineExpand(
                    state = state,
                    dualLineText = dualLineText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = false
                )
            } else {
                remoteViewsFactory.createExpand(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = false,
                    albumArt = albumArt,
                    miPlayIntent = miPlayIntent
                )
            }
            val customDarkViews = if (useDualLineStyle) {
                remoteViewsFactory.createDualLineExpand(
                    state = state,
                    dualLineText = dualLineText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = true
                )
            } else {
                remoteViewsFactory.createExpand(
                    state = state,
                    subText = subText,
                    progressPercent = progressPercent,
                    progressBarColor = progressBarColor,
                    darkMode = true,
                    albumArt = albumArt,
                    miPlayIntent = miPlayIntent
                )
            }
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
                        if (preferences.showProgressBar) {
                            progressInfo {
                                progress = progressPercent
                                colorReach = if (showHighlightColor) hexColor else "#757575"
                                colorUnReach = "#333333"
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

        try {
            val customRoot = JSONObject(customJson)
            val standardRoot = JSONObject(standardJson)
            val island = standardRoot.optJSONObject("param_v2")?.optJSONObject("param_island")
            if (island != null) {
                customRoot.put("param_island", island)
                merged.putString("miui.focus.param.custom", customRoot.toString())
            } else {
                AppLogger.getInstance().w("SuperIsland", "mergeCustomFocus: standard island is null, collapse state may be missing")
            }
        } catch (e: Exception) {
            AppLogger.getInstance().e("SuperIsland", "mergeCustomFocus failed: ${e.message}", e)
        }

        return merged
    }
}
