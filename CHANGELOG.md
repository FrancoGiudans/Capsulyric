<!--
发版说明：PR 修改本文件，最终合并提交保留以 [release] 开头的标题行。
合入 develop 发布 Preview；合入 main 发布 Stable。频道由目标分支决定。
正式发版使用 develop -> main 的 PR，并选择 Create a merge commit，禁止 squash/rebase。
完整流程见 docs/RELEASING.md。

PR 标题示例：
  [release]              → 按日期自动版本，频道由分支决定
  [release]26.9.1        → main: Stable；develop: Preview
  [release]26.9.Preview  → 仅允许 develop

手动发版也仅允许 main / develop；不再使用 Preview 元数据覆盖频道。
-->

## Release Highlights
### 🇨🇳
- 新增液态玻璃样式导航栏，优化拖拽交互与视觉效果。
- 新增设置项搜索与快捷跳转，更快找到并打开所需设置。
- 全面优化在线歌词匹配，提高识别命中率，并完善罗马音、Sidecar 与 TTML 歌词支持。

### 🇬🇧
- Added a liquid glass navigation bar with smoother dragging and refined visual effects.
- Added settings search and quick navigation to help you find and open settings faster.
- Improved online lyrics matching accuracy, including better support for romanized, sidecar, and TTML lyrics.
