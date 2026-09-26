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

package com.example.islandlyrics.core.settings.search

import android.content.Context
import android.content.Intent
import com.example.islandlyrics.feature.customsettings.CustomSettingsTab

sealed interface SettingsSearchAction {
    /**
     * 导航至指定设置页面，可选指定目标 Tab 与需要定位高亮的具体设置项 Key。
     */
    data class Navigate(
        val target: SettingsNavigationTarget,
        val tab: CustomSettingsTab? = null,
        val targetItemKey: String? = null
    ) : SettingsSearchAction

    /**
     * 触发主设置页的弹窗交互（如语言选择、权限提示弹窗、备份导出等）。
     */
    data class TriggerDialog(
        val dialog: MainSettingsDialogType
    ) : SettingsSearchAction

    /**
     * 启动外部系统 Intent（如系统通知使用权设置、电池白名单等）。
     */
    data class LaunchIntent(
        val intentBuilder: (Context) -> Intent
    ) : SettingsSearchAction
}

enum class SettingsNavigationTarget {
    CAPSULE_NOTIFICATION,
    APP_UI,
    DESKTOP_LYRICS,
    LOCAL_LYRIC_DIRECTORIES,
    CACHE_MANAGEMENT,
    ONLINE_LYRIC_REMATCH,
    LAST_FM,
    APPLE_MUSIC,
    FAQ,
    COMMUNITY,
    ABOUT,
    DIAGNOSTICS,
    LAB,
    PARSER_RULES
}

enum class MainSettingsDialogType {
    PRIVACY_NOTIFICATION_LISTENER,
    HIDE_LAUNCHER,
    BACKUP_EXPORT,
    BACKUP_IMPORT,
    LANGUAGE_PICKER
}
