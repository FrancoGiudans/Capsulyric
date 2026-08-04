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
 *  *
 *  *
 */

package com.example.islandlyrics.ui.overlay.superisland.config

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.islandlyrics.core.settings.AppPreferences

/**
 * 旧版本「通知按键」偏好迁移到新版「播放按键布局 + 显示进度条」推导模型：
 * - 旧「通知按键 = 关闭 / miplay」→ 播放按键布局 = 无按键，显示进度条 = 开（效果不变）
 * - 旧「通知按键 = 媒体控制」→ 保持原有播放按键布局（两键/三键），显示进度条 = 开
 * - 旧单行「次要文本模式」→ 迁移为新的多选优先级列表（保持原选项）
 */
internal object SuperIslandPreferenceMigration {
    private const val KEY_MIGRATED = "super_island_notification_logic_v2_migrated"

    fun migrate(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val actionStyle = prefs.getString(AppPreferences.Keys.NOTIFICATION_ACTIONS_STYLE, "disabled")
            ?: "disabled"
        prefs.edit {
            if (actionStyle == "disabled" || actionStyle == "miplay") {
                putString(AppPreferences.Keys.SUPER_ISLAND_MEDIA_BUTTON_LAYOUT, "no_button")
                putBoolean(AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR, true)
            } else {
                // media_controls：保留原按键布局
                putBoolean(AppPreferences.Keys.SUPER_ISLAND_SHOW_PROGRESS_BAR, true)
            }

            if (!prefs.contains(AppPreferences.Keys.SUPER_ISLAND_SECONDARY_TEXT_MODES)) {
                // 旧用户设置过「第二行内容」则沿用；从未设置过则默认下一句歌词
                val legacy = prefs.getString(AppPreferences.Keys.SUPER_ISLAND_DUAL_LINE_MODE, null)
                val mode = SuperIslandSecondaryTextMode.from(legacy)
                    ?: SuperIslandSecondaryTextMode.NEXT_LYRIC
                putString(
                    AppPreferences.Keys.SUPER_ISLAND_SECONDARY_TEXT_MODES,
                    mode.preferenceValue
                )
            }
            putBoolean(KEY_MIGRATED, true)
        }
    }
}
