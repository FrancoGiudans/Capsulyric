# Architecture

This document describes the current package boundaries and runtime data flow for Capsulyric. It is intentionally lightweight: use it as a map for maintenance and refactoring, not as a full design specification.

本文档说明 Capsulyric 当前的包边界与运行时数据流。它刻意保持轻量：用于维护和重构时快速定位职责，而不是完整设计说明书。

> This document is also the **file-level map**: each feature/page, runtime service, lyric-pipeline stage, and overlay surface lists the concrete files that own it. Unless noted otherwise, paths below are relative to `app/src/main/java/com/example/islandlyrics/`. / 本文档同时充当**文件级地图**：每个功能/页面、运行时服务、歌词流水线阶段与悬浮层表面都会列出负责它的具体文件。除特别说明外，下文路径均相对于 `app/src/main/java/com/example/islandlyrics/`。

## Goals

- Keep service/runtime code independent from screen UI.
- Keep lyric fetching, parsing, scoring, cache, and repository state in lyric-focused packages.
- Keep Material and Miuix UI as presentation choices over shared feature state.
- Keep external API integrations behind narrow adapters.
- Avoid refactors that reduce UI smoothness, visual fidelity, foreground-service reliability, or compatibility with lyric provider APIs.

## Top-Level Package Map

```text
com.example.islandlyrics/
  app/          Application entry point and process-level initialization.
  core/         Shared infrastructure: preferences, logging, cache helpers, update helpers,
                platform/theme utilities, community-feed data.
  data/         Legacy shared-data helpers that have not moved into a narrower domain package
                (currently only data/mediadata/TitleParser.kt).
  lyrics/       Lyric domain: sources, online fetching, parsing, scoring, local lyrics, cache,
                export, repository state.
  runtime/      Runtime services and playback integration: foreground services, media monitoring,
                notification control, render-mode coordination.
  feature/      Screen-level business features, ViewModels/contracts, and per-screen UI.
  ui/           Reusable UI, themes, navigation hosts, overlay renderers (capsule, Super Island,
                floating lyrics), and view helpers.
  integration/  External or privileged API bridges: Last.fm and Shizuku/system integrations.
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
  feed/CommunityFeedRepository.kt          # Community / announcement feed data
  logging/AppLogger.kt                     # Unified file-backed logger (write path)
  logging/LogManager.kt                    # Log rotation / log-file management
  network/OfflineModeManager.kt            # Offline-mode state shared by UI & runtime
  platform/RomUtils.kt                     # ROM detection (HyperOS/ColorOS/…), debug override
  platform/XmsfBypassMode.kt               # Xiaomi service-framework bypass handling
  settings/AppPreferences.kt               # SharedPreferences access & key registry
  settings/BackupCategory.kt               # Backup/restore category model
  settings/SettingsBackupManager.kt        # Settings export/import logic
  settings/LabFeatureManager.kt            # Lab (experimental) feature flags
  settings/LauncherAliasManager.kt         # Enable/disable launcher alias (hide app icon)
  theme/ThemeHelper.kt                     # Theme mode + language application
  update/UpdateChecker.kt                  # Update check, version ignore, release lookup
data/
  mediadata/TitleParser.kt                 # Title/artist text parsing helper
rules/
  ParserRule.kt                            # Parser-rule model
  ParserRuleHelper.kt                      # Rule matching, cache, notification-text extraction
  WhitelistHelper.kt                       # App whitelist matching
  WhitelistItem.kt                         # Whitelist item model
lyrics/
  cache/OnlineLyricCacheStore.kt           # Persistent online-lyric cache
  export/LyricExporter.kt                  # Lyric export (LRC/…)
  local/LocalLrcParser.kt                  # Local .lrc parsing
  local/LocalLyricDirectoryManager.kt      # Scans configured local lyric directories
  online/OnlineLyricFetcher.kt             # Orchestrates online fetch (provider -> parser)
  online/crypto/                           # NetEase EAPI, QQ payload/QR code decrypt
  online/network/OnlineLyricHttpClient.kt  # Shared HTTP client for lyric APIs
  online/parser/OnlineLyricParser.kt       # Normalizes provider responses -> lyric models
  online/parser/OnlineLyricSidecarMerger.kt# Merges translation/romanization sidecar lines
  online/provider/                         # Kugou, LrcApi, Lrclib, NetEase, QQ Music, Soda
  online/selection/OnlineLyricSelector.kt  # Scores/selects best candidate
  source/                                  # LyricGetterSource, LocalLyricSource, LyriconSource,
                                           # OnlineLyricSource, SuperLyricSource (source adapters)
  state/LyricRepository.kt                 # Central shared lyric state (singleton)
  state/LyricSidecarStore.kt               # Translation/romanization sidecar state
  state/LyricSourceArbiter.kt              # Decides which source feeds current track
  state/TrackChangeDetector.kt             # Detects track switches
runtime/
  foreground/DelayedStopController.kt      # Delayed service stop after playback pauses
  foreground/LyricForegroundController.kt  # Foreground-service lifecycle/notification
  media/LyricMediaCommandRouter.kt         # Media command routing (play/pause/next/…)
  media/MediaActionController.kt           # Executes media actions on active controller
  media/MediaControllerSelection.kt        # Picks the active MediaController/session
  media/ProgressSyncController.kt          # Progress sync & timing for lyric display
  memory/FairMemoryManager.kt              # OEM fair-memory (Android 16+) integration
  metadata/AppNameResolver.kt              # Resolves playing-app display name
  metadata/MetadataLyricFetchCoordinator.kt# Coordinates lyric fetch from metadata
  metadata/StaticLyricDetector.kt          # Detects static/embedded lyrics
  playingapp/NewPlayingAppActionReceiver.kt# Receiver for "open playing app" action
  playingapp/NewPlayingAppNotifier.kt      # Launches/notifies playing app
  render/RenderModeCoordinator.kt          # Chooses capsule vs Super Island vs floating
  service/LyricService.kt                  # Main foreground lyric service (orchestrator)
  service/MediaMonitorService.kt           # NotificationListenerService; media observation
  service/CapsulyricTileService.kt         # Quick-Settings tile entry point
feature/
  cache/                                   # Cache management page
  customsettings/                          # Personalization: App UI / Capsule Notification /
                                           # Desktop (floating) lyrics sub-screens
  diagnostics/                             # Diagnostics tool page
  faq/                                     # FAQ page
  lab/                                     # Laboratory page
  lastfm/                                  # Last.fm settings page
  licenses/OpenSourceLicensesActivity.kt   # Open-source licenses page
  locallyrics/                             # Local lyric directory page
  logviewer/                               # Developer log viewer page
  lyric/LyricExportMessage.kt              # Shared lyric-export result message
  main/                                    # MainActivity + home screen + lyric preview
  mediacontrol/                            # Media-control popup (transparent activity)
  navigation/TopLevelNavigation.kt         # Top-level destinations (Home/Rules/Settings) & bars
  onlinelyricdebug/                        # Online-lyric rematch/debug page
  oobe/                                    # First-run onboarding
  parserrule/                              # Parser-rule list/editor/source-config pages
  settings/                                # Settings page + About/Community/directories sections
  update/                                  # Update changelog dialog + markdown parsing
ui/
  material/blur/MaterialBlur.kt            # Material blur helpers (self-wrapped from miuix-blur)
  miuix/                                   # Miuix wrappers: blur (dialog/sheet/scaffold/topbar/
                                           # popup/backdrop), effects, navigation, preference,
                                           # reorderable panel, theme
  navigation/                              # BaseActivity, PredictiveBack*, PageStackHost,
                                           # OverlaySheetHost, LayeredPagerTransition
  overlay/capsule/                         # Live Update capsule renderer (handler/config/intent/render)
  overlay/config/                          # Shared overlay render config & defaults
  overlay/display/                         # Display manager, text-window calc, album-art color,
                                           # timing-gap resolver, display state
  overlay/floating/                        # Floating lyrics window (renderer/chrome/drag/config)
  overlay/model/                           # LyricPresentation, UIState, WordProgressCalculator
  overlay/superisland/                     # Super Island renderer (handler/cache/config/intent/
                                           # render/state)
  overlay/views/OutlineTextView.kt         # Outlined text view helper
  preview/SettingsPreviewUtils.kt          # Settings preview helpers (Compose preview)
  theme/material/                          # AppTheme, MaterialTopBarStyle
```

### Debug source set (`app/src/debug/java/com/example/islandlyrics/`)

```text
DebugCenterActivity.kt / DebugCenterScreen.kt / MiuixDebugCenterScreen.kt   # Debug center entry
DebugLyricActivity.kt / DebugLyricViewModel.kt                              # Debug lyric harness
feature/debug/DebugLyricScreen.kt / MiuixDebugLyricScreen.kt                # Debug lyric screens
feature/qqroman/                                                           # QQ romanization debug
data/lyric/NeteaseRomanFetcher.kt / QqRomanFetcher.kt                       # Romanization fetchers
```

### Other source sets & modules

```text
app/src/main/
  AndroidManifest.xml              # All activities, services, providers, permissions, launcher alias
  res/                             # Strings (values/, values-zh/), themes, drawables, layouts
                                   # (super_island_*, dialog_hide_launcher_warning.xml), xml rules
app/src/debug/                     # Debug-only activities/screens/res (see above)
app/src/prerelease/                # Prerelease launcher icon & store asset
hidden-api/                        # compileOnly module: hidden-system AIDL stubs
  src/main/java/android/net/IConnectivityManager.java
  src/main/java/android/os/INetworkManagementService.java
gradle/
  libs.versions.toml               # Version catalog
  scripts/versioning.gradle        # Version name/code derivation
  scripts/signing.gradle           # Signing configuration
  scripts/android-app.gradle       # Shared Android app options
  wrapper/                         # Gradle wrapper
tools/                             # Build/dev tooling (autobuild scripts, autobuild-ui web console,
                                   # capture-notify-trace.ps1)
docs/                              # ARCHITECTURE.md, PRIVACY.md
.github/workflows/                 # CI: main/develop build, changelog release, issue labeler
screenshots/  perf-artifacts/      # UI screenshots and performance artifacts
```

## Feature & Page Map (功能/页面文件归属)

Navigation model: the app has three top-level destinations — **Home**, **Rules**, **Settings** — defined in `feature/navigation/TopLevelNavigation.kt`. `MainActivity` additionally hosts a single-activity page stack (`AppPage` sealed class inside `feature/main/MainActivity.kt`) for pages that benefit from in-process transitions; most of those pages also have a standalone Activity declared in the manifest (deep-linkable / direct entry). Every page has a Material and a Miuix presentation, chosen at runtime by `ui/miuix/theme/isMiuixEnabled`.

| Page (EN / CN) | Entry point | State / contract | Material screen | Miuix screen | Backing logic / notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Home (首页) | `feature/main/MainActivity.kt` (launcher alias target; also `capsulyric://settings` deep link) | — | `feature/main/material/MainScreen.kt` | `feature/main/miuix/MiuixMainScreen.kt` | Lyric preview: `feature/main/HomeLyricPreviewDisplay.kt`; observes `lyrics/state/LyricRepository.kt` + `ui/overlay/model/UIState.kt`; hosts in-activity pages (AppPage) |
| Rules (规则) | `feature/parserrule/ParserRuleActivity.kt` | — | `feature/parserrule/material/ParserRuleScreen.kt` | `feature/parserrule/miuix/MiuixParserRuleScreen.kt` | Uses `rules/ParserRuleHelper.kt` |
| Rule Editor (编辑) | `feature/parserrule/ParserRuleEditorActivity.kt` | `feature/parserrule/ParserRuleEditorShared.kt` | `feature/parserrule/material/ParserRuleEditorScreen.kt` | `feature/parserrule/miuix/MiuixParserRuleEditorScreen.kt` | Per-source enable/disable of online lyrics |
| Lyric Source Config (歌词源设置) | `feature/parserrule/ParserRuleSourceConfigActivity.kt` | — | (in editor screens) | (in editor screens) | Sub-page of rule editor |
| Settings (设置) | `feature/settings/SettingsActivity.kt` | — | `feature/settings/material/SettingsScreen.kt` | `feature/settings/miuix/MiuixSettingsScreen.kt` | Sections: `feature/settings/LocalLyricDirectoriesSection.kt`, `feature/settings/CommunityDialogs.kt`, `feature/settings/ParserBackupPreviewReader.kt` |
| Community & About (公告与投票与关于) | in-app page (AppPage.Community) | — | `feature/settings/material/CommunityScreen.kt` | `feature/settings/miuix/MiuixCommunityScreen.kt` | Data: `core/feed/CommunityFeedRepository.kt` |
| About (关于) | `feature/settings/AboutActivity.kt` + in-app page (AppPage.About) | — | `feature/settings/material/AboutScreen.kt` | `feature/settings/miuix/MiuixAboutScreen.kt` | Update check: `core/update/UpdateChecker.kt`; update UI: `feature/update/` |
| Open Source Licenses (开源许可) | `feature/licenses/OpenSourceLicensesActivity.kt` | — | (AboutLibraries-based) | (AboutLibraries-based) | Depends on `com.mikepenz.aboutlibraries` |
| Personalization (个性化设置) | `feature/customsettings/CustomSettingsActivity.kt` + in-app page (AppPage.CustomSettings) | `feature/customsettings/CustomSettingsContract.kt`, `CustomSettingsViewModel.kt` | `feature/customsettings/material/CustomSettingsScreen.kt` (also defines `AppUiScreen`, `CapsuleNotificationScreen`, `DesktopLyricsScreen`) | `feature/customsettings/miuix/MiuixCustomSettingsScreen.kt` (defines `MiuixAppUiScreen`, `MiuixCapsuleNotificationScreen`, `MiuixDesktopLyricsScreen`) | Customization for App UI / capsule notification / floating lyrics |
| Floating Lyrics Settings (桌面歌词设置) | in-app sub-page | — | `feature/customsettings/material/FloatingLyricsSettingsSubScreen.kt` | `feature/customsettings/miuix/MiuixFloatingLyricsSettingsSubScreen.kt` | Editable color section: `material/MaterialEditableColorSection.kt`, `miuix/MiuixEditableColorSection.kt` |
| Cache Management (缓存管理) | `feature/cache/CacheManagementActivity.kt` + in-app page (AppPage.CacheManagement) | `feature/cache/CacheManagementViewModel.kt`, `feature/cache/CacheEntrySearch.kt` | `feature/cache/material/CacheManagementScreen.kt` | `feature/cache/miuix/MiuixCacheManagementScreen.kt` | Operates on `lyrics/cache/OnlineLyricCacheStore.kt`, `core/cache/AppImageCacheManager.kt` |
| Diagnostics (诊断工具) | `feature/diagnostics/DiagnosticsActivity.kt` + in-app page (AppPage.Diagnostics) | — | `feature/diagnostics/material/DiagnosticsScreen.kt` | `feature/diagnostics/miuix/MiuixDiagnosticsScreen.kt` | Exposes logs, service state, lab entry |
| Log Viewer (开发者日志) | `feature/logviewer/LogViewerActivity.kt` + in-app page (AppPage.LogViewer) | — | `feature/logviewer/material/LogViewerScreen.kt` | `feature/logviewer/miuix/MiuixLogViewerScreen.kt` | Reads `core/logging/LogManager.kt` |
| Laboratory (实验室) | `feature/lab/LabActivity.kt` + in-app page (AppPage.Lab) | — | `feature/lab/material/LabScreen.kt` | `feature/lab/miuix/MiuixLabScreen.kt` | Flags: `core/settings/LabFeatureManager.kt` |
| Online Lyric Rematch (在线歌词重匹配) | `feature/onlinelyricdebug/OnlineLyricDebugActivity.kt` + in-app page | `feature/onlinelyricdebug/OnlineLyricDebugViewModel.kt` | `feature/onlinelyricdebug/material/OnlineLyricDebugScreen.kt` | `feature/onlinelyricdebug/miuix/MiuixOnlineLyricDebugScreen.kt` | Drives `lyrics/online/OnlineLyricFetcher.kt` |
| Last.fm | `feature/lastfm/LastFmSettingsActivity.kt` + in-app page (AppPage.LastFm) | — | `feature/lastfm/material/LastFmSettingsScreen.kt` | `feature/lastfm/miuix/MiuixLastFmSettingsScreen.kt` | Backend: `integration/lastfm/` (client, scrobble manager, secure store) |
| Lyrics Directories (歌词目录) | `feature/settings/LocalLyricDirectoriesSection.kt` (settings) + `feature/locallyrics/LocalLyricDirectoryActivity.kt` (per-directory) + in-app pages | — | `feature/settings/material/LocalLyricDirectoriesScreen.kt`, `feature/locallyrics/material/LocalLyricDirectoryScreen.kt` | `feature/settings/miuix/MiuixLocalLyricDirectoriesScreen.kt`, `feature/locallyrics/miuix/MiuixLocalLyricDirectoryScreen.kt` | Uses `lyrics/local/LocalLyricDirectoryManager.kt` |
| FAQ | `feature/faq/FAQActivity.kt` + in-app page (AppPage.Faq) | — | `feature/faq/material/FAQScreen.kt` | `feature/faq/miuix/MiuixFAQScreen.kt` | Static Q&A content |
| OOBE (首次引导) | `feature/oobe/OobeActivity.kt` | — | `feature/oobe/material/OobeScreen.kt` | `feature/oobe/miuix/MiuixOobeScreen.kt` | First-run welcome/permissions |
| Media Control (媒体控制弹窗) | `feature/mediacontrol/MediaControlActivity.kt` (transparent, singleInstance) | — | `feature/mediacontrol/material/MediaControlDialog.kt` | `feature/mediacontrol/miuix/MiuixMediaControlDialog.kt` | Commands via `runtime/media/LyricMediaCommandRouter.kt` |
| Update Dialog (更新弹窗) | shown from Home/About | — | `feature/update/material/UpdateDialog.kt` | `feature/update/miuix/MiuixUpdateDialog.kt` | Changelog parsing: `feature/update/UpdateParser.kt`, `feature/update/UpdateMarkdown.kt` |

Shared activity plumbing (theme wrappers, predictive back, page-stack host): `ui/navigation/BaseActivity.kt`, `ui/navigation/PredictiveBackActivity.kt`, `ui/navigation/PredictiveBackAnimation.kt`, `ui/navigation/PageStackHost.kt`, `ui/navigation/OverlaySheetHost.kt`, `ui/navigation/LayeredPagerTransition.kt`.

## Runtime Services & Background Components (运行时服务)

| Component | Files | Responsibility |
| :--- | :--- | :--- |
| LyricService (foreground) | `runtime/service/LyricService.kt` | Main orchestrator: owns foreground lifecycle, wires sources → repository → renderers, media commands, progress sync, scrobbling, playing-app actions. Backed by `runtime/foreground/`, `runtime/media/`, `runtime/metadata/`, `runtime/render/`. |
| MediaMonitorService | `runtime/service/MediaMonitorService.kt` | `NotificationListenerService`: observes media notifications/sessions, extracts title/artist/album/art via `rules/ParserRuleHelper.kt`, feeds `lyrics/state/LyricRepository.kt`. |
| Quick-Settings Tile | `runtime/service/CapsulyricTileService.kt` | Alternative entry when the launcher icon is hidden (see `core/settings/LauncherAliasManager.kt`). |
| Playing-app action | `runtime/playingapp/NewPlayingAppActionReceiver.kt`, `NewPlayingAppNotifier.kt` | Handles "open playing app" clicks from the overlay. |
| Fair memory | `runtime/memory/FairMemoryManager.kt` | OEM fair-memory (Android 16+) integration; complements `app/IslandLyricsApp.kt` `onTrimMemory`. |

## Lyric Pipeline (歌词流水线)

```text
media notification / media session / local .lrc / online APIs
        |
        v
lyrics.source.*  (SuperLyricSource, LyricGetterSource, LyriconSource,
                   OnlineLyricSource, LocalLyricSource)
        |
        v
lyrics.online.*  (OnlineLyricFetcher -> provider -> parser -> selector)
  online/provider/{Kugou,LrcApi,Lrclib,Netease,QqMusic,Soda}LyricProvider.kt
  online/crypto/  (NeteaseEapiCrypto, QqLyricPayloadDecoder, QqQrcDecrypter)
  online/network/OnlineLyricHttpClient.kt
  online/parser/  (OnlineLyricParser, OnlineLyricSidecarMerger)
  online/selection/OnlineLyricSelector.kt
lyrics.local.*   (LocalLrcParser, LocalLyricDirectoryManager)
lyrics.cache/OnlineLyricCacheStore.kt
lyrics.export/LyricExporter.kt
        |
        v
lyrics.state.*   (LyricRepository, LyricSidecarStore, LyricSourceArbiter,
                   TrackChangeDetector)  <-- shared state
        |
        +--> runtime notification updates (runtime/service, ui/overlay/*)
        +--> ui.overlay capsule / Super Island / floating renderers
        +--> feature screens and debug tools
```

Selection order and sidecar handling live in `lyrics/state/LyricSourceArbiter.kt`; translation/romanization sidecars are merged by `OnlineLyricSidecarMerger.kt` and stored in `LyricSidecarStore.kt`.

## UI Overlay Surfaces (悬浮层/系统表面)

| Surface | Root package | Key files |
| :--- | :--- | :--- |
| Live Update capsule (Android 16+) | `ui/overlay/capsule/` | `LyricCapsuleHandler.kt`, `config/LyricCapsulePreferencesCache.kt`, `config/LiveUpdateTextLimitConfig.kt`, `intent/LyricCapsuleIntentFactory.kt`, `render/LyricCapsuleNotificationBuilder.kt`, `render/LyricCapsuleDynamicIconCache.kt`, `render/AdvancedIconRenderer.kt` |
| Xiaomi Super Island | `ui/overlay/superisland/` | `SuperIslandHandler.kt`, `SuperIslandNotificationDispatcher.kt`, `cache/`, `config/` (prefs, color source, dual-line mode, layout, text limits, template pics, migration), `intent/SuperIslandIntentFactory.kt`, `render/` (notification builder, RemoteViews factory, focus builders, text resolvers), `state/SuperIslandRenderStateTracker.kt` |
| Floating lyrics (桌面歌词) | `ui/overlay/floating/` | `FloatingLyricsRenderer.kt`, `FloatingLyricsChrome.kt`, `FloatingLyricsContentView.kt`, `FloatingLyricsActionController.kt`, `FloatingLyricsDraggableFrameLayout.kt`, `FloatingLyricsStyleStore.kt`, `FloatingLyricsWindowPositionStore.kt`, `FloatingLyricsDisplayConfig.kt` |
| Shared overlay state/config | `ui/overlay/model/`, `ui/overlay/config/`, `ui/overlay/display/` | `LyricPresentation.kt`, `UIState.kt`, `WordProgressCalculator.kt`, `SecondaryTextMode.kt`, `OverlayRenderDefaults.kt`, `LyricDisplayManager.kt`, `LyricTextWindowCalculator.kt`, `ParsedLyricDisplayState.kt`, `TimingGapDisplayResolver.kt`, `AlbumArtColorExtractor.kt`, `OverlayDisplayConfig.kt` |

## Runtime Data Flow

```text
Media notifications / media sessions
        |
        v
runtime.service / runtime monitoring
        |
        v
rules + lyrics.source + lyrics.online/local/cache
        |
        v
lyrics.state repository
        |
        +--> runtime notification updates
        +--> ui.overlay capsule / Super Island renderers
        +--> feature screens and debug tools
```

Main flow:

1. Runtime services observe media sessions and media notifications.
2. Parser rules and lyric sources extract or fetch lyric candidates.
3. Online/local/cache providers normalize lyric data into repository state.
4. Runtime renderers update Live Update, capsule, and Super Island surfaces.
5. Feature screens observe shared state for settings, diagnostics, debug tools, and manual controls.

## External API Boundaries

Capsulyric integrates with several external or privileged APIs:

- SuperLyric API — `lyrics/source/SuperLyricSource.kt`
- Lyric Getter API — `lyrics/source/LyricGetterSource.kt`
- Lyricon subscriber API — `lyrics/source/LyriconSource.kt`
- Online lyric providers (NetEase, QQ Music, Kugou, Soda, LRCLIB, LrcApi) — `lyrics/online/provider/`
- Last.fm scrobbling — `integration/lastfm/` (`LastFmApiClient.kt`, `LastFmScrobbleManager.kt`, `LastFmSecureStore.kt`)
- HyperNotification / Focus API (Super Island) — `ui/overlay/superisland/`
- Shizuku and system-service bridges — `integration/shizuku/` (`ShizukuHook.kt`, `PrivilegedServiceImpl.kt`, `ShizukuUtil.kt`, `FirewallCompat.kt`, `XmsfNetworkHelper.kt`, `ShizukuUserServiceRecycler.kt`)
- Hidden API bypass — `app/IslandLyricsApp.kt` (via `HiddenApiBypass`), `hidden-api/` module (compileOnly AIDL stubs)

Keep API-specific keep rules and Parcelable compatibility requirements in build/R8 configuration. Do not remove API keep rules unless the upstream contract is verified.

## UI Architecture

The app currently supports both Material and Miuix UI surfaces. Prefer this shape:

```text
Feature contract / ViewModel / shared state
        |
        +--> material screen
        +--> miuix screen
```

Guidelines:

- Keep business decisions out of Material/Miuix component files when a shared ViewModel or contract can own them.
- Avoid changing visual behavior as a side effect of package cleanup or APK-size work.
- Treat animation smoothness, media-control responsiveness, and notification/surface fidelity as product requirements.
- When replacing UI dependencies, verify the resulting UI on device or with screenshots before accepting the size win.

## Refactoring Rules

- Prefer moving code toward existing ownership boundaries instead of creating broad utility packages.
- Keep provider/parser/scorer logic separated in `lyrics`.
- Keep notification control and service lifecycle logic in `runtime`.
- Keep settings/custom settings state in ViewModels/contracts rather than composables.
- Keep overlay rendering code split by target surface: capsule, Super Island, floating views, shared config/model.
- Add a page: create `feature/<name>/{Activity, Contract, ViewModel}` plus `material/` and `miuix/` screens; register the Activity in `app/src/main/AndroidManifest.xml` and (if in-app) add an `AppPage` in `feature/main/MainActivity.kt`.
- Do not introduce a new module split until package boundaries are stable enough to make module APIs meaningful.

## Build Layout

The app module build script delegates reusable concerns to Gradle scripts:

```text
app/build.gradle.kts            # Dependencies, plugin application
hidden-api/build.gradle.kts     # compileOnly hidden-API stubs module
gradle/scripts/versioning.gradle
gradle/scripts/signing.gradle
gradle/scripts/android-app.gradle
gradle/libs.versions.toml       # Version catalog
```

Keep versioning, signing, and Android app options separate. A future `build-logic` convention plugin can replace these scripts once the build shape stops changing frequently.

## Debug & Tooling

- `app/src/debug/` contains a debug-only center (`DebugCenterActivity`), a lyric rendering harness, and QQ romanization debug screens used for development; it is not part of release builds.
- `tools/` holds the autobuild scripts (`autobuild.bat`, `autobuild.sh`), a small web console (`tools/autobuild-ui/`), and tracing helpers (`tools/capture-notify-trace.ps1`).
- `perf-artifacts/` and `screenshots/` store performance captures and UI screenshots used for verification.
