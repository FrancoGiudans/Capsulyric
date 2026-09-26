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
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import com.example.islandlyrics.R
import com.example.islandlyrics.core.network.OfflineModeManager
import com.example.islandlyrics.core.platform.RomUtils
import com.example.islandlyrics.core.settings.LabFeatureManager
import com.example.islandlyrics.feature.customsettings.CustomSettingsTab
import com.example.islandlyrics.feature.parserrule.ParserRuleEditorActivity
import com.example.islandlyrics.lyrics.state.LyricRepository

object SettingsSearchRegistry {

    fun getAllItems(): List<SettingsSearchItem> = listOf(
        // ═════════════════════════════════════════════════════════════════════
        // 一级设置项 (Main Settings Top-Level)
        // ═════════════════════════════════════════════════════════════════════
        SettingsSearchItem(
            id = "page_capsule_notification",
            titleRes = R.string.settings_capsule_notification,
            breadcrumbResList = listOf(R.string.settings_core_header),
            keywords = listOf("灵动岛", "胶囊", "超级岛", "状态栏通知", "ldd", "jn"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.CAPSULE_NOTIFICATION)
        ),
        SettingsSearchItem(
            id = "page_desktop_lyrics",
            titleRes = R.string.settings_floating_lyrics,
            breadcrumbResList = listOf(R.string.settings_core_header),
            keywords = listOf("桌面歌词", "悬浮歌词", "桌面悬浮窗", "悬浮窗歌词", "zm", "xf"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "page_parser_rules",
            titleRes = R.string.tab_rules,
            breadcrumbResList = listOf(R.string.settings_core_header),
            keywords = listOf("规则", "解析规则", "播放器规则", "通知规则", "gz", "jxgz"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.PARSER_RULES)
        ),
        SettingsSearchItem(
            id = "page_personalization",
            titleRes = R.string.page_title_personalization,
            breadcrumbResList = listOf(R.string.settings_general_header),
            keywords = listOf("个性化", "界面", "主题", "深色模式", "gxh"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.APP_UI)
        ),
        SettingsSearchItem(
            id = "pref_language",
            titleRes = R.string.settings_language,
            breadcrumbResList = listOf(R.string.settings_general_header),
            keywords = listOf("语言", "多语言", "英文", "中文", "繁体", "日语", "language"),
            action = SettingsSearchAction.TriggerDialog(MainSettingsDialogType.LANGUAGE_PICKER)
        ),
        SettingsSearchItem(
            id = "pref_recommend_media_app",
            titleRes = R.string.settings_recommend_media_app,
            summaryRes = R.string.settings_recommend_media_app_desc,
            breadcrumbResList = listOf(R.string.settings_general_header),
            keywords = listOf("推荐媒体应用", "推荐播放器", "自动推荐"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                targetItemKey = "key_recommend_media_app"
            )
        ),
        SettingsSearchItem(
            id = "pref_new_playing_app_alert",
            titleRes = R.string.settings_new_playing_app_alert,
            summaryRes = R.string.settings_new_playing_app_alert_desc,
            breadcrumbResList = listOf(R.string.settings_general_header),
            keywords = listOf("发现新播放应用提示", "新应用提醒", "未配置应用"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                targetItemKey = "key_new_playing_app_alert"
            )
        ),
        SettingsSearchItem(
            id = "pref_hide_recents",
            titleRes = R.string.settings_hide_recents,
            summaryRes = R.string.settings_hide_recents_desc,
            breadcrumbResList = listOf(R.string.settings_general_header),
            keywords = listOf("最近任务中隐藏", "后台隐藏", "多任务卡片"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                targetItemKey = "key_hide_recents"
            )
        ),
        SettingsSearchItem(
            id = "pref_hide_launcher",
            titleRes = R.string.settings_hide_launcher,
            summaryRes = R.string.settings_hide_launcher_desc,
            breadcrumbResList = listOf(R.string.settings_general_header),
            keywords = listOf("隐藏桌面图标", "桌面图标隐藏", "桌面无图标"),
            action = SettingsSearchAction.TriggerDialog(MainSettingsDialogType.HIDE_LAUNCHER)
        ),
        SettingsSearchItem(
            id = "perm_read_notif",
            titleRes = R.string.perm_read_notif,
            summaryRes = R.string.perm_read_notif_desc,
            breadcrumbResList = listOf(R.string.settings_permissions_header),
            keywords = listOf("通知读取权限", "通知监听", "权限", "状态栏通知权限"),
            action = SettingsSearchAction.TriggerDialog(MainSettingsDialogType.PRIVACY_NOTIFICATION_LISTENER)
        ),
        SettingsSearchItem(
            id = "perm_post_notif",
            titleRes = R.string.perm_post_notif,
            summaryRes = R.string.perm_post_notif_desc,
            breadcrumbResList = listOf(R.string.settings_permissions_header),
            keywords = listOf("发送通知权限", "通知权限", "POST_NOTIFICATIONS"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", ctx.packageName, null)
                }
            }
        ),
        SettingsSearchItem(
            id = "perm_battery",
            titleRes = R.string.settings_general_battery,
            summaryRes = R.string.summary_optimize_battery,
            breadcrumbResList = listOf(R.string.settings_permissions_header),
            keywords = listOf("忽略电池优化", "省电策略", "后台保活", "电池无限制"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${ctx.packageName}".toUri()
                }
            }
        ),
        SettingsSearchItem(
            id = "page_local_lyrics",
            titleRes = R.string.settings_local_lyrics_title,
            summaryRes = R.string.settings_local_lyrics_directories,
            breadcrumbResList = listOf(R.string.settings_lyric_tools_header),
            keywords = listOf("本地歌词", "歌词目录", "本地lrc", "文件夹扫描"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.LOCAL_LYRIC_DIRECTORIES)
        ),
        SettingsSearchItem(
            id = "page_cache_management",
            titleRes = R.string.title_cache_management,
            summaryRes = R.string.settings_cache_management_desc,
            breadcrumbResList = listOf(R.string.settings_lyric_tools_header),
            keywords = listOf("缓存管理", "歌词缓存", "清理缓存", "清除全部数据", "导出缓存"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.CACHE_MANAGEMENT)
        ),
        SettingsSearchItem(
            id = "page_online_rematch",
            titleRes = R.string.online_lyric_rematch_title,
            summaryRes = R.string.online_lyric_rematch_settings_desc,
            breadcrumbResList = listOf(R.string.settings_lyric_tools_header),
            keywords = listOf("在线歌词重匹配", "重新匹配歌词", "歌词源调试"),
            isVisible = { !OfflineModeManager.isEnabled(it) },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.ONLINE_LYRIC_REMATCH)
        ),
        SettingsSearchItem(
            id = "page_lastfm",
            titleRes = R.string.lastfm_title,
            summaryRes = R.string.lastfm_settings_summary,
            breadcrumbResList = listOf(R.string.settings_lyric_tools_header),
            keywords = listOf("Last.fm", "记录播放", "Scrobble", "音乐打卡"),
            isVisible = { !OfflineModeManager.isEnabled(it) },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.LAST_FM)
        ),
        SettingsSearchItem(
            id = "page_apple_music",
            titleRes = R.string.apple_music_settings_title,
            summaryRes = R.string.apple_music_settings_summary,
            breadcrumbResList = listOf(R.string.settings_lyric_tools_header),
            keywords = listOf("Apple Music", "苹果音乐", "网易云歌词同步"),
            isVisible = { !OfflineModeManager.isEnabled(it) },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.APPLE_MUSIC)
        ),
        SettingsSearchItem(
            id = "dialog_backup_export",
            titleRes = R.string.settings_backup_export,
            summaryRes = R.string.settings_backup_export_desc,
            breadcrumbResList = listOf(R.string.settings_backup_header),
            keywords = listOf("备份设置", "导出配置", "导出设置", "备份"),
            action = SettingsSearchAction.TriggerDialog(MainSettingsDialogType.BACKUP_EXPORT)
        ),
        SettingsSearchItem(
            id = "dialog_backup_import",
            titleRes = R.string.settings_backup_import,
            summaryRes = R.string.settings_backup_import_desc,
            breadcrumbResList = listOf(R.string.settings_backup_header),
            keywords = listOf("恢复设置", "导入配置", "导入设置", "还原"),
            action = SettingsSearchAction.TriggerDialog(MainSettingsDialogType.BACKUP_IMPORT)
        ),
        SettingsSearchItem(
            id = "page_faq",
            titleRes = R.string.faq_title,
            summaryRes = R.string.summary_faq,
            breadcrumbResList = listOf(R.string.settings_help_about_header),
            keywords = listOf("常见问题", "FAQ", "使用说明", "帮助文档"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.FAQ)
        ),
        SettingsSearchItem(
            id = "page_community",
            titleRes = R.string.settings_community_header,
            breadcrumbResList = listOf(R.string.settings_help_about_header),
            keywords = listOf("社区与反馈", "反馈", "群组", "公告"),
            isVisible = { !OfflineModeManager.isEnabled(it) },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.COMMUNITY)
        ),
        SettingsSearchItem(
            id = "page_about",
            titleRes = R.string.about_title,
            summaryRes = R.string.settings_about_capsulyric,
            breadcrumbResList = listOf(R.string.settings_help_about_header),
            keywords = listOf("关于", "检查更新", "版本号", "更新日志", "GitHub"),
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.ABOUT)
        ),
        SettingsSearchItem(
            id = "page_diagnostics",
            titleRes = R.string.title_diagnostics,
            summaryRes = R.string.summary_diagnostics,
            breadcrumbResList = listOf(R.string.settings_developer_mode_header),
            keywords = listOf("诊断与日志", "日志控制台", "抓取日志", "debug"),
            isVisible = { LyricRepository.getInstance().devModeEnabled.value == true },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DIAGNOSTICS)
        ),
        SettingsSearchItem(
            id = "page_lab",
            titleRes = R.string.title_lab,
            summaryRes = R.string.diag_lab_page_desc,
            breadcrumbResList = listOf(R.string.settings_developer_mode_header),
            keywords = listOf("实验室功能", "实验性特性", "高级实验"),
            isVisible = { LyricRepository.getInstance().devModeEnabled.value == true },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.LAB)
        ),

        // ═════════════════════════════════════════════════════════════════════
        // 二级设置项：个性化 / App 界面 (Personalization / App UI Tab)
        // ═════════════════════════════════════════════════════════════════════
        SettingsSearchItem(
            id = "pref_app_ui_style",
            titleRes = R.string.settings_app_ui_style,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("UI风格", "Material", "Miuix风格", "小米风格"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_app_ui_style"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_follow_system",
            titleRes = R.string.settings_theme_follow_system,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("跟随系统", "跟随系统深色", "自动深色模式"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_follow_system"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_dark_mode",
            titleRes = R.string.settings_theme_dark_mode,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("深色模式", "夜间模式", "黑夜模式", "暗色", "dark mode"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_dark_mode"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_pure_black",
            titleRes = R.string.settings_theme_pure_black,
            summaryRes = R.string.settings_theme_pure_black_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("纯黑", "AMOLED", "深色纯黑", "真黑", "OLED"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_dark_mode"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_dynamic_color",
            titleRes = R.string.settings_theme_dynamic_color,
            summaryRes = R.string.settings_theme_dynamic_color_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("动态色彩", "Monet", "壁纸取色", "Material You", "动态主题色"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_dynamic_color"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_color_source",
            titleRes = R.string.settings_theme_color_source,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("主题色来源", "配色方案", "主题颜色"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_color_source"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_custom_color",
            titleRes = R.string.settings_theme_custom_color,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("自定义主题色", "选色板", "色盘", "主题颜色修改"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_custom_color"
            )
        ),
        SettingsSearchItem(
            id = "pref_theme_custom_color_global_tint",
            titleRes = R.string.settings_theme_custom_color_global_tint,
            summaryRes = R.string.settings_theme_custom_color_global_tint_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("全局着色", "主题色覆盖", "全局色调"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_theme_custom_color_global_tint"
            )
        ),
        SettingsSearchItem(
            id = "pref_card_blur",
            titleRes = R.string.settings_card_blur,
            summaryRes = R.string.settings_card_blur_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("高级材质", "毛玻璃", "卡片模糊", "透明", "模糊效果", "mbl", "blur"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_card_blur"
            )
        ),
        SettingsSearchItem(
            id = "pref_edge_highlight_texture",
            titleRes = R.string.settings_edge_highlight_texture_title,
            summaryRes = R.string.settings_edge_highlight_texture_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("边缘高光", "高光纹理", "质感高光", "边缘反光"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_edge_highlight_texture"
            )
        ),
        SettingsSearchItem(
            id = "pref_navigation_bar_style",
            titleRes = R.string.settings_navigation_bar_style,
            summaryRes = R.string.settings_navigation_bar_style_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("导航栏样式", "浮动药丸", "药丸导航栏", "悬浮导航", "水滴导航"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_navigation_bar_style"
            )
        ),
        SettingsSearchItem(
            id = "pref_predictive_back",
            titleRes = R.string.settings_predictive_back,
            summaryRes = R.string.settings_predictive_back_desc,
            breadcrumbResList = listOf(R.string.page_title_personalization, R.string.tab_app_ui),
            keywords = listOf("预测性返回", "返回手势动画", "全面屏手势动画", "返回动画"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.APP_UI,
                tab = CustomSettingsTab.APP_UI,
                targetItemKey = "key_predictive_back"
            )
        ),

        // ═════════════════════════════════════════════════════════════════════
        // 二级设置项：灵动岛胶囊 (Capsule Tab)
        // ═════════════════════════════════════════════════════════════════════
        SettingsSearchItem(
            id = "pref_disable_scrolling",
            titleRes = R.string.settings_disable_scrolling,
            summaryRes = R.string.settings_disable_scrolling_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("禁用歌词滚动", "单行静止", "滚动歌词", "强制滚动", "不滚动"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_disable_scrolling"
            )
        ),
        SettingsSearchItem(
            id = "pref_capsule_mode",
            titleRes = R.string.settings_capsule_mode,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("胶囊渲染模式", "渲染模式", "状态栏胶囊", "超级岛胶囊"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_capsule_mode"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_lyric_mode",
            titleRes = R.string.settings_super_island_lyric_mode,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("歌词显示模式", "标准模式", "长歌词模式", "双行"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_super_island_lyric_mode"
            )
        ),
        SettingsSearchItem(
            id = "pref_lyric_text_display_mode",
            titleRes = R.string.settings_lyric_text_display_mode,
            summaryRes = R.string.settings_lyric_text_display_mode_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("歌词显示模式", "标准歌词", "仅歌名", "音译"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_lyric_text_display_mode"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_dual_line_mode",
            titleRes = R.string.settings_super_island_dual_line_mode,
            summaryRes = R.string.settings_super_island_dual_line_mode_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("双行歌词模式", "双行歌词", "翻译歌词", "音译发音", "下一行歌词", "副歌词"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_super_island_dual_line_mode"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_color_source",
            titleRes = R.string.settings_super_island_color_source,
            summaryRes = R.string.settings_super_island_color_source_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("胶囊歌词颜色", "颜色来源", "专辑封面取色", "胶囊自定义颜色"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_super_island_color_source"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_colorize",
            titleRes = R.string.settings_super_island_colorize,
            summaryRes = R.string.settings_super_island_colorize_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("胶囊歌词着色", "颜色来源", "专辑封面取色", "胶囊自定义颜色", "智能取色"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_super_island_colorize"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_show_left_cover",
            titleRes = R.string.settings_super_island_standard_show_left_cover,
            summaryRes = R.string.settings_super_island_standard_show_left_cover_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("显示左侧封面", "封面小图标", "专辑封面显示"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_super_island_show_left_cover"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_share",
            titleRes = R.string.settings_super_island_share,
            summaryRes = R.string.settings_super_island_share_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_capsule),
            keywords = listOf("歌词海报分享", "灵动岛分享", "分享卡片", "一键分享"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.CAPSULE,
                targetItemKey = "key_super_island_share"
            )
        ),

        // ═════════════════════════════════════════════════════════════════════
        // 二级设置项：状态栏通知 (Notification Tab)
        // ═════════════════════════════════════════════════════════════════════
        SettingsSearchItem(
            id = "pref_super_island_notification_style",
            titleRes = R.string.settings_super_island_notification_style,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("超级岛通知样式", "通知样式", "高级双行歌词样式", "模板样式", "紧凑样式"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_super_island_notification_style"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_media_button_layout",
            titleRes = R.string.settings_super_island_media_button_layout,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("播放按键布局", "双按键", "三按键", "无按键", "快捷控制按键"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_super_island_media_button_layout"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_show_progress_bar",
            titleRes = R.string.settings_super_island_show_progress_bar,
            summaryRes = R.string.settings_super_island_show_progress_bar_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("通知进度条", "显示进度条", "音乐播放进度"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_super_island_show_progress_bar"
            )
        ),
        SettingsSearchItem(
            id = "pref_notification_actions",
            titleRes = R.string.settings_notification_actions,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("通知快捷操作按键", "上一首", "下一首", "MiPlay"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_notification_actions"
            )
        ),
        SettingsSearchItem(
            id = "pref_click_action",
            titleRes = R.string.settings_click_action_title,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("点击通知行为", "打开播放器", "打开音乐App", "通知跳转"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_click_action"
            )
        ),
        SettingsSearchItem(
            id = "pref_dismiss_delay",
            titleRes = R.string.settings_dismiss_delay_title,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("延时消失", "通知保留时长", "暂停保留时长", "延迟关闭通知"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_dismiss_delay"
            )
        ),
        SettingsSearchItem(
            id = "pref_lock_screen_hide_notification",
            titleRes = R.string.settings_lock_screen_hide_notification,
            summaryRes = R.string.settings_lock_screen_hide_notification_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("锁屏隐藏通知", "锁屏隐私", "锁屏不显示"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_lock_screen_hide_notification"
            )
        ),
        SettingsSearchItem(
            id = "pref_block_xmsf",
            titleRes = R.string.settings_block_xmsf,
            summaryRes = R.string.settings_block_xmsf_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("绕过小米超级岛白名单", "XMSF", "白名单绕过", "Shizuku", "断网绕过"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_block_xmsf"
            )
        ),
        SettingsSearchItem(
            id = "pref_super_island_template2_pic_source",
            titleRes = R.string.settings_super_island_template2_pic_source,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("通知配图来源", "专辑封面", "应用图标", "自定义图片"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_super_island_template2_pic_source"
            )
        ),
        SettingsSearchItem(
            id = "pref_progress_color",
            titleRes = R.string.settings_progress_color,
            summaryRes = R.string.settings_progress_color_desc,
            breadcrumbResList = listOf(R.string.settings_capsule_notification, R.string.tab_notification),
            keywords = listOf("彩色进度条", "封面取色进度条", "进度条颜色"),
            action = SettingsSearchAction.Navigate(
                target = SettingsNavigationTarget.CAPSULE_NOTIFICATION,
                tab = CustomSettingsTab.NOTIFICATION,
                targetItemKey = "key_progress_color"
            )
        ),

        // ═════════════════════════════════════════════════════════════════════
        // 二级设置项：桌面歌词 (Desktop Lyrics Tab)
        // ═════════════════════════════════════════════════════════════════════
        SettingsSearchItem(
            id = "pref_desktop_lyrics_show",
            titleRes = R.string.settings_floating_lyrics_show,
            summaryRes = R.string.settings_floating_lyrics_show_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("显示桌面歌词", "开启桌面歌词", "悬浮歌词开关"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_show_album_art",
            titleRes = R.string.settings_floating_show_album_art,
            summaryRes = R.string.settings_floating_show_album_art_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("桌面歌词封面", "显示专辑封面"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_text_stroke",
            titleRes = R.string.settings_floating_text_stroke,
            summaryRes = R.string.settings_floating_text_stroke_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("文字描边", "歌词描边", "阴影描边"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_text_background",
            titleRes = R.string.settings_floating_text_background,
            summaryRes = R.string.settings_floating_text_background_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("文字底色", "歌词背景", "悬浮窗背景"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_show_second_line",
            titleRes = R.string.settings_floating_show_second_line,
            summaryRes = R.string.settings_floating_show_second_line_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("双行歌词", "下一行歌词", "副歌词", "桌面双行"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_word_highlight",
            titleRes = R.string.settings_floating_word_highlight,
            summaryRes = R.string.settings_floating_word_highlight_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("逐字高亮", "逐字歌词", "卡拉OK", "逐字渲染"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_follow_album_color",
            titleRes = R.string.settings_floating_follow_album_color,
            summaryRes = R.string.settings_floating_follow_album_color_desc,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("跟随封面颜色", "桌面歌词取色", "桌面歌词颜色"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),
        SettingsSearchItem(
            id = "pref_desktop_lyrics_text_size",
            titleRes = R.string.settings_floating_text_size,
            breadcrumbResList = listOf(R.string.settings_core_header, R.string.settings_floating_lyrics),
            keywords = listOf("字号", "字体大小", "桌面歌词大小", "歌词大小"),
            isVisible = { context ->
                val prefs = context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
                LabFeatureManager.isFloatingLyricsEnabled(prefs)
            },
            action = SettingsSearchAction.Navigate(SettingsNavigationTarget.DESKTOP_LYRICS)
        ),

        // ═════════════════════════════════════════════════════════════════════
        // 播放器解析规则 (Player Parser Rules)
        // ═════════════════════════════════════════════════════════════════════
        SettingsSearchItem(
            id = "rule_apple_music",
            titleRes = R.string.settings_link_apple_music_rule,
            breadcrumbResList = listOf(R.string.tab_rules),
            keywords = listOf("Apple Music", "苹果音乐规则", "am"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(ctx, ParserRuleEditorActivity::class.java).apply {
                    putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, "com.apple.android.music")
                    putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, "Apple Music")
                }
            }
        ),
        SettingsSearchItem(
            id = "rule_qq_music",
            titleRes = R.string.provider_qq_music,
            summaryRes = R.string.settings_link_parser_rules,
            breadcrumbResList = listOf(R.string.tab_rules),
            keywords = listOf("QQ音乐", "qq music", "qq", "企鹅音乐", "解析规则"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(ctx, ParserRuleEditorActivity::class.java).apply {
                    putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, "com.tencent.qqmusic")
                    putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, "QQ Music")
                }
            }
        ),
        SettingsSearchItem(
            id = "rule_netease_music",
            titleRes = R.string.provider_netease_music,
            summaryRes = R.string.settings_link_parser_rules,
            breadcrumbResList = listOf(R.string.tab_rules),
            keywords = listOf("网易云音乐", "云音乐", "163", "wyy", "netease", "解析规则"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(ctx, ParserRuleEditorActivity::class.java).apply {
                    putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, "com.netease.cloudmusic")
                    putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, "NetEase Cloud Music")
                }
            }
        ),
        SettingsSearchItem(
            id = "rule_kugou_music",
            titleRes = R.string.provider_kugou_music,
            summaryRes = R.string.settings_link_parser_rules,
            breadcrumbResList = listOf(R.string.tab_rules),
            keywords = listOf("酷狗音乐", "kugou", "kg", "解析规则"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(ctx, ParserRuleEditorActivity::class.java).apply {
                    putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, "com.kugou.android")
                    putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, "KuGou Music")
                }
            }
        ),
        SettingsSearchItem(
            id = "rule_soda_music",
            titleRes = R.string.provider_soda_music,
            summaryRes = R.string.settings_link_parser_rules,
            breadcrumbResList = listOf(R.string.tab_rules),
            keywords = listOf("汽水音乐", "抖音音乐", "luna", "soda", "解析规则"),
            action = SettingsSearchAction.LaunchIntent { ctx ->
                Intent(ctx, ParserRuleEditorActivity::class.java).apply {
                    putExtra(ParserRuleEditorActivity.EXTRA_PACKAGE_NAME, "com.luna.music")
                    putExtra(ParserRuleEditorActivity.EXTRA_SUGGESTED_NAME, "Soda Music")
                }
            }
        )
    )
}
