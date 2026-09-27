# 发版与分支同步

## 分支职责

- 功能、修复 PR 合入 `develop`，日常 Experiment 构建由 `develop_build.yml` 负责。
- Changelog Release 由 `main` / `develop` 上包含 `[release]` 标题行的提交触发，不要求该提交改动 `CHANGELOG.md`。
- `develop` 发布 Preview；正式发版开 `develop -> main` PR，使用 **Create a merge commit**，`main` 发布 Stable。
- `main` 和 `develop` 之间禁止 squash / rebase merge，以免相同改动产生两套 SHA。
- 保护规则若要求线性历史，需要调整为允许 merge commit；仍保留 PR 审核和必需检查。

## 自动发版

自动发版须 push 到 main/develop，且最终提交信息包含以 `[release]` 开头的行。工作流读取该分支当前版本的 `CHANGELOG.md`，不依赖本次提交是否改了该文件。
推荐发版 PR 标题为 `[release]26.9.1`。合并前检查最终提交信息保留该标题。
默认 merge commit 的正文标题和 squash 自动附加的 `(#123)` 都可以解析。
普通同步 PR 的最终提交信息不要包含发版标题行。

频道由目标分支决定：main 为 Stable，develop 为 Preview。发版前还必须在 `CHANGELOG.md` 的 `## Release Metadata` 下提供且只提供一项 Preview 标记：main 必须设为 `false`，develop 必须设为 `true`。标记缺失、重复或与分支不匹配都会在构建和发布前失败。
输入仅允许数字版本及可选频道，如 `26.9.1`、`26.9.Preview`、`Preview` 或空值。
Experiment 使用独立的 Experiment / Preview Build 工作流。

## 手动发版

在 Changelog Release 的 Run workflow 中选择 main 或 develop。版本可留空，但同样必须设置匹配的 Preview 标记。
其他分支、tag，非法版本、或 Preview 标记缺失/不符都会在构建、打 tag、发布之前失败。
CHANGELOG 无变化但需要发版时使用此入口；只要当前文件有正确标记即可。

## 本次历史修复

远端曾将 develop 的提交以不同 SHA 放入 main，导致历史分叉，但实际文件仅有 Preview 元数据和 README 的 LTS 文案差异。
`codex/release-flow-sync` 基于 main，用真正的 merge 纳入 develop 历史，并保留 main 的 LTS 文案。

1. 将修复分支以 **Create a merge commit** 合入 main，PR/最终提交不要带发版标题行。
2. 再开 `main -> develop` 同步 PR，同样选择 **Create a merge commit**，不带发版标题行。
3. 两次合并完成后检查 `git diff origin/main origin/develop` 应为空；main 应是 develop 的祖先。
4. 两个分支的 HEAD 不必相同：第二次 PR 会产生额外 merge commit，但内容一致、共同历史已恢复。

待处理的回退 PR #120 会撤销整批功能，不能作为历史同步方案。若只是为修复分叉而创建，应关闭；若确实要回滚功能，需单独重新评估本方案。
本修复不删除已有 Release/tag，也不会自动触发新的 Stable。同步可能按现有规则触发 Experiment 构建。

## 后续发布循环

功能 PR -> develop -> 测试。Preview 发版时设 `Preview: true`，以 `[release]版本` 合入 develop。Stable 发版时设 `Preview: false`，以 `[release]版本` 的 develop -> main PR 发布。
Stable 发布后 main 上的标记为 false；回流到 develop 时保留 develop 的 true，供下一轮 Preview 使用。main 上的独立修复通过 main -> develop PR 回流；全部使用 merge commit。
允许 develop 保持领先，不需要每次开发都让两个分支内容相同。
