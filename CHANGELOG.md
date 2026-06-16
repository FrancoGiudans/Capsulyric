<!-- 
Push this file with a commit message starting with [release] to trigger a release build.

Commit format examples:
  [release]              → Auto version (e.g. Version.26.6.Stable_C1234)
  [release]26.6.1        → Version.26.6.1.Stable_C{commitCount}
  [release]26.6.Preview  → Version.26.6.Preview_C{commitCount} (pre-release)
  [release]Preview       → Version.{YY}.{M}.Preview_C{commitCount} (pre-release)

The Preview flag below overrides any channel in the commit message.
Set to `true` to force a pre-release build regardless of commit message.
-->

## Release Metadata
- **Preview**: `false`

## 🇨🇳 更新日志
<img width="850" height="286" alt="26.6.2" src="https://github.com/user-attachments/assets/d41823c8-b7d6-49bc-ba64-ab6ad73b47ac" />

**功能更新**
- 新增配置备份与恢复系统
- 重构在线歌词调试工具为重匹配工具，迁移访问路径至设置页，并新增首页与媒体控制弹窗动态快捷入口
- Live Update新增左侧专辑图片展示样式
- 在线歌词调试页新增一键填入当前播放歌曲与歌手信息
- 应用设置底部新增“开发者模式”分类，诊断工具已移入该分组
- 小米超级岛绕过模式新增自定义绕过时长
- 导出缓存文件时现可同步导出自定义匹配信息
- 新增默认规则模板，新建应用时自动套用配置
- 支持点击通知直接跳转至正在播放的应用

**体验优化**
- 优化页面切换动画
- 优化开箱体验引导流程

**问题修复**
- 修复歌词滚动中字数回退的显示异常
- 修复开箱体验页面切换时的闪烁问题
- 修复 MTK 设备无法绕过小米超级岛校验的兼容性问题
- 修复桌面歌词拖动后位置未保存的问题，新增位置重置按钮

**技术更新**
- 重构歌词缓存架构，优化读写逻辑与存储格式
- 更新部分依赖库

**本地化与内容**
- 更新部分常见问题解答内容
- 补全各配置选项的说明文案
- 优化部分界面文案与功能说明

## 🇬🇧 Change Log
<img width="850" height="286" alt="26.6.2" src="https://github.com/user-attachments/assets/d41823c8-b7d6-49bc-ba64-ab6ad73b47ac" />

**Feature Updates**
- Introduced configuration backup and restore system
- Refactored online lyrics debug tool into Re-match Tool, relocated access to Settings, and added dynamic shortcut entries on Home page and Media Control popup
- Added left album art display style for Live Update
- Added one-tap import of current song and artist info on the online lyrics debugging page
- Added "Developer Mode" section at the bottom of Settings, with Diagnostics Tools relocated into it
- Added custom bypass duration option for Xiaomi SuperIsland bypass mode
- Cache file export now includes custom matching information
- Added default rule template for automatic configuration when adding new apps
- Added support for opening the currently playing app via notification tap

**Enhancements**
- Improved page transition animation for smoother navigation
- Optimized Onboarding Experience guidance flow

**Fixes**
- Fixed word count regression during lyrics scrolling
- Fixed screen flicker during Onboarding Experience page switching
- Fixed compatibility issue preventing MTK devices from bypassing Xiaomi SuperIsland verification
- Resolved issue where desktop lyrics position was not saved after dragging, and added position reset button

**Technical**
- Refactored lyrics cache architecture with improved read/write logic and storage format

**Localization & Content**
- Updated selected FAQ content
- Added descriptive text for various configuration options
- Refined interface copy and feature descriptions