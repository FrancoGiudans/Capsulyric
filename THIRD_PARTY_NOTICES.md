# Third-Party Notices (第三方声明)

> This file records third-party open-source components used by this repository in **three complementary layers**:
>
> 1. **§1–§5 — Source-tree embedding (源码嵌入)** — third-party code that has been **adapted, modified, ported, or wrapped** into this repository's source tree (self-wrapped UI controls, ported lyric providers, adapted Shizuku helpers, AOSP hidden API stubs), as required by their licenses.
> 2. **§6 — Direct Gradle dependencies (直接依赖总览)** — libraries imported as direct Gradle dependencies in `app/build.gradle.kts`.
> 3. **In-app license page (应用内许可页面)** — `OpenSourceLicensesActivity` (built with aboutlibraries) lists the **full transitive closure** (174 libraries) generated at build time.

> 本文件以**三层互补**方式记录本仓库使用的第三方开源组件：
>
> 1. **§1–§5 源码嵌入** —— 被**改编、修改、移植或封装**进本仓库源码树的第三方代码（自封装 UI 控件、移植的歌词提供方、改编的 Shizuku 辅助类、AOSP 隐藏 API stub），以满足相应开源协议的要求。
> 2. **§6 直接依赖总览** —— 在 `app/build.gradle.kts` 中直接声明的 Gradle 依赖。
> 3. **应用内许可页面** —— `OpenSourceLicensesActivity`（基于 aboutlibraries）列出构建期生成的**完整传递依赖**（174 个库）。

All file paths below are relative to `app/src/main/java/com/example/islandlyrics/` unless noted otherwise. (§5 paths are relative to the repository root.)

除非另有说明，下文所有路径均相对于 `app/src/main/java/com/example/islandlyrics/`（§5 的路径相对于仓库根目录）。

---

## 1. compose-miuix-ui/miuix — Apache-2.0

| Item / 项目 | Value / 内容 |
| --- | --- |
| Project / 项目 | [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) |
| License / 协议 | Apache License 2.0 — full text / 全文见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt) |
| Version / 版本 | 0.9.3 (`miuix-ui`, `miuix-icons`, `miuix-preference`, `miuix-blur`) |
| Copyright / 版权 | Copyright 2025, compose-miuix-ui contributors |

The self-wrapped controls below are **modified from or built on** the corresponding miuix components, with blur-backdrop support added on top of `miuix-blur`.

以下自封装控件**修改自或基于**对应的 miuix 组件，并在其基础上叠加 `miuix-blur` 模糊背景支持。

| File / 文件 | Based on (miuix source) / 上游来源 | Type / 类型 |
| --- | --- | --- |
| `ui/miuix/blur/BlurBackdrop.kt` | `miuix-blur`: `Backdrop`, `layerBackdrop`, `rememberLayerBackdrop` | 封装 (wrapper) |
| `ui/miuix/blur/MiuixBlurDialog.kt` | `OverlayDialog` (`overlay/OverlayDialog.kt`) + `miuix-blur` | 修改 (modified) |
| `ui/miuix/blur/MiuixBlurBottomSheet.kt` | `OverlayBottomSheet` (`overlay/OverlayBottomSheet.kt`) + `miuix-blur` | 修改 (modified) |
| `ui/miuix/blur/MiuixBlurPopupHost.kt` | `MiuixPopupUtils.MiuixPopupHost` (`utils/MiuixPopupUtils.kt`) | 封装 (wrapper) |
| `ui/miuix/blur/MiuixBlurScaffold.kt` | `Scaffold` (`basic/Scaffold.kt`) + `miuix-blur` `layerBackdrop` | 封装 (wrapper) |
| `ui/miuix/blur/MiuixBlurSnackbar.kt` | `Snackbar` (`basic/Snackbar.kt`) + `miuix-blur` | 修改 (modified) |
| `ui/miuix/blur/MiuixBlurStyle.kt` | `miuix-blur`: `BlurDefaults`, `BlurColors`, `BlendColorEntry`, `textureBlur`, `Highlight`, `BloomStroke`, `LightSource` | 基于上游公开 API 实现 (implemented using upstream public APIs) |
| `ui/miuix/blur/MiuixBlurTopAppBar.kt` | `TopAppBar` / `SmallTopAppBar` (`basic/TopAppBar.kt`) + `miuix-blur` | 修改 (modified) |
| `ui/miuix/effects/MiuixAuroraBackground.kt` | `miuix-blur`: `RuntimeShader`, `asBrush` | 基于上游公开 API 实现 (implemented using upstream public APIs) |
| `ui/miuix/effects/MiuixScrollEffects.kt` | `ScrollBehavior` (`basic`), `overScrollVertical` (`utils`) | 基于上游公开 API 实现 (implemented using upstream public APIs) |
| `ui/miuix/navigation/MiuixBackIcon.kt` | `Icon`, `MiuixIcons`, `MiuixTheme` | 封装 (wrapper) |
| `ui/miuix/navigation/MiuixBackHandler.kt` | — (based on `androidx.navigationevent`) | 未使用第三方源码 (no third-party source) |
| `ui/miuix/preference/BlurOverlayDropdownPreference.kt` | `miuix-preference`: `OverlayDropdownPreference` (`preference/OverlayDropdownPreference.kt`), `OverlayDropdownPopup` (`popup/OverlayDropdownPopup.kt`) + `miuix-blur` | 修改 (modified) |
| `ui/miuix/reorderable/MiuixBlurReorderablePanel.kt` | miuix basic components: `Card`, `CardDefaults`, `Icon`, `Text` + `miuix-blur` styles | 基于上游公开 API 实现 (implemented using upstream public APIs) |
| `ui/miuix/theme/MiuixAppTheme.kt` | miuix theme: `MiuixTheme`, `ThemeController`, `ColorSchemeMode`, `ThemePaletteStyle`, `darkColorScheme`, `lightColorScheme` | 基于上游公开 API 实现 (implemented using upstream public APIs) |
| `ui/miuix/theme/MiuixTopBarStyle.kt` | `MiuixTheme` | 基于上游公开 API 实现 (implemented using upstream public APIs) |

类型说明 / Type legend:

| Type / 类型 | Meaning / 含义 |
| --- | --- |
| 修改 (modified) | 拷贝并修改了上游源码，按 Apache-2.0 §4 需保留上游版权并注明修改 / copied and modified upstream source code |
| 封装 (wrapper) | 未拷贝源码，仅对上游组件做薄包装 / thin wrapper delegating to upstream components, no source copied |
| 基于上游公开 API 实现 (implemented using upstream public APIs) | 未拷贝源码，仅调用上游公开 API/组件自行实现 / original implementation using upstream public APIs, no source copied |
| 未使用第三方源码 (no third-party source) | 未拷贝任何第三方项目源码，仅基于系统/androidx API（列入仅为说明审计覆盖范围）/ no third-party source copied; uses system/androidx APIs only (listed to document audit coverage) |

## 2. Material 3 (AndroidX Compose) — Apache-2.0

| Item / 项目 | Value / 内容 |
| --- | --- |
| Project / 项目 | [androidx.compose.material3](https://developer.android.com/jetpack/androidx/releases/compose-material3) |
| License / 协议 | Apache License 2.0 |
| Copyright / 版权 | The Android Open Source Project |

| File / 文件 | Based on / 上游来源 | Type / 类型 |
| --- | --- | --- |
| `ui/material/blur/MaterialBlur.kt` | material3: `Scaffold`, `AlertDialog`, `DropdownMenu` + `miuix-blur`: `layerBackdrop`, `textureBlur`, `rememberLayerBackdrop` | 基于上游公开 API 组合实现 (combined implementation using upstream public APIs) |

## 3. Lyricify-Lyrics-Helper — Apache-2.0

| Item / 项目 | Value / 内容 |
| --- | --- |
| Project / 项目 | [WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) |
| License / 协议 | Apache License 2.0 — full text / 全文见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt) |
| Copyright / 版权 | Copyright (C) WXRIW/Lyricify-Lyrics-Helper contributors |

The files below are **ported from** Lyricify-Lyrics-Helper (C# → Kotlin) as part of the online lyric retrieval pipeline. Each file carries the header `// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)` plus this project's GPL-3.0 file header.

以下文件是**从** Lyricify-Lyrics-Helper **移植**（C# → Kotlin）而来，构成在线歌词获取管线。每个文件均带有 `// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)` 移植声明与本项目 GPL-3.0 文件头。

| File / 文件 | Purpose / 用途 | Type / 类型 |
| --- | --- | --- |
| `lyrics/online/OnlineLyricFetcher.kt` | 在线歌词抓取器入口 / online lyric fetcher entry | 移植 (ported, C#→Kotlin) |
| `lyrics/online/crypto/NeteaseEapiCrypto.kt` | 网易云 EAPI 加密 / NetEase EAPI encryption | 移植 (ported, C#→Kotlin) |
| `lyrics/online/crypto/QqQrcDecrypter.kt` | QQ 音乐 QRC 解密 / QQ Music QRC decryption | 移植 (ported, C#→Kotlin) |
| `lyrics/online/crypto/QqLyricPayloadDecoder.kt` | QQ 音乐歌词负载解码 / QQ Music lyric payload decoding | 移植 (ported, C#→Kotlin) |
| `lyrics/online/parser/OnlineLyricParser.kt` | 在线歌词解析入口 / online lyric parser entry | 移植 (ported, C#→Kotlin) |
| `lyrics/online/parser/OnlineLyricSidecarMerger.kt` | sidecar 歌词合并 / sidecar lyric merging | 移植 (ported, C#→Kotlin) |
| `lyrics/online/parser/QrcParser.kt` | QRC 歌词格式解析 / QRC lyric format parsing | 移植 (ported, C#→Kotlin) |
| `lyrics/online/parser/YrcParser.kt` | YRC 歌词格式解析 / YRC lyric format parsing | 移植 (ported, C#→Kotlin) |
| `lyrics/online/parser/MusixmatchRichsyncParser.kt` | Musixmatch Richsync 解析 / Richsync parsing | 移植 (ported, C#→Kotlin) |
| `lyrics/online/provider/AppleMusicLyricProvider.kt` | Apple Music 歌词提供方 / Apple Music provider | 移植 (ported, C#→Kotlin) |
| `lyrics/online/provider/AppleMusicStateCache.kt` | Apple Music 状态缓存 / Apple Music state cache | 移植 (ported, C#→Kotlin) |
| `lyrics/online/provider/KugouLyricProvider.kt` | 酷狗歌词提供方 / Kugou provider | 移植 (ported, C#→Kotlin) |
| `lyrics/online/provider/MusixmatchLyricProvider.kt` | Musixmatch 歌词提供方 / Musixmatch provider | 移植 (ported, C#→Kotlin) |
| `lyrics/online/provider/NeteaseLyricProvider.kt` | 网易云歌词提供方 / NetEase provider | 移植 (ported, C#→Kotlin) |
| `lyrics/online/provider/QqMusicLyricProvider.kt` | QQ 音乐歌词提供方 / QQ Music provider | 移植 (ported, C#→Kotlin) |

## 4. InstallerX Revived — GPL-3.0

| Item / 项目 | Value / 内容 |
| --- | --- |
| Project / 项目 | [wxxsfxyzm/InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) |
| License / 协议 | GNU General Public License v3.0 — identical to this project's [LICENSE](LICENSE) / 与项目 [LICENSE](LICENSE) 相同 |
| Copyright / 版权 | Copyright (C) 2023–2026 iamr0s, InstallerX Revived contributors |

The files below contain portions **adapted from** InstallerX Revived (Shizuku hook-mode networking helpers). Each file carries the header `// Portions of this file are adapted from InstallerX Revived`.

以下文件包含**改编自** InstallerX Revived 的内容（Shizuku hook 模式联网辅助）。每个文件均带有 `// Portions of this file are adapted from InstallerX Revived` 声明。

| File / 文件 | Purpose / 用途 | Type / 类型 |
| --- | --- | --- |
| `integration/shizuku/ShizukuHook.kt` | Shizuku hook 模式最小辅助 / minimal hook-mode helper | 改编 (adapted) |
| `integration/shizuku/XmsfNetworkHelper.kt` | XMSF 网络控制辅助 / XMSF networking helper | 改编 (adapted) |

> GPL-3.0 compatibility: this project is itself GPL-3.0, so the adapted portions remain GPL-3.0 as part of the combined work; no additional license obligations beyond retaining the notices above.
> GPL-3.0 兼容性说明：本项目本身即 GPL-3.0，改编部分随合并作品保持 GPL-3.0；除保留上述声明外无额外义务。

## 5. AOSP hidden API stubs (hidden-api module) — AOSP / GPL-3.0

Paths in this section are relative to the repository root. / 本节路径相对于仓库根目录。

| File / 文件 | Origin / 来源 | Type / 类型 |
| --- | --- | --- |
| `hidden-api/src/main/java/android/net/IConnectivityManager.java` | 接口镜像自 AOSP（Apache-2.0）；具体拷贝派生自 InstallerX Revived（GPL-3.0），仅保留防火墙相关方法并删除原注释 / interface mirrors AOSP (Apache-2.0); concrete copy derived from InstallerX Revived (GPL-3.0), firewall methods only, original comments removed | 派生 (derived) |
| `hidden-api/src/main/java/android/os/INetworkManagementService.java` | 项目自写 stub，镜像 AOSP 接口签名，未拷贝 AOSP 源码 / project-written stub mirroring AOSP interface signatures, no AOSP source copied | 自写 (original) |

The `hidden-api` module is `compileOnly`-linked into `:app` and exposes the AOSP firewall control interfaces (`setFirewallChainEnabled`, `setUidFirewallRule`, …) for reflective access under Shizuku. AOSP is distributed under Apache-2.0 (see <https://source.android.com/docs/setup/reference/licenses>); the stub files themselves carry this project's GPL-3.0 header, and the derived portions retain the InstallerX Revived attribution in §4.

`hidden-api` 模块以 `compileOnly` 方式链接进 `:app`，暴露 AOSP 防火墙控制接口（`setFirewallChainEnabled`、`setUidFirewallRule` 等）供 Shizuku 下反射调用。AOSP 以 Apache-2.0 发布（见 <https://source.android.com/docs/setup/reference/licenses>）；stub 文件本身带本项目 GPL-3.0 文件头，派生部分保留 §4 的 InstallerX Revived 署名。

## 6. Direct Gradle dependencies (直接依赖清单)

The following libraries are **directly declared** in `app/build.gradle.kts` (via `gradle/libs.versions.toml`). Versions marked *BOM* are managed by their platform/BOM entry. The full transitive closure (174 libraries) — including `commonmark` (BSD-2-Clause), `material-color-utilities` (MIT), `okio`, and the KMP Compose chain — is listed by the in-app license page generated by aboutlibraries at build time.

以下为 `app/build.gradle.kts` **直接声明**的依赖（通过 `gradle/libs.versions.toml` 管理）。标 *BOM* 的版本由对应 BOM 管理。完整传递依赖（174 个库，含 `commonmark`(BSD-2-Clause)、`material-color-utilities`(MIT)、`okio` 及 KMP Compose 传递链）由 aboutlibraries 在构建期生成，应用内许可页面统一列出。

### 6.1 Lyrics (歌词检索)

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| SuperLyricApi | `com.github.HChenX:SuperLyricApi` | 3.4 | **LGPL-2.1** |
| Lyric-Getter-Api | `com.github.xiaowine:Lyric-Getter-Api` | 6.0.0 | **LGPL-2.1** |
| lyricon subscriber | `io.github.proify.lyricon:subscriber` | 0.1.70 | Apache-2.0 |
| focus-api (HyperNotification) | `com.xzakota.hyper.notification:focus-api` | 1.4 | Apache-2.0 |

### 6.2 Kotlin foundation (Kotlin 基础)

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| kotlin-bom | `org.jetbrains.kotlin:kotlin-bom` | 2.4.10 | Apache-2.0 |
| kotlin-stdlib | `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.10 | Apache-2.0 |
| kotlinx-coroutines-android | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 |

### 6.3 Compose UI

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| compose-bom | `androidx.compose:compose-bom` | 2026.06.01 | Apache-2.0 |
| ui | `androidx.compose.ui:ui` | BOM | Apache-2.0 |
| ui-graphics | `androidx.compose.ui:ui-graphics` | BOM | Apache-2.0 |
| ui-tooling-preview | `androidx.compose.ui:ui-tooling-preview` | BOM | Apache-2.0 |
| ui-tooling (debug only) | `androidx.compose.ui:ui-tooling` | BOM | Apache-2.0 |
| material3 | `androidx.compose.material3:material3` | BOM | Apache-2.0 |
| material-icons-extended | `androidx.compose.material:material-icons-extended` | BOM | Apache-2.0 |
| runtime-livedata | `androidx.compose.runtime:runtime-livedata` | BOM | Apache-2.0 |
| activity-compose | `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 |
| lifecycle-viewmodel-compose | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 |
| navigationevent-compose | `org.jetbrains.androidx.navigationevent:navigationevent-compose` | 1.1.0 | Apache-2.0 |

### 6.4 Miuix (MIUI-style components)

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| miuix-ui | `top.yukonga.miuix.kmp:miuix-ui-android` | 0.9.3 | Apache-2.0 |
| miuix-icons | `top.yukonga.miuix.kmp:miuix-icons-android` | 0.9.3 | Apache-2.0 |
| miuix-preference | `top.yukonga.miuix.kmp:miuix-preference-android` | 0.9.3 | Apache-2.0 |
| miuix-blur | `top.yukonga.miuix.kmp:miuix-blur-android` | 0.9.3 | Apache-2.0 |

### 6.5 Markdown rendering

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| markwon-core | `io.noties.markwon:core` | 4.6.2 | Apache-2.0 |
| markwon-ext-tables | `io.noties.markwon:ext-tables` | 4.6.2 | Apache-2.0 |
| markwon-ext-strikethrough | `io.noties.markwon:ext-strikethrough` | 4.6.2 | Apache-2.0 |
| markwon-ext-tasklist | `io.noties.markwon:ext-tasklist` | 4.6.2 | Apache-2.0 |
| markwon-html | `io.noties.markwon:html` | 4.6.2 | Apache-2.0 |
| markwon-image-coil | `io.noties.markwon:image-coil` | 4.6.2 | Apache-2.0 |
| coil | `io.coil-kt:coil` | 2.7.0 | Apache-2.0 |

### 6.6 Shizuku privileged API

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| shizuku-api | `dev.rikka.shizuku:api` | 13.1.5 | **MIT** |
| shizuku-provider | `dev.rikka.shizuku:provider` | 13.1.5 | **MIT** |
| hiddenapibypass | `org.lsposed.hiddenapibypass:hiddenapibypass` | 6.1 | Apache-2.0 |

### 6.7 AndroidX foundation

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| core-ktx | `androidx.core:core-ktx` | 1.19.0 | Apache-2.0 |
| media | `androidx.media:media` | 1.8.0 | Apache-2.0 |
| material | `com.google.android.material:material` | 1.14.0 | Apache-2.0 |
| palette-ktx | `androidx.palette:palette-ktx` | 1.0.0 | Apache-2.0 |

### 6.8 Networking & tooling

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| okhttp | `com.squareup.okhttp3:okhttp` | 5.4.0 | Apache-2.0 |
| aboutlibraries-compose-m3 | `com.mikepenz:aboutlibraries-compose-m3` | 15.0.4 | Apache-2.0 |

### 6.9 Local module

| Dependency / 依赖 | Coordinates / 坐标 | Version / 版本 | License / 协议 |
| --- | --- | --- | --- |
| hidden-api | `compileOnly project(":hidden-api")` | — | GPL-3.0（stub 来源见 [§5](#5-aosp-hidden-api-stubs-hidden-api-module--aosp--gpl-30)） |

Build toolchain (build-time only, not distributed): Android Gradle Plugin 9.3.1, Kotlin compose plugin 2.4.10, aboutlibraries plugin 15.0.4, ben-manes versions plugin 0.59.0, foojay-resolver-convention 1.0.0.

构建工具链（仅构建期使用，不随应用分发）：Android Gradle Plugin 9.3.1、Kotlin compose 插件 2.4.10、aboutlibraries 插件 15.0.4、ben-manes versions 插件 0.59.0、foojay-resolver-convention 1.0.0。

---

## Compliance notes (合规说明)

- This project is released under [GPL-3.0](LICENSE). Apache-2.0 is compatible with GPL-3.0, so the combined work may remain GPL-3.0; the Apache-2.0 attribution requirements below still apply to the files listed above.
- 本项目基于 [GPL-3.0](LICENSE) 发布。Apache-2.0 与 GPL-3.0 兼容，合并后的作品仍可按 GPL-3.0 发布；但上述文件仍需满足 Apache-2.0 的署名要求。
- **LGPL-2.1** (`SuperLyricApi`, `Lyric-Getter-Api`): used as separate libraries linked against the app, which is permitted under LGPL-2.1; full text in [LICENSES/LGPL-2.1.txt](LICENSES/LGPL-2.1.txt). The aboutlibraries overrides in `app/config/` (`libraries/*.json` + `licenses/*.json`) resolve both JitPack artifacts to LGPL-2.1, so the in-app license page shows the correct license name and full text.
- **LGPL-2.1**（`SuperLyricApi`、`Lyric-Getter-Api`）：以独立库方式链接进应用，符合 LGPL-2.1 使用条件；全文见 [LICENSES/LGPL-2.1.txt](LICENSES/LGPL-2.1.txt)。aboutlibraries 通过 `app/config/` 覆盖配置（`libraries/*.json` + `licenses/*.json`）将这两个 JitPack 构件解析为 LGPL-2.1，应用内许可页面可正确显示协议名称与全文。
- **MIT** (`dev.rikka.shizuku:*`): full text in [LICENSES/MIT.txt](LICENSES/MIT.txt). / 全文见 [LICENSES/MIT.txt](LICENSES/MIT.txt)。
- **BSD-2-Clause** (`commonmark`, transitive via markwon): full text in [LICENSES/BSD-2-Clause.txt](LICENSES/BSD-2-Clause.txt). / 全文见 [LICENSES/BSD-2-Clause.txt](LICENSES/BSD-2-Clause.txt)。
- **GPL compatibility matrix / GPL 兼容矩阵**: project is GPL-3.0 — compatible with Apache-2.0 (miuix, Lyricify, markwon, okhttp, …), MIT (Shizuku), BSD-2-Clause (commonmark), LGPL-2.1 (SuperLyricApi, Lyric-Getter-Api, used as separate libraries), and GPL-3.0 itself (InstallerX Revived adapted code stays GPL-3.0 as part of the combined work).
- The in-app license page (`OpenSourceLicensesActivity`, built with aboutlibraries) lists all Gradle dependencies (direct + transitive) together with their licenses.
- 应用内「开源许可」页面（`OpenSourceLicensesActivity`，基于 aboutlibraries）列出全部 Gradle 依赖（直接 + 传递）及其协议。

## Audit / 审计方法

- Files under `ui/miuix` and `ui/material` were audited by comparing their public API signatures and imports against the vendored miuix 0.9.3 source (`OverlayDialog`, `OverlayBottomSheet`, `OverlayDropdownPreference`, `TopAppBar`, `Scaffold`, `MiuixPopupUtils`, `miuix-blur`).
- Files outside these directories that import `top.yukonga.*` are feature screens that use the miuix library (normal dependency usage), not derived source, and are therefore not listed.
- 对 `ui/miuix` 与 `ui/material` 下的文件，通过与 miuix 0.9.3 源码（`OverlayDialog`、`OverlayBottomSheet`、`OverlayDropdownPreference`、`TopAppBar`、`Scaffold`、`MiuixPopupUtils`、`miuix-blur`）逐文件比对公开 API 签名与 import 完成审计；目录外引用 `top.yukonga.*` 的文件属于正常使用依赖的页面，不在此列。
- **§3 Lyricify**: the 15 ported files were identified by the `// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)` header and cross-checked against the vendored upstream copy in `.vscode/Lyricify-Lyrics-Helper-master` (local audit reference, not in git).
- **§4 InstallerX**: the 2 adapted files carry the `// Portions of this file are adapted from InstallerX Revived` header; cross-checked against `.vscode/InstallerX-Revived-main` (local audit reference, not in git).
- **§5 hidden-api**: `IConnectivityManager.java` was diffed against the same-named file in `.vscode/InstallerX-Revived-main/hidden-api` (only comments/blank lines removed); `INetworkManagementService.java` is project-written (no matching upstream file).
- **§3 Lyricify**：15 个移植文件通过 `// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)` 文件头识别，并与 `.vscode/Lyricify-Lyrics-Helper-master` 上游源码副本（本地审计参考，不入 git）交叉核对。
- **§4 InstallerX**：2 个改编文件带 `// Portions of this file are adapted from InstallerX Revived` 文件头，与 `.vscode/InstallerX-Revived-main`（本地审计参考，不入 git）交叉核对。
- **§5 hidden-api**：`IConnectivityManager.java` 与 `.vscode/InstallerX-Revived-main/hidden-api` 同名文件 diff 比对（仅删除注释/空行）；`INetworkManagementService.java` 为项目自写（上游无对应文件）。

---

*Maintained as part of Capsulyric (IslandLyrics). Please update this file whenever self-wrapped controls are added, removed, or re-based. / 随 Capsulyric (IslandLyrics) 维护；新增、移除或改版自封装控件时请同步更新本文件。*
