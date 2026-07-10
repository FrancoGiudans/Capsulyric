<!-- 
Push this file with a commit message starting with [release] to trigger a release build.

Commit format examples:
  [release]              → Auto version (e.g. 26.6.Stable_C1234)
  [release]26.6.1        → 26.6.1.Stable_C{commitCount}
  [release]26.6.Preview  → 26.6.Preview_C{commitCount} (pre-release)
  [release]Preview       → {YY}.{M}.Preview_C{commitCount} (pre-release)

The Preview flag below overrides any channel in the commit message.
Set to `true` to force a pre-release build regardless of commit message.
-->

## Release Metadata
- **Preview**: `false`

## 🇨🇳 更新日志
<img width="850" height="349" alt="26.7.2" src="https://github.com/user-attachments/assets/5539649a-f550-4563-8a69-f810445f3b96" />

**注意事项**
- 自 26.7.1.1 版本起，版本号格式移除"Version."前缀，Git Tag 命名规则同步调整

**功能更新**
- 完善桌面歌词功能，新增双行显示、罗马音对照与翻译对照模式
- 新增首页歌词类型多选配置功能
- 引入 Last.fm 播放记录同步功能，支持自定义 Key 输入与单解析规则独立开关
- 支持在线歌词纯音乐标记，标记后自动跳过匹配请求，胶囊视图仅显示播放信息，支持手动撤销
- 完善应用完全离线模式
- 新增 Live Update 字数上限调节功能
- 支持在线歌词重匹配页面组合不同来源的歌词、翻译与罗马音
- 新增 Android 系统级单应用语言偏好设置支持
- 在线歌词检索结果新增类型标识，并自动过滤无效来源，仅展示可用内容

**体验优化**
- 重构桌面歌词交互界面，将设置项移至独立列表弹窗
- 优化 Material 页面图标样式、桌面歌词布局及部分设置项说明
- 优化 MIUIX 缓存管理页面布局，新增删除前确认流程
- 翻新 MIUIX 与 Material 关于页结构，新增项目引用库披露页面
- 更新 MIUIX 框架图标资源
- 更新预发布版应用图标，移除底部标识并优化阴影渲染效果

**问题修复**
- 修复"使用当前播放信息重新匹配"按钮未按预期显示的问题
- 修复 MIUIX Blur 弹窗按键与背景对比度不足导致的边界模糊问题
- 修复更新日志获取失败弹窗样式与全局 MIUIX 弹窗不一致的问题

**本地化与内容**
- 优化社区公告加载失败时的提示文案，明确展示具体失败原因
- 优化部分界面中文文案表述

## 🇬🇧 Change Log
<img width="850" height="349" alt="26.7.2" src="https://github.com/user-attachments/assets/5539649a-f550-4563-8a69-f810445f3b96" />

**Important Note**
- Starting from version 26.7.1.1, the "Version." prefix has been removed from version numbers, and Git Tag naming conventions have been updated accordingly

**Feature Updates**
- Enhanced Desktop Lyrics with dual-line display, Romaji comparison, and translation comparison modes
- Added multi-select configuration for homepage lyric types
- Integrated Last.fm playback history sync with custom Key input and per-rule toggle support
- Added pure music tag for online lyrics to skip repeated matching requests, showing only playback info in Capsule view, with manual undo support
- Improved complete offline mode functionality
- Added character limit adjustment for Live Update
- Added multi-source lyrics combination in online lyrics re-match page, supporting original lyrics, translations, and Romaji from different providers
- Added support for Android system-level per-app language preferences
- Added type tags to online lyrics search results and automatically filtered invalid sources to display only available content

**Enhancements**
- Refactored Desktop Lyrics interface, moving settings to a dedicated list popup
- Optimized Material page icons, Desktop Lyrics layout, and selected setting descriptions
- Refined MIUIX cache management layout with added pre-deletion confirmation
- Overhauled MIUIX and Material About pages structure, added project credits disclosure page
- Updated MIUIX framework icon assets
- Updated beta app icon, removed bottom label and refined shadow rendering

**Fixes**
- Resolved issue where the "Re-match with current playback info" button failed to appear as expected
- Fixed low contrast between MIUIX Blur dialog buttons and background causing blurred edges
- Fixed inconsistent styling for the update log fetch failure popup compared to global MIUIX dialogs

**Localization & Content**
- Refined error messaging for community announcement loading failures to clearly display specific failure reasons
- Refined selected interface Chinese copy for improved clarity