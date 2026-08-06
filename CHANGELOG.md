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
- **Preview**: `true`

## 🇨🇳 更新日志
**功能更新**
- 重构设置页架构，拆分为胶囊与通知、桌面歌词、个性化设置独立页面，分离公告系统与关于页，新增本地歌词独立访问入口
- 新增一种小米超级岛通知样式，支持双行文本显示与右侧图片自定义
- 支持公平运行内存分配策略

**体验优化**
- 优化部分场景下的胶囊视觉样式
- 优化通知设置页面布局与逻辑层级
- 新增问卷星在线反馈渠道

**技术更新**
- 升级核心 UI 框架版本
- 更新应用构建目标平台版本

**本地化与内容**
- 更新项目版权声明信息

## 🇬🇧 Change Log
**Feature Updates**
- Restructured Settings into independent pages for Capsule & Notifications, Desktop Lyrics, and Personalization, separated Announcements and About pages, added dedicated local lyrics entry
- Added Xiaomi Super Island notification style with dual-line text display and customizable right-side image
- Added fair runtime memory allocation support

**Enhancements**
- Refined Capsule visual styling in selected scenarios
- Optimized layout and logical hierarchy of notification settings page
- Added Wjx online feedback channel

**Technical**
- Upgraded core UI framework version
- Updated app target platform version

**Localization & Content**
- Updated project copyright information