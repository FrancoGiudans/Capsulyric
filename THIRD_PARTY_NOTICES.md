# Third-Party Notices (第三方声明)

> This file records third-party open-source components that have been **adapted, modified, or wrapped** into this repository's source tree (self-wrapped UI controls), as required by their licenses. It complements the in-app license page (`OpenSourceLicensesActivity`), which covers libraries imported as Gradle dependencies.

> 本文件记录被**改编、修改或封装**进本仓库源码的第三方开源组件（自封装 UI 控件），以满足相应开源协议的要求。应用内「开源许可」页面（`OpenSourceLicensesActivity`）覆盖的是以 Gradle 依赖引入的库，两者互为补充。

All file paths below are relative to `app/src/main/java/com/example/islandlyrics/` unless noted otherwise.

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

---

## Compliance notes (合规说明)

- This project is released under [GPL-3.0](LICENSE). Apache-2.0 is compatible with GPL-3.0, so the combined work may remain GPL-3.0; the Apache-2.0 attribution requirements below still apply to the files listed above.
- 本项目基于 [GPL-3.0](LICENSE) 发布。Apache-2.0 与 GPL-3.0 兼容，合并后的作品仍可按 GPL-3.0 发布；但上述文件仍需满足 Apache-2.0 的署名要求。
- The in-app license page (`OpenSourceLicensesActivity`, built with aboutlibraries) lists miuix and other Gradle dependencies together with their licenses.
- 应用内「开源许可」页面（`OpenSourceLicensesActivity`，基于 aboutlibraries）会列出 miuix 等 Gradle 依赖及其协议。

## Audit / 审计方法

- Files under `ui/miuix` and `ui/material` were audited by comparing their public API signatures and imports against the vendored miuix 0.9.3 source (`OverlayDialog`, `OverlayBottomSheet`, `OverlayDropdownPreference`, `TopAppBar`, `Scaffold`, `MiuixPopupUtils`, `miuix-blur`).
- Files outside these directories that import `top.yukonga.*` are feature screens that use the miuix library (normal dependency usage), not derived source, and are therefore not listed.
- 对 `ui/miuix` 与 `ui/material` 下的文件，通过与 miuix 0.9.3 源码（`OverlayDialog`、`OverlayBottomSheet`、`OverlayDropdownPreference`、`TopAppBar`、`Scaffold`、`MiuixPopupUtils`、`miuix-blur`）逐文件比对公开 API 签名与 import 完成审计；目录外引用 `top.yukonga.*` 的文件属于正常使用依赖的页面，不在此列。

---

*Maintained as part of Capsulyric (IslandLyrics). Please update this file whenever self-wrapped controls are added, removed, or re-based. / 随 Capsulyric (IslandLyrics) 维护；新增、移除或改版自封装控件时请同步更新本文件。*