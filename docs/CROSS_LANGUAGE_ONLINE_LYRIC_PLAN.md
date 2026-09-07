# 跨语言在线歌词命中率提升方案

## 1. 结论

不建议把“将日文歌名翻译成中文”作为主方案。平台之间的曲名差异往往是版权方本地化、官方译名、别名或版本命名差异，并非逐字翻译；机器翻译只能生成搜索提示，不能证明两条曲目属于同一录音。

建议在现有在线歌词框架前增加一层 **Track Identity Resolution（曲目身份解析）**：优先使用平台媒体 ID、ISRC、时长、歌手、专辑、曲序和封面等跨语言信号，把播放器元数据解析为一组带来源与置信度的别名；Provider 先返回歌曲候选，上层确认候选身份后再获取歌词。最终将“是不是同一首歌”和“哪份歌词质量更好”拆成两个独立问题。

推荐按以下顺序落地：

1. 先补齐元数据、候选字段和身份置信度门控，解决错误候选抢跑问题。
2. 再为 Apple Music 增加来源平台解析器，通过 Apple songId/目录搜索获得 ISRC，并用 ISRC、时长和专辑建立跨地区别名。
3. 最后增加国内 Provider 的宽搜召回、封面感知哈希消歧、MusicBrainz 补充别名和本地纠错学习。

## 2. 当前链路与根因

当前主要链路为：

```text
MediaMonitorService
  -> LyricRepository.MediaInfo(title, artist, album, duration, raw*)
  -> MetadataLyricFetchCoordinator
  -> OnlineLyricSource
  -> OnlineLyricFetcher.LyricQuery(title, artist)
  -> 各 Provider 内部搜索并直接挑一个候选、下载歌词
  -> OnlineLyricSelector 按歌词质量 + 标题/歌手字符串评分
  -> OnlineLyricCacheStore
```

现有实现有以下结构性限制：

- `MediaInfo` 已包含 `album`、`duration` 和封面状态，但 `LyricQuery` 只保留 `title + artist`。
- `SearchCandidate` 只有 `matchedTitle + matchedArtist`，Provider 返回的 songId、专辑、时长、ISRC、封面等信息没有进入公共匹配层。
- 原始查询失败后只尝试去括号、`remix`、`feat.` 等文本清理，无法产生跨语言官方别名。
- `CandidateMatcher` 在所有候选标题均不匹配时回退第 0 条，存在明确的误匹配风险。
- `OnlineLyricSelector` 把“身份是否正确”和“是否逐字、歌词行数、源偏好”混在同一个总分里；有歌词内容不代表歌曲身份正确。
- 任一源先返回可解析歌词后，只再等待 500ms 就取消尚未完成的请求；较快的错误结果可能阻止较慢的正确结果参与比较。
- cleanTitle fallback 只在完全没有可用歌词时执行，低置信度或错误歌词也会阻止下一轮查询。
- Apple Music Provider 已经能取得 catalog song JSON，但只向上层暴露标题/歌手；`id`、`isrc`、`albumName`、`durationInMillis` 和 artwork 均未利用。
- Apple Music Provider 在搜索前就要求 `media-user-token`。目录解析与歌词下载没有分离，导致未登录用户无法把匿名 Apple Catalog 当作身份解析器使用。
- Apple Music 搜索请求的 `term` 当前只有 title，没有 artist，重名歌曲的候选质量偏低。
- 智能选择模式在 `OnlineLyricSource` 中使用全局 `defaultIds()`，没有使用来源 App 对应的默认顺序；Apple Music 播放时并不会天然优先 Apple Music Provider。
- 当前缓存以 `packageName + rawTitle + rawArtist` 为主键。它能保存人工覆盖，但没有独立的“原始身份 -> 规范身份/别名”记录，也无法跨播放器复用已确认映射。

## 3. 目标架构

```text
播放器 MediaSession
  -> ObservedTrack（完整观测元数据）
  -> TrackIdentityResolver
       1. 本地身份/纠错缓存
       2. 来源平台 Adapter（Apple Music 首发）
       3. ISRC/目录别名补全
       4. 查询计划生成
  -> QueryPlan（原始、清理、别名、歌手+专辑、宽搜）
  -> Provider.search() 返回 ProviderTrackCandidate
  -> IdentityMatcher 计算证据与置信度、拒绝冲突版本
  -> 对高置信候选调用 Provider.fetchLyrics(candidateRef)
  -> LyricQualitySelector 只在同一身份的歌词之间选质量
  -> 歌词缓存 + 身份映射缓存 + 诊断快照
```

核心约束：

- **先确认身份，后比较歌词质量。**
- **稳定标识优先于文本。** ISRC/来源平台 ID > 时长/专辑/封面组合 > 标题别名 > 翻译结果。
- **跨语言不是标题不匹配。** 当 ISRC 或多个非文本信号吻合时，标题脚本完全不同不应扣成负分。
- **不确定时宁可不自动应用。** 在线歌词显示错误比暂时未命中更伤害体验。
- **渐进增强。** 保留现有快速精确查询路径，只有低置信或未命中时才进入较昂贵的跨语言扩展。

## 4. 建议数据模型

### 4.1 播放器观测

```kotlin
data class ObservedTrack(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val durationMs: Long?,
    val mediaId: String?,
    val mediaUri: String?,
    val trackNumber: Int?,
    val artworkFingerprint: Long?,
    val localeHint: String?
)
```

Android 的 `MediaMetadata` 已定义 `MEDIA_ID`、`MEDIA_URI`、`ALBUM_ARTIST`、`TRACK_NUMBER` 等字段。需要按 App 记录“字段是否真实可用”，不能假定每个播放器都会填充。

### 4.2 规范身份与别名

```kotlin
data class ResolvedTrackIdentity(
    val canonicalKey: String,
    val isrc: String?,
    val providerIds: Map<String, String>,
    val titleAliases: List<TextAlias>,
    val artistAliases: List<TextAlias>,
    val albumAliases: List<TextAlias>,
    val durationMs: Long?,
    val trackNumber: Int?,
    val artworkFingerprints: Set<Long>,
    val confidence: Float,
    val evidence: List<IdentityEvidence>
)

data class TextAlias(
    val value: String,
    val locale: String?,
    val source: AliasSource,
    val confidence: Float
)
```

`canonicalKey` 优先使用 `isrc:<ISRC>`；没有 ISRC 时使用来源平台稳定 ID，例如 `apple:<storefront>:<songId>`；再退化为规范化歌手、专辑、时长桶的本地合成键。

### 4.3 Provider 候选

```kotlin
data class ProviderTrackCandidate(
    val provider: OnlineLyricProvider,
    val providerTrackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val isrc: String?,
    val artworkUrl: String?,
    val trackNumber: Int?,
    val versionTags: Set<VersionTag>,
    val queryVariantId: String
)
```

现有 QQ、网易、酷狗、汽水和 Apple 的搜索 JSON 应先尽量映射这些字段；某个 Provider 缺字段时保留 `null`，不为统一接口伪造数据。

## 5. 查询与解析流程

### 5.1 第 0 层：缓存和来源平台直达

1. 优先按 `packageName + mediaId` 查本地身份映射。
2. mediaId 缺失时，使用 `packageName + normalized(rawTitle/rawArtist) + durationBucket` 查映射。
3. 命中用户人工修正时直接采用；它的优先级高于自动解析。
4. 对 Apple Music，先尝试从 `MEDIA_ID`/`MEDIA_URI` 提取 songId；提取规则必须通过真实设备样本验证，不能只靠格式猜测。
5. 有 songId 时直接获取 Apple catalog song；没有时用 `title + artist` 搜索，并用 album、duration 和 artwork 选候选。

### 5.2 第 1 层：保留现有快速路径

并行发送：

- 原始 `title + artist`；
- 规范化标题 + artist；
- 已有高置信 title/artist alias。

只有身份置信度达到自动接受阈值时才结束快速路径。某个结果仅仅“有可解析歌词”不能触发提前结束。

### 5.3 第 2 层：跨语言身份扩展

Apple Music 首发路径：

1. 从 Apple song 获取 `isrc`、`durationInMillis`、`albumName`、artist、artwork 与 songId。
2. 使用 Apple 官方 `filter[isrc]` 在账号 storefront 以及目标别名 storefront 查询同一录音。首期建议只查询来源 storefront + `cn`，避免无界遍历地区。
3. 把各 storefront 返回的本地化 name/artistName/albumName 作为“目录证实别名”，而非直接覆盖当前显示元数据。
4. 可选调用 MusicBrainz 的 ISRC lookup，补充 recording/release/artist aliases；MusicBrainz 只作增强，失败不能阻断主链路。
5. 不把机器翻译结果标记为已证实别名。若以后加入翻译，仅用于生成低优先级搜索词，不能独立触发自动接受。

### 5.4 第 3 层：国内 Provider 宽搜

按成本和区分度依次生成有限查询：

1. 已证实的中文/目标地区 title alias + artist alias；
2. artist + album alias；
3. artist + 原专辑；
4. artist-only 或 album-only 宽搜，仅在已有 duration/ISRC/artwork 等强信号时启用。

Provider 搜索结果先统一返回候选，不立即下载每条歌词。全局匹配器从各源候选中挑出少量高置信候选，再下载歌词，避免请求数成倍增长。

### 5.5 第 4 层：歧义消解与人工兜底

- 当分数处于灰区且前两名差距不足时，仅对前 2～3 名下载小尺寸封面，在本地计算感知哈希，与 MediaSession 封面比较。
- 仍不确定时进入 NOT_FOUND，并在现有“在线歌词重匹配”页展示推荐候选和证据，允许用户确认或修改。
- 用户确认后写入永久优先的本地身份映射；清除人工映射时恢复自动解析。

## 6. 身份评分与自动接受规则

建议把分数拆为可观测组件，初始权重之后通过测试集校准：

| 证据 | 建议初始影响 | 说明 |
|---|---:|---|
| ISRC 完全一致 | +60 | 最强跨语言录音身份；同一 ISRC 仍可能对应多个目录条目 |
| 来源平台稳定 ID 一致 | +55 | 仅在同一平台命名空间内比较 |
| 时长差 ≤ 1.5s / 3s / 8s | +20 / +14 / +5 | 超过 12s 默认强惩罚，需考虑静音尾和版本差异 |
| 已证实 title alias 一致 | +25 | 来源必须是 ISRC 目录映射、人工确认或稳定缓存 |
| 规范化标题一致/包含 | +18 / +7 | 沿用现有文本能力，但权重不再压过稳定标识 |
| artist alias 完全/部分一致 | +20 / +10 | 支持组合艺人、别名、罗马字转写 |
| album alias 完全/模糊一致 | +15 / +8 | 单曲版、豪华版需要版本归一化 |
| 封面 pHash 高度/部分相似 | +15 / +7 | 只在歧义阶段使用；不同版封面不能作为硬拒绝 |
| trackNumber 一致 | +5 | 辅助信号 |
| live/remix/instrumental/cover 等版本冲突 | -35 至硬拒绝 | 避免同名不同录音 |
| 歌手与专辑同时冲突 | 硬拒绝 | 除非 ISRC/平台 ID 已证明一致 |

建议初始门槛：

- `confidence >= 80`：自动接受；
- `65..79`：仅当前两名差距至少 12 分、且无版本冲突时接受；
- `< 65`：不自动应用，继续扩展或交给人工重匹配。

这些数值不是最终事实，必须由标注测试集和错误案例回放校准。关键是保留每个分项和拒绝原因，而不是只记录一个不可解释的总分。

歌词质量评分继续负责逐字、翻译、罗马音、行数和 Provider 偏好，但仅在已确认属于同一 `canonicalKey` 的结果之间比较。

## 7. 并发、延迟与请求预算

采用分阶段竞速，而不是一次把所有查询变体乘以所有 Provider：

- 热缓存：目标 50ms 内完成身份解析，直接读取歌词缓存。
- 快速层：保留现有并发 Provider，约 2～3 秒预算；高置信命中立即返回。
- 扩展层：仅在快速层无高置信结果时执行，约 5～7 秒总预算。
- 封面消歧：只对灰区候选触发，并设置图片大小、候选数与总流量上限。
- 整体冷启动继续受 10 秒左右上限约束；取消协程时保存已完成的身份解析缓存，但不得把过期曲目结果写入当前 UI。
- 为解析失败建立短 TTL negative cache，避免一首歌播放期间反复请求；认证失败、限流和“确实无结果”应使用不同 TTL。

提前结束条件应从“首个可用歌词”改为“首个达到身份阈值的候选歌词”；到达后可以保留一个很短的质量提升窗口。

## 8. 缓存设计

建议把歌词内容缓存和身份映射缓存分开：

```text
cache_store/
  identity_index.json
  identities/{prefix}/{canonicalKeyHash}.json
  observed_mappings/{package}/{observedKeyHash}.json
  lyrics/...                         # 保留现有歌词缓存
```

身份条目至少保存：

- observed key、canonicalKey、ISRC、provider IDs；
- title/artist/album aliases 及其 locale、来源、置信度；
- duration、trackNumber、artwork pHash；
- selected candidate、评分分项、解析策略；
- `AUTO` / `USER_CONFIRMED` / `USER_OVERRIDE` 来源；
- createdAt、updatedAt、lastValidatedAt、schemaVersion。

迁移原则：

- 现有人工 `MatchOverride` 转成 `USER_OVERRIDE` 映射，不能丢失。
- 现有歌词缓存仍可读取；命中旧条目时可惰性生成身份映射。
- package + mediaId 只能作为 observed key，不能直接当跨平台 canonicalKey。
- 自动映射设置可失效 TTL；人工确认默认不过期，除非用户清除。

## 9. Provider 接口重构

当前 Provider 把“搜索、候选选择、歌词下载”合并在 `fetch(title, artist)` 中。建议渐进拆分：

```kotlin
interface TrackCatalogProvider {
    suspend fun search(query: ProviderSearchQuery): List<ProviderTrackCandidate>
    suspend fun fetchLyrics(candidate: ProviderTrackCandidate): LyricPayload?
}
```

迁移期间可保留旧 `fetch()` 适配器，先逐个 Provider 实现新接口，避免一次性重写全部来源。

优先顺序：

1. Apple Music：字段最完整，先作为 origin resolver 和歌词 Provider 分离。
2. QQ Music / 网易云：补齐 songId、album、duration，验证是否能读取 ISRC。
3. 酷狗 / 汽水：补齐 hash/trackId、album、duration。
4. LRCLIB：使用其 album/duration 参数做精确查询，并增加 search fallback。
5. LrcApi / Musixmatch：按 API 可用字段渐进适配；不能返回候选列表的 Provider 继续作为直接歌词端点。

## 10. 分阶段实施计划

### Phase 0：建立基线与回放数据

- 在 debug 构建记录完整搜索 attempt、候选元数据、各分项得分、提前结束原因和耗时。
- 建立脱敏 JSON fixture：至少覆盖中/日/韩/英标题、官方译名、罗马字、feat/remix/live/cover、同名歌和不同版本。
- 不默认上传播放历史；由用户主动导出诊断样本或使用项目维护的公开曲目集。
- 先量出当前 Recall@1、Wrong Match Rate、Miss Rate、P50/P95 延迟与每曲请求数。

### Phase 1：低风险基础改造

- 扩展 `MediaInfo`/`LyricQuery`，带入 album、duration、albumArtist、mediaId、trackNumber。
- 扩展公共 candidate 字段，先从 QQ/网易/酷狗/汽水 JSON 映射 album、duration、ID。
- 新增 `IdentityMatcher`，把身份门控与 `OnlineLyricSelector` 的歌词质量分离。
- 删除“所有候选不匹配时回退第 0 条”的自动应用行为。
- 将 fallback 触发条件从“没有歌词”改为“没有高置信身份”。
- 修正智能模式的来源 App 默认顺序。

预期：即使标题完全不同，只要歌手相同且时长/专辑高度吻合，也能从国内宽搜候选中安全召回；同时显著降低错误歌词。

### Phase 2：Apple Music 身份桥接

- 新增 Apple origin adapter，验证 Android Apple Music 的 mediaId/mediaUri 实际格式。
- Apple catalog search 使用 `title + artist`，并按 duration/album/artwork 选曲。
- 匿名 catalog 解析与需要登录的歌词下载分离。
- 提取 ISRC，并通过 Apple 官方 `filter[isrc]` 获取跨 storefront 的同录音条目和本地化别名。
- 首期只查询来源 storefront 与 `cn`；结果缓存后不重复解析。

预期：解决 Apple Music JP 等本地化元数据到国内源标题之间的主要断层。

### Phase 3：增强召回与消歧

- 接入 MusicBrainz ISRC lookup 作为可关闭的补充别名源。
- 为宽搜加入 artist+album、artist-only 查询计划与严格预算。
- 加入本地封面 pHash 灰区消歧。
- 加强版本标签、日文假名/罗马字、简繁体与 Unicode 归一化；转写不是翻译，不能单独证明身份。

### Phase 4：纠错闭环与灰度发布

- 在现有重匹配页展示“为何命中/为何拒绝”、候选时长差、专辑、别名来源和置信度。
- 用户确认后写入身份映射，之后同一首歌热命中。
- 用实验室开关灰度启用跨语言解析；按 App/语言对比旧算法与新算法。
- 达标后默认启用，保留“严格匹配/允许跨语言增强”策略开关和诊断清除入口。

## 11. 测试与验收指标

### 单元测试

- Unicode NFKC、全半角、标点、大小写、空格、简繁体、假名/罗马字规范化。
- 组合艺人拆分与别名匹配。
- live/remix/cover/instrumental/karaoke/acoustic 等版本冲突。
- duration 容差、缺字段降级和封面 pHash 距离。
- 阈值边界、前两名 margin、强标识覆盖跨语言标题差异。
- 取消、超时、negative cache TTL 与缓存 schema 迁移。

### Provider fixture 测试

- 固定保存各 Provider 搜索响应片段，验证 title/artist/album/duration/id/isrc 的解析。
- Apple：按 songId 查询、按 ISRC 跨 storefront 查询、同一 ISRC 多条目录结果。
- Provider 字段缺失、字段改名和错误响应不得导致错误自动接受。

### 集成回放

- 使用 fake providers 回放“快但错误”和“慢但正确”的竞速，确保正确结果不会被 500ms 逻辑取消。
- 回放 Apple JP `ただ風を追いかけて` 与国内别名 `唯有追逐风的时候`，验证不依赖直接字符串相似度也能落到同一 canonicalKey。
- 同名不同歌手、同歌手同名不同版本、原唱/翻唱必须覆盖。

### 指标

| 指标 | 定义 | 建议发布门槛 |
|---|---|---|
| Correct Recall@1 | 首次自动展示即为正确曲目的比例 | 跨语言集合相对基线提升至少 25 个百分点 |
| Wrong Match Rate | 自动展示了其他曲目歌词的比例 | ≤ 0.5%，且不得高于旧算法 |
| Miss Rate | 完成预算后仍无歌词 | 显著低于基线，按来源 App/语言拆分 |
| Warm P95 | 已有身份/歌词缓存的解析耗时 | < 200ms |
| Cold P95 Added Latency | 跨语言层相对快速层新增耗时 | < 3s；整体仍受总超时约束 |
| Request Cost | 每首歌平均/中位网络请求数与图片流量 | 灰度后再定硬门槛，防止请求爆炸 |
| Manual Correction Rate | 自动结果被用户重匹配覆盖的比例 | 持续下降，用于发现高分误匹配 |

25 个百分点是初始产品目标，不应在没有 Phase 0 基线时承诺绝对命中率。

## 12. 隐私、稳定性与运维

- 默认仅在设备本地保存身份映射和诊断记录，不上传播放历史、歌词或封面。
- 封面比较只下载候选缩略图并在本地算哈希；不上传用户当前封面。
- MusicBrainz 等第三方增强必须有明确 User-Agent、速率限制、缓存和独立熔断；不可成为歌词主链路单点。
- Apple、QQ、网易、酷狗、汽水、Musixmatch 中存在非稳定或非公开端点，Provider 解析必须使用 fixture 回归、错误隔离和能力探测。
- 认证错误、限流、网络失败、无搜索结果和无歌词应分开记录，以便设置不同重试策略。
- 自动匹配日志避免记录完整 token、URL 中的认证参数和不必要的用户播放历史。

## 13. 代码落点

建议新增：

```text
lyrics/online/identity/
  ObservedTrack.kt
  ResolvedTrackIdentity.kt
  TrackIdentityResolver.kt
  IdentityMatcher.kt
  IdentityEvidence.kt
  TrackAliasNormalizer.kt
  ArtworkFingerprint.kt
lyrics/online/query/
  OnlineLyricQueryPlanner.kt
  SearchQueryVariant.kt
lyrics/cache/
  TrackIdentityCacheStore.kt
lyrics/online/provider/
  TrackCatalogProvider.kt
  ProviderTrackCandidate.kt
integration/applemusic/
  AppleMusicCatalogResolver.kt
```

主要修改：

- `runtime/service/MediaMonitorService.kt`：采集额外标准元数据字段。
- `lyrics/state/LyricRepository.kt`：扩展 `MediaInfo`，保持默认值以降低迁移影响。
- `lyrics/source/OnlineLyricSource.kt`：把完整观测传给解析器，执行分层查询与 stale check。
- `lyrics/online/OnlineLyricFetcher.kt`：从单 query 两轮抓取改成受预算控制的 QueryPlan。
- `lyrics/online/selection/CandidateMatcher.kt`：迁移为证据化身份匹配，取消无条件第 0 条回退。
- `lyrics/online/selection/OnlineLyricSelector.kt`：只负责已确认身份后的歌词质量选择。
- `lyrics/online/provider/*.kt`：拆分 search/fetch，并暴露候选元数据。
- `lyrics/cache/OnlineLyricCacheStore.kt`：兼容旧歌词缓存，人工覆盖迁移到新身份映射。
- `feature/onlinelyricdebug/OnlineLyricDebugViewModel.kt` 及双 UI：展示身份证据并复用现有人工纠错入口。

## 14. 推荐的首个开发切片

第一批 PR 不应直接接入多个外部解析服务。建议选择一个可验证、可回滚的纵向切片：

1. 扩展 `ObservedTrack` 和 QQ/网易候选的 album/duration/id。
2. 引入 `IdentityMatcher`，修掉无条件第 0 条与“有歌词即算命中”。
3. 加一个受限的 artist+album 宽搜 fallback。
4. 在 debug 快照中显示证据分项。
5. 用 30～50 首跨语言/同名/不同版本 fixture 校准阈值。

这一切片即使还没有 Apple ISRC 桥接，也能验证新架构能否安全提高召回，并为 Phase 2 提供可靠评分基础。

## 15. 外部能力依据

- Android `MediaMetadata` 定义了持久媒体 ID、专辑、专辑艺术家、时长、曲序、媒体 URI 和封面等标准键：[Android MediaMetadata](https://developer.android.com/reference/android/media/MediaMetadata)。
- Apple Music song attributes 包含本地化曲名、artistName、albumName、durationInMillis、ISRC、artwork 等字段：[Apple Songs.Attributes](https://developer.apple.com/documentation/applemusicapi/songs/attributes-data.dictionary)。
- Apple Music API 支持按 ISRC 获取 catalog songs，且同一 ISRC 可能返回多个目录条目：[Get Multiple Catalog Songs by ISRC](https://developer.apple.com/documentation/applemusicapi/get-multiple-catalog-songs-by-isrc)。
- MusicBrainz Web Service 支持 ISRC lookup，并能返回 recording/release/artist 关系与别名：[MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API)。

## 16. 决策摘要

- 主方案：**身份解析 + 证据融合 + 别名缓存**。
- 不采用：把机器翻译后的标题直接当作正确歌曲名。
- 首发来源：Apple Music，因为现有 Provider 已具备 catalog 访问基础且官方数据包含 ISRC。
- 首发安全目标：先降低错误命中，再扩大跨语言召回。
- 首发落地方式：实验室开关 + 本地诊断回放 + 分阶段 Provider 迁移。
