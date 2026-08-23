---
name: workthrough-tracker
description: >-
  Use this skill whenever starting any new work, formulating a development plan,
  or completing individual tasks/steps in this project. Guides creating and maintaining
  work progress logs named as `workthrough/YYYY-MM-DD_<工作名称>.md` (e.g., `2026-08-01_修复xxxx.md`),
  and automatically committing changes to the dedicated `workthrough/` Git repository upon completing work.
---

# Workthrough Tracker (工作记录追踪)

本 Skill 用于规范在开展工作、制定计划以及完成工作步骤时的记录流程，确保在 `workthrough/` 目录下按日期及具体工作名称独立记录所有工作进度与变更。

---

## 核心规则与流程

### 1. 开展工作 / 制定计划时 (Start of Task / Planning)
- **确定文件名**：
  - 确认当天日期（格式：`YYYY-MM-DD`，例如 `2026-08-23`）。
  - 根据当前任务提取精炼的工作名称（例如 `修复歌词解析异常`、`适配Miuix毛玻璃效果`）。
  - 文件路径为：`workthrough/YYYY-MM-DD_<工作名称>.md`（例如 `workthrough/2026-08-23_修复歌词解析异常.md`）。
- **创建或继续记录**：
  - 若该工作记录文件不存在，在 `workthrough/` 目录下创建该文件。
  - 写入标题、任务目标与背景、详细实施计划清单（带复选框）。
  - 若是在同一次任务的后续阶段继续推进，则在现有文件对应章节继续完善。

### 2. 执行过程中 / 完成计划中的一步时 (During Execution / Step Completion)
- **即时记录**：每完成一个子步骤或遇到关键决策/调试结果时，**立即**更新对应的 `workthrough/YYYY-MM-DD_<工作名称>.md` 文件，不要等到所有工作全部结束才记录。
- **更新内容**：
  - 将计划列表中的复选框标记为已完成：`- [x] <步骤名称>`。
  - 记录所修改/新建的关键文件路径及改动点。
  - 记录测试与验证结果（编译状态、单元测试、运行表现等）。
  - 记录遇到的问题及解决方案。

### 3. 完成一项工作时 (Task Completion)
- 汇总本次任务完成情况。
- 更新任务状态为 `[已完成]` 或标明后续待办事项。

### 4. 完成所有工作后自动提交 Git Commit (Auto Git Commit)
- `workthrough/` 目录是一个独立的 Git 仓库（已被根目录 `.gitignore` 忽略，内部拥有独立的 `.git`）。
- **必须执行**：在完成当前任务的所有开发与记录工作后，**立即自动在 `workthrough/` 中提交一个 Git Commit**，简要说明完成了什么工作。
- **执行命令**：
  ```bash
  git -C workthrough add .
  git -C workthrough commit -m "docs: 记录完成<工作名称>的工作进度与变更"
  ```
- **Commit Message 示例**：
  - `git -C workthrough commit -m "docs: 记录完成 2026-08-23_适配Miuix毛玻璃效果"`
  - `git -C workthrough commit -m "docs: 记录完成 2026-08-01_修复xxxx"`

---

## 文件命名规范

- **目录**：`workthrough/`
- **文件名格式**：`YYYY-MM-DD_<工作名称>.md`
  - 示例 1：`workthrough/2026-08-01_修复xxxx.md`
  - 示例 2：`workthrough/2026-08-23_适配Miuix下拉菜单毛玻璃效果.md`
  - 示例 3：`workthrough/2026-08-23_重构LyricParser解析器.md`

---

## 记录模板 (Template)

### `workthrough/YYYY-MM-DD_<工作名称>.md` 模板

```markdown
# 2026-08-23 修复歌词解析异常 工作记录

---

## 1. 目标与背景
- 简要描述本次任务需要实现的功能、修复的问题或重构的目标。

---

## 2. 实施计划 (Plan)
- [ ] 步骤 1: 定位解析异常引发原因
- [ ] 步骤 2: 修复正则表达式与边界处理逻辑
- [ ] 步骤 3: 补充单元测试并验证编译

---

## 3. 执行过程与变更记录 (Execution Log)
- **[14:35] 步骤 1 完成**
  - 排查结果：在特定时间戳格式下出现越界错误。
- **[14:50] 步骤 2 完成**
  - 修改文件：`app/src/main/java/.../LyricParser.kt`
  - 变更说明：优化时间标签正则匹配，增加安全校验。
- **[15:10] 步骤 3 完成**
  - 修改文件：`app/src/test/java/.../LyricParserTest.kt`
  - 变更说明：新增多时间标签边界测试用例。

---

## 4. 验证与测试 (Verification)
- 编译状态：BUILD SUCCESSFUL
- 单元测试：`./gradlew testDebugUnitTest` 全部通过 (12/12)
- 运行验证：真机/模拟器测试解析正常。

---

## 5. 总结与状态 (Summary)
- 当前状态：[已完成]
- 遗留事项：无
```

---

## 注意事项

1. **按工作命名**：每项独立的工作/任务使用清晰描述性名称命名文件（`YYYY-MM-DD_<工作名称>.md`），便于按事件追溯。
2. **原子性更新**：每完成一个计划步骤，应立即同步更新文档，保证记录实时准确。
3. **路径准确**：记录文件变更时，使用项目相对路径（例如 `app/src/main/java/...` 或 `docs/...`），方便追溯。
4. **自动提交**：全部工作完成后，务必自动在 `workthrough/` 中执行 git commit 提交当次记录。
