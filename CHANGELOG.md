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
- 引入 Apple Music 与 Musicmatch 在线歌词源
- QQ 音乐源新增逐字歌词支持
- 新增超级岛胶囊颜色模式与可读性增强功能，支持原色、智能与自定义三档取色，并提供实验室参数调校
- 新增可选的口令加密备份功能，支持备份与恢复 Last.fm 凭据等敏感数据
- 新增锁屏时隐藏媒体通知功能
- Live Update 新增进度条隐藏开关

**体验优化**
- 优化在线歌词检索、评分逻辑及重匹配交互体验
- 引入模糊 Snackbar 提示样式
- 将超级岛调校参数纳入备份系统，并清理废弃的配置缓存

**问题修复**
- 修复超级岛相关的部分显示与交互异常
- 修复 Material 弹窗背景消失的问题

**本地化与内容**
- 优化第三方开源库声明措辞及部分界面文本



## 🇬🇧 Change Log
**Feature Updates**
- Integrated Apple Music and Musicmatch online lyrics providers
- Added word-by-word lyrics support for QQ Music provider
- Added Super Island capsule color modes and readability enhancement, supporting Original, Smart, and Custom color picking with Lab parameter tuning
- Added optional passphrase-encrypted backup to secure sensitive data like Last.fm credentials
- Added option to hide media notifications on the lock screen
- Added progress bar toggle for Live Update

**Enhancements**
- Optimized online lyrics search, scoring logic, and re-match interaction
- Introduced blurred Snackbar prompt style
- Integrated Super Island tuning parameters into the backup system and cleared deprecated configuration caches

**Fixes**
- Resolved selected display and interaction anomalies related to Super Island
- Fixed disappearing background issue in Material dialogs

**Localization & Content**
- Refined wording for third-party library declarations and selected interface text