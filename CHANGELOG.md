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
<img src="https://raw.githubusercontent.com/FrancoGiudans/Capsulyric/refs/heads/main/screenshots/logo26.8.png" width="100%" />

**功能更新**
- 引入 Apple Music 与 Musicmatch 在线歌词源，QQ 音乐源新增逐字歌词支持
- 新增超级岛胶囊颜色模式与可读性增强功能，支持原色、智能与自定义三档取色，并提供实验室参数调校
- 新增小米超级岛通知样式，支持双行文本显示与右侧图片自定义
- 新增可选的口令加密备份功能，支持备份与恢复 Last.fm 凭据等敏感数据
- 新增纯音乐专辑标记功能
- 新增锁屏时隐藏媒体通知功能
- Live Update 新增进度条隐藏开关

**体验优化**
- 重构设置页架构，拆分为独立模块页面，分离公告与关于页，新增本地歌词独立页面与个性化设置布局优化
- 优化在线歌词检索、评分、匹配等待策略及重匹配交互体验
- 优化重匹配入口触发逻辑，支持首句播放前或无内容时提前进入在线歌词调整页
- 引入模糊 Snackbar 提示样式，并为 Material 页面引入背景模糊效果
- 优化 MIUIX 页面视觉表现，加回并默认启用底部导航栏
- 优化下拉菜单视觉效果
- 翻新小米超级岛通知预览，深色模式下加入边缘指示，优化首页双行歌词切换效果，并在重匹配页面标识缓存来源
- 优化部分场景下的胶囊视觉样式与通知设置页面布局
- 新增 Gitee 作为应用更新检查的数据源
- 新增问卷星在线反馈渠道
- 支持公平运行内存分配策略
- 新增歌词状态识别机制，明确区分等待同步与未找到状态，并提供播放进度占位提示

**问题修复**
- 修复标准小米超级岛歌词滚动异常、左右字号不一致及摄像头遮挡问题
- 修复专辑封面时间逻辑问题
- 修复超级岛相关的部分显示与交互异常

**技术更新**
- 将超级岛调校参数纳入备份系统，并清理废弃的配置缓存

**本地化与内容**
- 新增繁体中文与日语多语言支持
- 替换部分硬编码英文占位文案，调整部分过时文案表述
- 优化第三方开源库声明措辞及部分界面文本
- 更新项目版权声明信息



## 🇬🇧 Change Log
<img src="https://raw.githubusercontent.com/FrancoGiudans/Capsulyric/refs/heads/main/screenshots/logo26.8.png" width="100%" />

**Feature Updates**
- Integrated Apple Music and Musicmatch online lyrics providers, added word-by-word lyrics support for QQ Music
- Added Super Island capsule color modes and readability enhancement, supporting Original, Smart, and Custom color picking with Lab parameter tuning
- Added Xiaomi Super Island notification style with dual-line text display and customizable right-side image
- Added optional passphrase-encrypted backup to secure sensitive data like Last.fm credentials
- Added pure music album tagging feature
- Added option to hide media notifications on the lock screen
- Added progress bar toggle for Live Update


**Enhancements**
- Restructured Settings into independent modules, separated Announcements and About pages, added dedicated local lyrics page and optimized Personalization layout
- Optimized online lyrics search, scoring, matching wait strategy, and re-match interaction
- Optimized re-match entry logic, allowing early access to online lyrics adjustment before the first line plays or when content is unavailable
- Introduced blurred Snackbar prompt style and added background blur effect for Material pages
- Refined MIUIX visual presentation, restored and enabled the bottom navigation bar by default
- Optimized Dropdown visual effects
- Overhauled Xiaomi Super Island notification preview, added edge indicators in dark mode, refined homepage dual-line lyrics transition, and indicated cache sources in the re-match page
- Refined Capsule visual styling and notification settings layout in selected scenarios
- Added Gitee as a data source for application update checks
- Added Wjx online feedback channel
- Added fair runtime memory allocation support
- Added lyrics state recognition mechanism to distinguish Syncing and Not Found statuses, providing playback progress placeholder prompts

**Fixes**
- Fixed standard Xiaomi Super Island lyrics scrolling anomalies, inconsistent font sizes, and camera obstruction issues
- Fixed album cover time logic issues
- Resolved selected display and interaction anomalies related to Super Island

**Technical**
- Integrated Super Island tuning parameters into the backup system and cleared deprecated configuration caches

**Localization & Content**
- Added Traditional Chinese and Japanese multilingual support
- Replaced selected hardcoded English placeholder text and refined outdated copy
- Refined wording for third-party library declarations and selected interface text
- Updated project copyright information