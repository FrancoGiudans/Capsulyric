<!--
发版说明：发版提交信息包含以 [release] 开头的标题行即可触发工作流，不要求本次修改本文件。
合入 develop 发布 Preview；合入 main 发布 Stable。频道由目标分支决定，且需与下方 Preview 标记一致。
正式发版使用 develop -> main 的 PR，并选择 Create a merge commit，禁止 squash/rebase。
完整流程见 docs/RELEASING.md。

PR 标题示例：
  [release]              → 按日期自动版本，频道由分支决定
  [release]26.9.1        → main: Stable；develop: Preview
  [release]26.9.Preview  → 仅允许 develop

手动发版也仅允许 main / develop，并校验 Preview 标记与目标分支一致。
-->

## Release Metadata
- **Preview**: `true`

## Release Highlights
### 🇨🇳
- 重构 Miuix 桌面歌词悬浮窗，优化收起、展开与快捷设置交互，支持从媒体控制弹窗快速开启及四档字重调节。
- 新增全屏歌词播放页与首页播放卡片逐音节高亮，支持逐字扫光、横屏双栏布局、封面流光背景及独立显示设置。
- 升级小米超级岛与实况胶囊显示，接入逐字歌词视觉中心，新增三行歌词卡片、收缩态进度环及右侧歌词校准引导。
- 拓展歌词获取与导入能力，支持按平台歌曲 ID 精准匹配、Superlyric API v4.0 与增强 LRC（ELRC）格式，并新增 Lyrimuse 与 Lyricify 数据库导入。


### 🇬🇧
- Redesigned the Miuix desktop lyrics floating window with refined collapse, expand, and quick-settings interactions, adding quick toggles from the media control sheet and four font weight options.
- Added a full-screen lyrics view and syllable-by-syllable highlighting on the home playback card, featuring word-level sweep effects, a dual-pane landscape layout, dynamic cover-art backgrounds, and dedicated display customization.
- Upgraded Xiaomi Super Island and Live Updates capsules with visual-center alignment for word-by-word lyrics, a three-line lyric card layout, a collapsed progress ring, and guided right-side lyric calibration.
- Expanded lyric fetching and import capabilities with exact matching by platform track ID, support for Superlyric API v4.0 and Enhanced LRC (ELRC), and database imports from Lyrimuse and Lyricify.

