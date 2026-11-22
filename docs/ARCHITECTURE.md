# Architecture

This document describes the current package boundaries and runtime data flow for Capsulyric.

本文档说明 Capsulyric 当前的包边界与运行时数据流。

> This document is also the **file-level map**: each feature/page, runtime service, lyric-pipeline stage, and overlay surface lists the concrete files that own it. Unless noted otherwise, paths below are relative to `app/src/main/java/com/example/islandlyrics/`. / 本文档同时充当**文件级地图**：每个功能/页面、运行时服务、歌词流水线阶段与悬浮层表面都会列出负责它的具体文件。除特别说明外，下文路径均相对于 `app/src/main/java/com/example/islandlyrics/`。

## Goals (架构目标)

- Keep service/runtime code independent from screen UI. / 保持后台运行时与服务层代码同界面 UI 解耦。
- Keep lyric fetching, parsing, scoring, cache, and repository state in lyric-focused packages. / 将歌词获取、解析、评分、本地缓存与仓储状态严格收拢在歌词领域包中。
- Keep Material and Miuix UI as presentation choices over shared feature state. / 保持 Material 与 Miuix 作为表现层选型，共享底层 ViewModel 与业务契约。
- Keep external API integrations behind narrow adapters. / 将外部服务与提权系统集成收敛于窄接口适配器之后。
- Support rich overlay surfaces (Live Update capsule, Xiaomi Super Island, Desktop floating lyrics, Full-screen playback) with high visual fidelity, fluid animations, and robust foreground-service reliability. / 在保证高视觉还原度、流畅动效与前台服务稳定性的前提下，协同支撑灵动胶囊、超级岛、桌面悬浮歌词及全屏播放页等多形态表面。
- Avoid refactors that reduce UI smoothness, visual fidelity, foreground-service reliability, or compatibility with lyric provider APIs. / 避免降低交互流畅度、渲染还原度、保活稳定性及在线歌词源兼容性的负向重构。

## Top-Level Package Map (顶级包结构)

```text
com.example.islandlyrics/
  app/          Application entry point, process-level lifecycle, and global initialization.
  core/         Shared infrastructure: preferences, logging, cache helpers, update helpers,
                settings search, platform/theme utilities, community-feed data.
  data/         Legacy shared-data helpers that have not moved into a narrower domain package
                (currently data/mediadata/TitleParser.kt).
  lyrics/       Lyric domain: sources (SuperLyric v40, Lyric Getter, Lyricon, Online, Local),
                importers (Lyrimuse, Lyricify), online fetching, parsing (ELRC, QRC, YRC, Richsync),
                scoring, selection, cache, export, and repository state.
  runtime/      Runtime services and playback integration: foreground services, media monitoring,
                media action control, app launcher, notification policy, and render-mode coordination.
  feature/      Screen-level business features, ViewModels/contracts, and per-screen UI (Home,
                Full-screen Playback, Apple Music, Custom Settings, Parser Rules, Settings, etc.).
  ui/           Reusable UI, themes (Material/Miuix), navigation hosts, overlay renderers (Capsule,
                Super Island with Calibration Wizard, Desktop Floating Lyrics), and view helpers.
  integration/  External or privileged API bridges: Apple Music secure store, Last.fm scrobbler,
                and Shizuku/system privileged integrations.
  rules/        Parser-rule models, matching helpers, and rule-management support.
```

Note: the legacy top-level `service/` package no longer exists — services now live under `runtime/service/`.

## Source Tree (文件结构)

### App source set (`app/src/main/java/com/example/islandlyrics/`)

```text
app/
  IslandLyricsApp.kt                       # Application: logger, LyricRepository, FairMemoryManager,
                                           # theme, lab flags, preference migration, hidden-API bypass,
                                           # memory-pressure cleanup (onTrimMemory)
core/
  cache/AppImageCacheManager.kt            # Coil image-loader singleton & memory-cache control
  feed/                                    # Community feed: ActionHandler, Models, CommunityFeedRepository
  logging/AppLogger.kt                     # Unified file-backed logger (write path)
  logging/LogManager.kt                    # Log rotation / log-file management
  network/OfflineModeManager.kt            # Offline-mode state shared by UI & runtime
  platform/RomUtils.kt                     # ROM detection (HyperOS/ColorOS/…), debug override
  platform/XmsfBypassMode.kt               # Xiaomi service-framework bypass handling
  settings/AppPreferences.kt               # SharedPreferences access & key registry
  settings/BackupCategory.kt               # Backup/restore category model
  settings/SensitiveBackupVault.kt         # Secure token/credential storage for backup & restore
  settings/SettingsBackupManager.kt        # Settings export/import logic
  settings/LabFeatureManager.kt            # Lab (experimental) feature flags
  settings/LauncherAliasManager.kt         # Enable/disable launcher alias (hide app icon)
  settings/search/                         # Settings search: SearchEngine, Registry, Items, Action, HistoryStore
  theme/BitmapPalette.kt                   # Bitmap color palette extraction for UI dynamic coloring
  theme/ColorContrast.kt                   # Dynamic text/background contrast calculation utilities
  theme/ThemeHelper.kt                     # Theme mode + language application
  update/UpdateChecker.kt                  # Update check, version ignore, release lookup
data/
  mediadata/TitleParser.kt                 # Title/artist text parsing helper
rules/
  ParserRule.kt                            # Parser-rule model
  ParserRuleHelper.kt                      # Rule matching, cache, notification-text extraction
lyrics/
  cache/OnlineLyricCacheStore.kt           # Persistent online-lyric cache
  cache/TrackIdentityCacheStore.kt         # Track identity & ISRC / platform ID cache
  export/LyricExporter.kt                  # Lyric export (LRC/…)
  importer/LyricifyDatabaseImporter.kt     # Lyricify SQLite database import & cancel/progress support
  importer/LyrimuseBackupImporter.kt       # Lyrimuse backup configuration & cache import
  local/LocalLrcParser.kt                  # Local .lrc & enhanced ELRC parsing
  local/LocalLyricDirectoryManager.kt      # Scans configured local lyric directories
  online/OnlineLyricFetcher.kt             # Orchestrates online fetch (provider -> parser)
  online/OnlineLyricFetchSnapshotStore.kt  # Snapshot store for online fetch debug & rematch inspection
  online/crypto/                           # NetEase EAPI, QQ payload & QR code decrypt
  online/network/OnlineLyricHttpClient.kt  # Shared HTTP client for lyric APIs
  online/parser/OnlineLyricParser.kt       # Normalizes provider responses -> lyric models (LRC, ELRC)
  online/parser/MusixmatchRichsyncParser.kt# Musixmatch Richsync word-by-word parser
  online/parser/OnlineLyricSidecarMerger.kt# Merges translation/romanization sidecar lines
  online/parser/QrcParser.kt               # QQ Music QRC word-by-word lyric parser
  online/parser/YrcParser.kt               # NetEase YRC word-by-word lyric parser
  online/provider/                         # Apple Music, Kugou, LrcApi, Lrclib, Musixmatch, NetEase, QQ Music, Soda
  online/selection/OnlineLyricSelector.kt  # Scores/selects best candidate
  online/selection/CandidateMatcher.kt     # Candidate title/artist similarity & alias matcher
  online/selection/JapaneseRomajiMatcher.kt# Romaji / Japanese kana phonetic matcher
  online/selection/VersionTag.kt           # Version tag extractor (Live, Acoustic, Remastered, etc.)
  source/                                  # LyricGetterSource, LocalLyricSource, LyriconSource, OnlineLyricSource
  source/SuperLyricSource.kt               # SuperLyric v40 IPC adapter (AIDL communication)
  source/SuperLyricConnection.kt           # SuperLyric IPC service binding manager & reconnect logic
  source/SuperLyricMapper.kt               # Maps SuperLyric v40 protocol entities to domain models
  source/SuperLyricTrace.kt                # Diagnostic tracing for SuperLyric IPC events
  state/LyricRepository.kt                 # Central shared lyric state (singleton)
  state/LyricSidecarStore.kt               # Translation/romanization sidecar state
  state/LyricSourceArbiter.kt              # Decides which source feeds current track
  state/TrackChangeDetector.kt             # Detects track switches
runtime/
  foreground/DelayedStopController.kt      # Delayed service stop after playback pauses
  foreground/LockScreenNotificationPolicy.kt # Determines lock-screen visibility & notification rules
  foreground/LyricForegroundController.kt  # Foreground-service lifecycle & notification maintenance
  media/LyricMediaCommandRouter.kt         # Media command routing (play/pause/next/seek/…)
  media/MediaActionController.kt           # Executes media actions on active controller
  media/MediaAppLauncher.kt                # Resolves and launches playback app from media session/album cover
  media/MediaControllerSelection.kt        # Picks the active MediaController/session
  media/ProgressSyncController.kt          # Progress sync & timing estimation for lyric display
  memory/FairMemoryManager.kt              # OEM fair-memory (Android 16+) integration
  metadata/AppNameResolver.kt              # Resolves playing-app display name
  metadata/MetadataLyricFetchCoordinator.kt# Coordinates lyric fetch from metadata
  metadata/StaticLyricDetector.kt          # Detects static/embedded lyrics
  playingapp/NewPlayingAppActionReceiver.kt# Receiver for "open playing app" action
  playingapp/NewPlayingAppNotifier.kt      # Launches/notifies playing app
  render/RenderModeCoordinator.kt          # Chooses capsule vs Super Island vs floating lyrics
  service/LyricService.kt                  # Main foreground lyric service (orchestrator)
  service/MediaMonitorService.kt           # NotificationListenerService; media observation
  service/CapsulyricTileService.kt         # Quick-Settings tile entry point
integration/
  applemusic/AppleMusicSecureStore.kt      # Secure token & cookie storage for Apple Music web login
  lastfm/                                  # Last.fm client, credentials, scrobble manager, secure store
  shizuku/                                 # Shizuku hook, privileged service, firewall compat, xmsf helper
feature/
  applemusic/                              # Apple Music settings & web login flow (Material + Miuix)
  cache/                                   # Cache management page & entry search (Material + Miuix)
  customsettings/                          # Personalization: App UI / Capsule Notification /
                                           # Desktop (floating) lyrics & right-side calibration
  diagnostics/                             # Diagnostics tool page (Material + Miuix)
  faq/                                     # FAQ page (Material + Miuix)
  lab/                                     # Laboratory page & experimental toggles (Material + Miuix)
  lastfm/                                  # Last.fm scrobbling settings page (Material + Miuix)
  licenses/OpenSourceLicensesActivity.kt   # Open-source licenses page
  locallyrics/                             # Local lyric directory management (Material + Miuix)
  logviewer/                               # Developer log viewer page (Material + Miuix)
  lyric/LyricExportMessage.kt              # Shared lyric-export result message
  main/                                    # MainActivity + home screen + lyric preview +
                                           # Miuix full-screen playback screen, share card exporter & single-line loop
  mediacontrol/                            # Media-control popup (transparent activity with word-by-word progress)
  navigation/TopLevelNavigation.kt         # Top-level destinations (Home/Rules/Settings) & navigation bars
  onlinelyricdebug/                        # Online-lyric rematch/debug page (Material + Miuix)
  oobe/                                    # First-run onboarding & restore (Miuix)
  parserrule/                              # Parser-rule list/editor/source-config pages (Material + Miuix)
  settings/                                # Settings page + About/Community/Third-party import dialogs/search
  update/                                  # Update changelog dialog + markdown parsing
ui/
  material/blur/MaterialBlur.kt            # Material blur helpers (self-wrapped from miuix-blur)
  material/search/MaterialSettingHighlight.kt # Material settings search target highlight
  miuix/                                   # Miuix wrappers: blur (dialog/sheet/scaffold/topbar/
                                           # popup/backdrop/navbar/snackbar), effects, navigation,
                                           # preference, reorderable panel, search highlight, theme
  navigation/                              # BaseActivity, PredictiveBack*, PageStackHost,
                                           # MiuixNavPageStackHost, OverlaySheetHost, MiuixNavOverlaySheetHost, PageExpansion
  overlay/capsule/                         # Live Update capsule renderer (handler/config/intent/render)
  overlay/config/                          # Shared overlay render config (render mode, color mode, defaults)
  overlay/display/                         # Display manager, text-window calc, album-art color,
                                           # timing-gap resolver, display state
  overlay/floating/                        # Floating lyrics window (renderer, chrome, content view,
                                           # draggable frame layout, Miuix panel, permission, timeline, visibility)
  overlay/model/                           # LyricPresentation, UIState, WordProgressCalculator, SecondaryTextMode
  overlay/superisland/                     # Super Island renderer (handler, cache, calibration wizard,
                                           # config, intent, render, state tracker)
  overlay/superisland/calibration/         # Right-side lyric guidance calibration: wizard, overlay, stores & resolvers
  overlay/views/OutlineTextView.kt         # Outlined text view with word-level highlight & visual centering
  preview/SettingsPreviewUtils.kt          # Settings preview helpers (Compose preview)
  theme/material/                          # AppTheme, MaterialTopBarStyle
```

### Debug source set (`app/src/debug/java/com/example/islandlyrics/`)

```text
DebugCenterActivity.kt / DebugCenterScreen.kt / MiuixDebugCenterScreen.kt   # Debug center entry
DebugLyricActivity.kt / DebugLyricViewModel.kt                              # Debug lyric harness
TestPlaybackInjector.kt                                                    # Mock playback injection for test/debugging
feature/debug/DebugLyricScreen.kt / MiuixDebugLyricScreen.kt                # Debug lyric screens
feature/debug/BackgroundBlurDebugActivity.kt                                # Full-screen background blur & shader lab
feature/debug/DebugOklabHalo.kt / DebugOrbitFlow.kt                         # Oklab halo & orbit flow visual prototypes
feature/qqroman/                                                           # QQ romanization debug
feature/superlyricdebug/SuperLyricDebugActivity.kt / SuperLyricDebugScreen.kt # SuperLyric v40 IPC diagnostics
data/lyric/NeteaseRomanFetcher.kt / QqRomanFetcher.kt                       # Romanization fetchers
```

### Other source sets & modules

```text
app/src/main/
  AndroidManifest.xml              # All activities, services, providers, permissions, launcher alias
  res/                             # Strings (values/, values-zh/, values-ja/, values-b+zh+Hant/), themes, drawables, layouts
                                   # (super_island_*, right_calibration_*, lyrics_share.xml), xml rules
app/src/debug/                     # Debug-only activities/screens/res (see above)
app/src/prerelease/                # Prerelease launcher icon & store asset
hidden-api/                        # compileOnly module: hidden-system AIDL stubs
  src/main/java/android/net/IConnectivityManager.java
  src/main/java/android/os/INetworkManagementService.java
gradle/
  libs.versions.toml               # Version catalog (AGP 9.5.1, Kotlin 2.5.0-RC, Miuix 0.9.4)
  scripts/versioning.gradle        # Version name/code derivation
  scripts/signing.gradle           # Signing configuration
  scripts/android-app.gradle       # Shared Android app options (Java 26 source / Java 27 target bytecode)
  wrapper/                         # Gradle wrapper
docs/                              # ARCHITECTURE.md, PRIVACY.md
.github/workflows/                 # CI: main/develop build, changelog release, pr check, issue labeler
screenshots/  perf-artifacts/      # UI screenshots and performance artifacts
```

## Feature & Page Map (功能/页面文件归属)

Navigation model: the app has three top-level destinations — **Home**, **Rules**, **Settings** — defined in `feature/navigation/TopLevelNavigation.kt`. `MainActivity` additionally hosts a single-activity page stack (`AppPage` sealed class inside `feature/main/MainActivity.kt`) for pages that benefit from in-process transitions; most of those pages also have a standalone Activity declared in the manifest (deep-linkable / direct entry). Pages other than OOBE have a Material and a Miuix presentation, chosen at runtime by `ui/miuix/theme/isMiuixEnabled`. OOBE always uses Miuix; a UI style restored from backup is used when entering the main screen.

For Miuix, `PageStackHost` delegates PageSpecific navigation to `MiuixNavPageStackHost` when predictive back is enabled. The adapter keeps the existing page list as the source of truth and maps it to miuix-nav entries with stable per-entry IDs and a fixed root entry. Material and Consistent animation mode continue to use the existing page-stack host. The Miuix parser-rule inline editor uses a nested miuix-nav Modal entry through `MiuixNavOverlaySheetHost`; its editor state is released when the entry leaves the display. Page swipe-to-dismiss remains disabled; Android system predictive back is handled by `NavDisplay`.

| Page (EN / CN) | Entry point | State / contract | Material screen | Miuix screen | Backing logic / notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Home (首页) | `feature/main/MainActivity.kt` (launcher alias target; also `capsulyric://settings` deep link) | — | `feature/main/material/MainScreen.kt` | `feature/main/miuix/MiuixMainScreen.kt` | Lyric preview: `HomeLyricPreviewDisplay.kt`, word highlight: `HomeLyricPreviewHighlight.kt`; media sessions & launcher: `HomeMediaSessions.kt`, `runtime/media/MediaAppLauncher.kt`; hosts in-activity pages (`AppPage`) |
| Full-Screen Lyrics Playback & Share (全屏歌词播放与卡片分享) | in-app page (`AppPage.LyricsPlayback`) opened from Home lyric preview | `feature/main/miuix/LyricsPlaybackState.kt`, `LyricsPlaybackPreferences.kt` | — | `feature/main/miuix/MiuixLyricsPlaybackScreen.kt` | Fluid background & blur (`LyricsPlaybackBackground.kt`, `LyricsPlaybackEffects.kt`), single-line loop (`LyricsPlaybackLoop.kt`), lyric selection (`LyricSelection.kt`, `LyricSelectionSheet.kt`), card rendering & export (`LyricShareCardRenderer.kt`, `LyricShareExporter.kt`), playback settings (`LyricsPlaybackSheets.kt`, `LyricsPlaybackSettings.kt`) |
| Apple Music (Apple Music 设置与网页登录) | `feature/applemusic/AppleMusicSettingsActivity.kt` + in-app page (`AppPage.AppleMusic`) / `AppleMusicLoginActivity.kt` | — | `feature/applemusic/material/AppleMusicSettingsScreen.kt`, `AppleMusicWebLoginScreen.kt` | `feature/applemusic/miuix/MiuixAppleMusicSettingsScreen.kt`, `MiuixAppleMusicWebLoginScreen.kt` | Web login helper (`AppleMusicWebLoginHelper.kt`), secure token store (`integration/applemusic/AppleMusicSecureStore.kt`), provider (`lyrics/online/provider/AppleMusicLyricProvider.kt`, `AppleMusicIsrc.kt`) |
| Rules (规则) | `feature/parserrule/ParserRuleActivity.kt` + in-app page (`AppPage.ParserRules`) | — | `feature/parserrule/material/ParserRuleScreen.kt` | `feature/parserrule/miuix/MiuixParserRuleScreen.kt` | Uses `rules/ParserRuleHelper.kt` |
| Rule Editor (编辑) | `feature/parserrule/ParserRuleEditorActivity.kt` + in-app page (`AppPage.ParserRuleEditor`) | `feature/parserrule/ParserRuleEditorShared.kt` | `feature/parserrule/material/ParserRuleEditorScreen.kt` | `feature/parserrule/miuix/MiuixParserRuleEditorScreen.kt` | Per-source enable/disable of online lyrics; supports template creation |
| Lyric Source Config (歌词源设置) | `feature/parserrule/ParserRuleSourceConfigActivity.kt` | — | (in editor screens) | (in editor screens) | Sub-page of rule editor |
| Settings (设置) | `feature/settings/SettingsActivity.kt` | — | `feature/settings/material/SettingsScreen.kt` | `feature/settings/miuix/MiuixSettingsScreen.kt` | Sections: `LocalLyricDirectoriesSection.kt`, `CommunityDialogs.kt`, `ParserBackupPreviewReader.kt`; search: `core/settings/search/SettingsSearchEngine.kt`, `SettingsSearchRegistry.kt`, highlights in `ui/material/search/` and `ui/miuix/search/`; third-party imports: `ThirdPartyImportController.kt`, `ThirdPartyImportDialogs.kt` |
| Community & About (公告与投票与关于) | in-app page (`AppPage.Community`) | — | `feature/settings/material/CommunityScreen.kt` | `feature/settings/miuix/MiuixCommunityScreen.kt` | Data: `core/feed/CommunityFeedRepository.kt`, `CommunityFeedActionHandler.kt` |
| About (关于) | `feature/settings/AboutActivity.kt` + in-app page (`AppPage.About`) | — | `feature/settings/material/AboutScreen.kt` | `feature/settings/miuix/MiuixAboutScreen.kt` | Update check: `core/update/UpdateChecker.kt`; update UI: `feature/update/` |
| Open Source Licenses (开源许可) | `feature/licenses/OpenSourceLicensesActivity.kt` | — | (AboutLibraries-based) | (AboutLibraries-based) | Depends on `com.mikepenz.aboutlibraries` |
| Personalization (个性化设置) | `feature/customsettings/CustomSettingsActivity.kt` + in-app page (`AppPage.CustomSettings`, `AppPage.CapsuleNotification`, `AppPage.DesktopLyrics`) | `feature/customsettings/CustomSettingsContract.kt`, `CustomSettingsViewModel.kt` | `feature/customsettings/material/CustomSettingsScreen.kt` (also defines `AppUiScreen`, `CapsuleNotificationScreen`, `DesktopLyricsScreen`) | `feature/customsettings/miuix/MiuixCustomSettingsScreen.kt` (defines `MiuixAppUiScreen`, `MiuixCapsuleNotificationScreen`, `MiuixDesktopLyricsScreen`) | Customization for App UI, capsule notification, desktop floating lyrics, and Super Island right-side calibration (`RightCalibrationSheet.kt`, `RightManualLimitSetting.kt`, `MiuixRightCalibrationContent.kt`) |
| Floating Lyrics Settings (桌面歌词设置) | in-app sub-page / `AppPage.DesktopLyrics` | — | `feature/customsettings/material/FloatingLyricsSettingsSubScreen.kt` | `feature/customsettings/miuix/MiuixFloatingLyricsSettingsSubScreen.kt` | Real-time preview: `FloatingLyricsSettingsPreview.kt`; editable color section: `material/MaterialEditableColorSection.kt`, `miuix/MiuixEditableColorSection.kt` |
| Cache Management (缓存管理) | `feature/cache/CacheManagementActivity.kt` + in-app page (`AppPage.CacheManagement`) | `feature/cache/CacheManagementViewModel.kt`, `feature/cache/CacheEntrySearch.kt` | `feature/cache/material/CacheManagementScreen.kt` | `feature/cache/miuix/MiuixCacheManagementScreen.kt` | Operates on `lyrics/cache/OnlineLyricCacheStore.kt`, `core/cache/AppImageCacheManager.kt` |
| Diagnostics (诊断工具) | `feature/diagnostics/DiagnosticsActivity.kt` + in-app page (`AppPage.Diagnostics`) | — | `feature/diagnostics/material/DiagnosticsScreen.kt` | `feature/diagnostics/miuix/MiuixDiagnosticsScreen.kt` | Exposes logs, service state, lab entry |
| Log Viewer (开发者日志) | `feature/logviewer/LogViewerActivity.kt` + in-app page (`AppPage.LogViewer`) | — | `feature/logviewer/material/LogViewerScreen.kt` | `feature/logviewer/miuix/MiuixLogViewerScreen.kt` | Reads `core/logging/LogManager.kt` |
| Laboratory (实验室) | `feature/lab/LabActivity.kt` + in-app page (`AppPage.Lab`) | — | `feature/lab/material/LabScreen.kt` | `feature/lab/miuix/MiuixLabScreen.kt` | Flags: `core/settings/LabFeatureManager.kt` |
| Online Lyric Rematch (在线歌词重匹配) | `feature/onlinelyricdebug/OnlineLyricDebugActivity.kt` + in-app page (`AppPage.OnlineLyricDebug`) | `feature/onlinelyricdebug/OnlineLyricDebugViewModel.kt` | `feature/onlinelyricdebug/material/OnlineLyricDebugScreen.kt` | `feature/onlinelyricdebug/miuix/MiuixOnlineLyricDebugScreen.kt` | Drives `lyrics/online/OnlineLyricFetcher.kt` with live search term overrides |
| Last.fm | `feature/lastfm/LastFmSettingsActivity.kt` + in-app page (`AppPage.LastFm`) | — | `feature/lastfm/material/LastFmSettingsScreen.kt` | `feature/lastfm/miuix/MiuixLastFmSettingsScreen.kt` | Backend: `integration/lastfm/` (client, scrobble manager, secure store) |
| Lyrics Directories (歌词目录) | `feature/settings/LocalLyricDirectoriesSection.kt` (settings) + `feature/locallyrics/LocalLyricDirectoryActivity.kt` (per-directory) + in-app pages (`AppPage.LocalLyricDirectories`, `AppPage.LocalLyricDirectory`) | — | `feature/settings/material/LocalLyricDirectoriesScreen.kt`, `feature/locallyrics/material/LocalLyricDirectoryScreen.kt` | `feature/settings/miuix/MiuixLocalLyricDirectoriesScreen.kt`, `feature/locallyrics/miuix/MiuixLocalLyricDirectoryScreen.kt` | Uses `lyrics/local/LocalLyricDirectoryManager.kt` |
| FAQ | `feature/faq/FAQActivity.kt` + in-app page (`AppPage.Faq`) | — | `feature/faq/material/FAQScreen.kt` | `feature/faq/miuix/MiuixFAQScreen.kt` | Static Q&A content |
| OOBE (首次引导) | `feature/oobe/OobeActivity.kt` | — | — | `feature/oobe/miuix/MiuixOobeScreen.kt` | First-run welcome/permissions and backup restore; always uses Miuix |
| Media Control (媒体控制弹窗) | `feature/mediacontrol/MediaControlActivity.kt` (transparent, singleInstance) | — | `feature/mediacontrol/material/MediaControlDialog.kt` | `feature/mediacontrol/miuix/MiuixMediaControlDialog.kt` | Commands via `runtime/media/LyricMediaCommandRouter.kt`; word-by-word progress display |
| Update Dialog (更新弹窗) | shown from Home/About | — | `feature/update/material/UpdateDialog.kt` | `feature/update/miuix/MiuixUpdateDialog.kt` | Changelog parsing: `feature/update/UpdateParser.kt`, `feature/update/UpdateMarkdown.kt` |

Shared activity plumbing (theme wrappers, predictive back, page-stack host): `ui/navigation/BaseActivity.kt`, `ui/navigation/PredictiveBackActivity.kt`, `ui/navigation/PredictiveBackAnimation.kt`, `ui/navigation/PageStackHost.kt`, `ui/navigation/MiuixNavPageStackHost.kt`, `ui/navigation/OverlaySheetHost.kt`, `ui/navigation/MiuixNavOverlaySheetHost.kt`, `ui/navigation/PageExpansion.kt`.

## Runtime Services & Background Components (运行时服务)

| Component | Files | Responsibility |
| :--- | :--- | :--- |
| LyricService (foreground) | `runtime/service/LyricService.kt` | Main orchestrator: owns foreground lifecycle, wires sources → repository → renderers, media commands, progress sync, scrobbling, playing-app actions. Backed by `runtime/foreground/`, `runtime/media/`, `runtime/metadata/`, `runtime/render/`. |
| Lock-Screen Notification Policy | `runtime/foreground/LockScreenNotificationPolicy.kt` | Manages notification visibility, public/private content masking, and lifecycle rules across lock-screen states. |
| MediaMonitorService | `runtime/service/MediaMonitorService.kt` | `NotificationListenerService`: observes media notifications/sessions, extracts title/artist/album/art via `rules/ParserRuleHelper.kt`, feeds `lyrics/state/LyricRepository.kt`. |
| Media App Launcher | `runtime/media/MediaAppLauncher.kt` | Resolves the foreground playback intent or launch intent for the active media session (e.g. clicking album art to open player). |
| Quick-Settings Tile | `runtime/service/CapsulyricTileService.kt` | Alternative entry when the launcher icon is hidden (see `core/settings/LauncherAliasManager.kt`). |
| Playing-app action | `runtime/playingapp/NewPlayingAppActionReceiver.kt`, `NewPlayingAppNotifier.kt` | Handles "open playing app" clicks from the overlay surfaces. |
| Fair memory | `runtime/memory/FairMemoryManager.kt` | OEM fair-memory (Android 16+) integration; complements `app/IslandLyricsApp.kt` `onTrimMemory`. |

## Lyric Pipeline (歌词流水线)

```text
Media notifications / media sessions / SuperLyric v40 IPC / local files / third-party backups
        |
        v
lyrics.source.*  (SuperLyricSource, LyricGetterSource, LyriconSource,
                   OnlineLyricSource, LocalLyricSource)
        |
        +---> lyrics.source.SuperLyricConnection / SuperLyricMapper / SuperLyricTrace (v40 IPC)
        |
        v
lyrics.importer.* (LyrimuseBackupImporter, LyricifyDatabaseImporter)
        |
        v
lyrics.online.*  (OnlineLyricFetcher -> provider -> parser -> selector)
  online/provider/{AppleMusic, Kugou, LrcApi, Lrclib, Musixmatch, Netease, QqMusic, Soda}LyricProvider.kt
  online/crypto/  (NeteaseEapiCrypto, QqLyricPayloadDecoder, QqQrcDecrypter)
  online/network/OnlineLyricHttpClient.kt
  online/parser/  (OnlineLyricParser [LRC, ELRC], MusixmatchRichsyncParser, QrcParser, YrcParser,
                   OnlineLyricSidecarMerger)
  online/selection/ (OnlineLyricSelector, CandidateMatcher, JapaneseRomajiMatcher, VersionTag)
lyrics.local.*   (LocalLrcParser, LocalLyricDirectoryManager)
lyrics.cache/ (OnlineLyricCacheStore, TrackIdentityCacheStore, OnlineLyricFetchSnapshotStore)
lyrics.export/LyricExporter.kt
        |
        v
lyrics.state.*   (LyricRepository, LyricSidecarStore, LyricSourceArbiter,
                   TrackChangeDetector)  <-- shared central state
        |
        +--> runtime notification updates (runtime/service, ui/overlay/*)
        +--> ui.overlay capsule / Super Island / desktop floating renderers
        +--> feature screens (Home preview, Full-screen playback, Debug tools)
```

Key pipeline stages:

1. **Source Collection & Arbitration**: `lyrics/state/LyricSourceArbiter.kt` decides whether the active track is fed by privileged sources (`SuperLyricSource` via SuperLyric v40 IPC, `LyricGetterSource`, `LyriconSource`), configured `LocalLyricSource`, or `OnlineLyricSource`.
2. **Third-Party Import**: `lyrics/importer/LyrimuseBackupImporter.kt` and `LyricifyDatabaseImporter.kt` convert external backup archives and SQLite databases into local caches and rule configurations.
3. **Online Providers & Crypto**: 8 online providers (NetEase, QQ Music, Kugou, Soda, LRCLIB, LrcApi, Musixmatch, Apple Music) fetch lyrics across multiple APIs, decrypting payloads via `NeteaseEapiCrypto`, `QqLyricPayloadDecoder`, and `QqQrcDecrypter`.
4. **Format Parsing**: Normalizes standard LRC, Enhanced LRC (ELRC with `<mm:ss.xx>` word-level timestamps), Musixmatch Richsync JSON, QQ Music QRC, and NetEase YRC formats into unified sentence and word timelines.
5. **Sidecar Merging & Caching**: Merges translation and romanization sidecars via `OnlineLyricSidecarMerger.kt` into `LyricSidecarStore.kt`; persists results in `OnlineLyricCacheStore.kt` and track identities (ISRC / platform IDs) in `TrackIdentityCacheStore.kt`.
6. **Selection & Scoring**: `CandidateMatcher` evaluates title/artist similarity, handles Japanese kana/Romaji via `JapaneseRomajiMatcher`, filters version tags (`VersionTag`), and scores candidates in `OnlineLyricSelector`.

## UI Overlay Surfaces (悬浮层/系统表面)

| Surface | Root package | Key files | Description / Highlights |
| :--- | :--- | :--- | :--- |
| Live Update capsule (Android 16+) | `ui/overlay/capsule/` | `LyricCapsuleHandler.kt`, `config/LyricCapsulePreferencesCache.kt`, `config/LiveUpdateTextLimitConfig.kt`, `intent/LyricCapsuleIntentFactory.kt`, `render/LyricCapsuleNotificationBuilder.kt`, `render/LyricCapsuleDynamicIconCache.kt`, `render/AdvancedIconRenderer.kt` | Renders dynamic capsule notifications on Android 16+ status bar. Supports word-by-word visual center (`WordProgressCalculator.kt`), One UI color mode, and custom render modes (`CapsuleRenderMode.kt`). |
| Xiaomi Super Island (超级岛) | `ui/overlay/superisland/` | `SuperIslandHandler.kt`, `SuperIslandNotificationDispatcher.kt`, `cache/`, `config/` (prefs, color source, dual-line mode, 3-line lyric layout, text limits, templates 2 & 18, migration), `intent/SuperIslandIntentFactory.kt`, `render/` (notification builder, RemoteViews factory, focus builders, text resolvers), `state/SuperIslandRenderStateTracker.kt` | HyperOS Super Island integration. Supports dual-line and 3-line card layouts (`SuperIslandLyricLayout.kt`), collapsed progress ring (`SuperIslandProgressBitmapCache.kt`), dynamic color extraction, and Template 2 / 18 focus layouts. |
| Super Island Calibration Subsystem (超级岛引导校准) | `ui/overlay/superisland/calibration/` | `RightCalibrationWizard.kt`, `RightCalibrationOverlay.kt`, `MiuixRightCalibrationOverlay.kt`, `RightCalibrationStore.kt`, `RightWizardController.kt`, `RightWizardCheckpointStore.kt`, `CalibratedLyricWindowResolver.kt`, `RightTextCalibration.kt` | Automated and manual right-side lyric calibration wizard for camera cutouts. Features step checkpoints with resume support, live preview overlay, and calibrated window calculation. |
| Desktop Floating Lyrics (桌面悬浮歌词) | `ui/overlay/floating/` | `FloatingLyricsRenderer.kt`, `FloatingLyricsChrome.kt`, `FloatingLyricsContentView.kt`, `FloatingLyricsActionController.kt`, `FloatingLyricsDraggableFrameLayout.kt`, `FloatingLyricsMiuixPanel.kt`, `FloatingLyricsOverlayPermission.kt`, `FloatingLyricsSettingsPreview.kt`, `FloatingLyricsStyleStore.kt`, `FloatingLyricsTimeline.kt`, `FloatingLyricsVisibility.kt`, `FloatingLyricsWindowPositionStore.kt`, `FloatingWordPlayback.kt` | Modernized desktop floating window. Features collapsible playback controls, safe edge dragging bounds, permission checks, instant settings preview, timeline interpolation, and word-by-word playback. |
| Shared Overlay State & Models | `ui/overlay/model/`, `ui/overlay/config/`, `ui/overlay/display/` | `LyricPresentation.kt`, `UIState.kt`, `WordProgressCalculator.kt`, `SecondaryTextMode.kt`, `CapsuleRenderMode.kt`, `LyricTextDisplayMode.kt`, `OneUiCapsuleColorMode.kt`, `OverlayRenderDefaults.kt`, `LyricDisplayManager.kt`, `LyricTextWindowCalculator.kt`, `ParsedLyricDisplayState.kt`, `TimingGapDisplayResolver.kt`, `AlbumArtColorExtractor.kt`, `OverlayDisplayConfig.kt`, `views/OutlineTextView.kt` | Central presentation models, text window calculation, outlined text with word-level highlight & visual centering, album art color palette extraction, and gap-filler display state. |

## Runtime Data Flow (运行时数据流)

```text
Media notifications / media sessions / SuperLyric IPC
        |
        v
runtime.service (LyricService / MediaMonitorService)
        |
        v
rules + lyrics.source + lyrics.online/local/cache/importers
        |
        v
lyrics.state repository (LyricRepository)
        |
        +--> runtime foreground notification updates
        +--> ui.overlay capsule / Super Island / floating renderers
        +--> feature screens (Home preview, Full-screen playback, Diagnostics)
```

Main flow:

1. Runtime services (`MediaMonitorService`, `LyricService`) observe media sessions and notifications, or receive SuperLyric IPC payloads via `SuperLyricConnection`.
2. Parser rules and lyric sources extract track metadata (title, artist, album, duration, ISRC) and determine candidate retrieval strategies.
3. Online, local, cache, or imported sources normalize lyrics into domain sentence/word timelines and sidecars in `LyricRepository`.
4. `ProgressSyncController` estimates playback progress and dispatches timing ticks to overlay surfaces and feature screens.
5. Runtime renderers update Live Update capsule, Super Island, and desktop floating lyrics surfaces in lockstep.
6. Feature screens observe shared repository state for settings, home previews, full-screen playback with fluid background blur, and debugging tools.

## External API Boundaries (外部集成边界)

Capsulyric integrates with several external or privileged APIs:

- **SuperLyric API v40**: `lyrics/source/SuperLyricSource.kt`, `SuperLyricConnection.kt`, `SuperLyricMapper.kt`, `SuperLyricTrace.kt`. Full support for protocol v40 with IPC reconnect logic and keep rules for Parcelable transport.
- **Lyric Getter API**: `lyrics/source/LyricGetterSource.kt`.
- **Lyricon subscriber API**: `lyrics/source/LyriconSource.kt`.
- **Online Lyric Providers**: NetEase, QQ Music, Kugou, Soda, LRCLIB, LrcApi, Musixmatch, Apple Music (`lyrics/online/provider/`).
- **Apple Music Web Session**: `feature/applemusic/`, `integration/applemusic/AppleMusicSecureStore.kt`, `AppleMusicLyricProvider.kt`. Manages MusicKit web authentication, token cookies, and ISRC track matching.
- **Musixmatch API**: `lyrics/online/provider/MusixmatchLyricProvider.kt`, `MusixmatchTokenStore.kt`, `MusixmatchRichsyncParser.kt`. Word-by-word richsync lyric support.
- **Third-Party Import Formats**: Lyrimuse backup archives (`LyrimuseBackupImporter.kt`), Lyricify SQLite database (`LyricifyDatabaseImporter.kt`).
- **Last.fm Scrobbling**: `integration/lastfm/` (`LastFmApiClient.kt`, `LastFmCredentials.kt`, `LastFmScrobbleManager.kt`, `LastFmSecureStore.kt`).
- **HyperNotification / Focus API (Super Island)**: `ui/overlay/superisland/`.
- **Shizuku & System-Service Bridges**: `integration/shizuku/` (`ShizukuHook.kt`, `PrivilegedServiceImpl.kt`, `ShizukuUtil.kt`, `FirewallCompat.kt`, `XmsfNetworkHelper.kt`, `ShizukuUserServiceRecycler.kt`).
- **Hidden API Bypass**: `app/IslandLyricsApp.kt` (via `HiddenApiBypass`), `hidden-api/` module (compileOnly AIDL stubs).

Keep API-specific keep rules and Parcelable compatibility requirements in build/R8 configuration. Do not remove API keep rules unless the upstream contract is verified.

## UI Architecture (UI 架构规范)

The app supports both Material 3 and Miuix UI presentation layers. Prefer this decoupled shape:

```text
Feature contract / ViewModel / shared state
        |
        +--> Material 3 screen (`material/`)
        +--> Miuix screen (`miuix/`)
```

Guidelines:

- Keep business decisions out of Material/Miuix component files when a shared ViewModel or contract can own them.
- **Miuix 0.9.4 Conventions**:
  - Use `MiuixBlurScaffold`, `MiuixBlurTopAppBar`, `MiuixBlurBottomSheet`, `MiuixBlurDialog`, `MiuixBlurPopupHost`, and `MiuixBlurNavigationBar` for blur consistency.
  - Wrap dropdowns and popup menus in `BlurBackdrop` / `MiuixBlurPopupHost`.
  - Delegate page stack transitions via `PageStackHost` / `MiuixNavPageStackHost` with predictive back gestures enabled.
  - Present nested sheets using `OverlaySheetHost` / `MiuixNavOverlaySheetHost`.
- **Search Highlighting**:
  - Connect settings items to `SettingsSearchRegistry.kt`.
  - Support instant navigation and visual target highlighting via `MiuixSettingHighlight` and `MaterialSettingHighlight`.
- **Smoothness & Fidelity**:
  - Avoid changing visual behavior as a side effect of package cleanup or APK-size work.
  - Treat animation smoothness, media-control responsiveness, and notification/surface fidelity as product requirements.
  - When replacing UI dependencies, verify the resulting UI on device or with screenshots before accepting changes.

## Refactoring Rules (重构准则)

- Prefer moving code toward existing ownership boundaries instead of creating broad utility packages.
- Keep provider, parser, scorer, and importer logic separated in `lyrics`.
- Keep notification control and service lifecycle logic in `runtime`.
- Keep settings/custom settings state in ViewModels/contracts rather than composables.
- Keep overlay rendering code split by target surface: capsule, Super Island, floating views, shared config/model.
- Add a page: create `feature/<name>/{Activity, Contract, ViewModel}` plus `material/` and `miuix/` screens; register the Activity in `app/src/main/AndroidManifest.xml` and (if in-app) add an `AppPage` in `feature/main/MainActivity.kt`.
- Do not introduce a new module split until package boundaries are stable enough to make module APIs meaningful.

## Build Layout (构建布局与工具链)

The app module build script delegates reusable concerns to Gradle scripts:

```text
app/build.gradle.kts            # Dependencies, plugin application, test dependencies
hidden-api/build.gradle.kts     # compileOnly hidden-API stubs module
gradle/scripts/versioning.gradle# Git commit count, hash, version name derivation
gradle/scripts/signing.gradle   # Keystore signing configuration
gradle/scripts/android-app.gradle # Shared Android app options, SDK versions & JVM targets
gradle/libs.versions.toml       # Version catalog
```

Key build configuration highlights:
- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 35`.
- `sourceCompatibility = JavaVersion.VERSION_26`, `targetCompatibility = JavaVersion.VERSION_27` (supporting Java 27 bytecode output).
- Android Gradle Plugin (AGP) `9.5.1`, Kotlin `2.5.0-RC`, Miuix `0.9.4`.

Keep versioning, signing, and Android app options separate. A future `build-logic` convention plugin can replace these scripts once the build shape stops changing frequently.

## Debug & Tooling (调试与工程工具)

- `app/src/debug/` contains a debug-only center (`DebugCenterActivity` / `MiuixDebugCenterScreen`), a lyric rendering harness (`DebugLyricActivity`), mock playback injection (`TestPlaybackInjector.kt`), QQ romanization debug screens, SuperLyric v40 IPC diagnostics (`SuperLyricDebugActivity`), and full-screen background blur / shader experimentation labs (`BackgroundBlurDebugActivity`, `DebugOklabHalo`, `DebugOrbitFlow`); it is excluded from release builds.
- `screenshots/` and `perf-artifacts/` store performance captures and UI screenshots used for verification.
