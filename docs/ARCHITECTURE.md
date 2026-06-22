# Architecture

This document describes the current package boundaries and runtime data flow for Capsulyric. It is intentionally lightweight: use it as a map for maintenance and refactoring, not as a full design specification.

本文档说明 Capsulyric 当前的包边界与运行时数据流。它刻意保持轻量：用于维护和重构时快速定位职责，而不是完整设计说明书。

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
  core/         Shared infrastructure: preferences, logging, cache helpers, update helpers, platform/theme utilities.
  data/         Shared data models or legacy data helpers that have not moved into a narrower domain package.
  lyrics/       Lyric domain: sources, online fetching, parsing, scoring, local lyrics, cache, export, repository state.
  runtime/      Runtime services and playback integration: foreground services, media monitoring, notification control.
  service/      Legacy service-facing package. Prefer runtime/ for new service code.
  feature/      Screen-level business features and ViewModels.
  ui/           Reusable UI, themes, overlay renderers, capsule, Super Island, and view helpers.
  integration/  External or privileged API bridges such as Shizuku/system integrations.
  rules/        Parser-rule models, matching helpers, and rule management support.
```

## Package Boundaries

### app

`app` owns process-level startup only. It may initialize logging, repositories, lab flags, theme behavior, and platform exemptions.

Do not put feature workflows, lyric parsing, notification rendering, or screen-specific behavior here.

### core

`core` contains shared infrastructure that has no feature-screen ownership:

- settings and preferences
- logging and diagnostics support
- update and cache helpers
- theme/platform helpers
- common Android utilities

`core` may be used by most packages, but it should not depend on `feature`, `ui.overlay`, or concrete runtime services.

### lyrics

`lyrics` owns lyric-domain behavior:

- online providers, network calls, crypto helpers, parser, selection, and scoring
- local lyric discovery and matching
- lyric cache and export
- repository state shared by runtime and UI
- source-specific adapters

UI code should consume lyric state through stable models or repository APIs instead of calling provider/parser internals directly.

### runtime

`runtime` owns long-lived Android runtime behavior:

- foreground lyric service
- media monitor service
- media session observation
- notification updates and control intents
- playing-app actions

Runtime code may depend on `lyrics`, `core`, `rules`, and selected `integration` APIs. It should not depend on screen packages under `feature`.

### feature

`feature` owns screen-level workflows:

- main screen
- settings and custom settings
- parser-rule management
- diagnostics and log viewer
- cache management
- OOBE and FAQ

Feature packages may provide Material and Miuix presentation implementations, but business state should live in ViewModels/contracts where possible.

### ui

`ui` owns reusable presentation code:

- Material and Miuix theme wrappers
- common UI helpers
- overlay renderer models and views
- capsule rendering
- Super Island rendering
- floating display helpers

`ui.overlay` can depend on runtime models needed for rendering, but it should avoid owning service orchestration.

### integration

`integration` isolates privileged or external APIs. Examples include Shizuku and system-level bridges.

Keep these adapters narrow. Callers should not spread privileged API details across feature or runtime packages.

### rules

`rules` owns parser-rule data and matching helpers. Lyric extraction and notification parsing can depend on it; UI should use rule-management screens and ViewModels rather than duplicating matching logic.

### data and service

`data` and `service` are compatibility/legacy areas. New domain-specific work should prefer `lyrics`, `runtime`, `core`, or `feature` unless the existing ownership clearly belongs here.

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

- SuperLyric API
- Lyric Getter API
- Lyricon subscriber API
- HyperNotification / Focus API
- Shizuku and system-service bridges
- Hidden API bypass

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
- Do not introduce a new module split until package boundaries are stable enough to make module APIs meaningful.

## Build Layout

The app module build script delegates reusable concerns to Gradle scripts:

```text
app/build.gradle
gradle/scripts/versioning.gradle
gradle/scripts/signing.gradle
gradle/scripts/android-app.gradle
```

Keep versioning, signing, and Android app options separate. A future `build-logic` convention plugin can replace these scripts once the build shape stops changing frequently.
