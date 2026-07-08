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
- 支持在线歌词重匹配页面组合不同来源的歌词、翻译与罗马音
- 新增 Android 系统级单应用语言偏好设置支持
- 在线歌词检索结果新增类型标识，并自动过滤无效来源，仅展示可用内容

**体验优化**
- 更新预发布版应用图标，移除底部标识并优化阴影渲染效果

**问题修复**
- 修复桌面歌词悬浮窗无响应问题

**本地化与内容**
- 优化部分界面中文文案表述

## 🇬🇧 Change Log
**Feature Updates**
- Added multi-source lyrics combination in online lyrics re-match page, supporting original lyrics, translations, and Romaji from different providers
- Added support for Android system-level per-app language preferences
- Added type tags to online lyrics search results and automatically filtered invalid sources to display only available content

**Enhancements**
- Updated beta app icon, removed bottom label and refined shadow rendering

**Fixes**
- Resolved unresponsiveness issue with the desktop lyrics floating window

**Localization & Content**
- Refined selected interface Chinese copy for improved clarity
