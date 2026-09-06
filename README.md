# <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" width="60" height="60" valign="middle"> Capsulyric

[![Latest Release](https://img.shields.io/github/v/release/FrancoGiudans/Capsulyric?include_prereleases&style=flat-square&label=Latest&color=orange)](https://github.com/FrancoGiudans/Capsulyric/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/FrancoGiudans/Capsulyric/total?style=flat-square&color=green)](https://github.com/FrancoGiudans/Capsulyric/releases)
[![License](https://img.shields.io/github/license/FrancoGiudans/Capsulyric?style=flat-square&color=blue)](LICENSE)

> **Provides status bar lyrics based on Live Update and Xiaomi Super Island.**

## About (项目介绍)

Capsulyric is an Android app that displays now-playing lyrics on the status bar, notification, and lock-screen area through **Live Update (Android 16+)** and/or **Xiaomi Super Island (HyperOS 3.0+)**. It gathers lyrics from media notifications, online services, and local `.lrc` files, with support for translations and romanization.

Capsulyric 是一款 Android 歌词应用，通过**实况通知（Android 16+）** 和/或 **小米超级岛（HyperOS 3.0+）** 在状态栏、通知栏与锁屏区域显示正在播放的歌词。支持从媒体通知、在线服务与本地 `.lrc` 文件获取歌词，并支持翻译与拼音歌词。

> [!NOTE]
> This project will enter the Long Term Slacking (LTS) phase after the next stable version is released. The core experience is now relatively stable. Future update frequency will be significantly reduced, and new feature development will proceed at a slower pace.\
> 本项目将在下一个Stable版本开始进入长周期阶段（LTS，Long Term Slacking）。基础体验已趋于稳定，更新频率将降低，功能开发也将相应放缓。

<details>
<summary><b>Table of Contents (目录)</b></summary>

- [About (项目介绍)](#about-项目介绍)
- [Features (功能特性)](#features-功能特性)
- [Screenshots (效果展示)](#screenshots-效果展示)
- [Modes & Requirements (模式与要求)](#modes--requirements-模式与要求)
- [Lyric Acquisition (歌词获取方式)](#lyric-acquisition-歌词获取方式)
- [FAQ (常见问题)](#faq-常见问题)
- [Privacy (隐私说明)](#privacy-隐私说明)
- [Project Structure (项目结构)](#project-structure-项目结构)
- [Build (构建)](#build-构建)
- [License (开源协议)](#license-开源协议)
- [Credits (致谢)](#credits-致谢)

</details>

## Features (功能特性)

- **Live Update (实况通知)** — System-level dynamic lyrics in the notification and lock-screen area. / 系统级实况通知，在通知栏与锁屏区域显示动态歌词。
- **Xiaomi Super Island (小米超级岛)** — Native island display on HyperOS 3.0+. / HyperOS 3.0+ 原生超级岛展示。
- **Multiple lyric sources (多歌词源)** — Media notifications, online lyrics, Superlyric, Lyric Getter, Lyricon, and local `.lrc`. / 通知栏、在线歌词、Superlyric、Lyric Getter、Lyricon 与本地 `.lrc` 多来源获取。
- **Translations & romanization (翻译与拼音歌词)** — Available with Online Lyrics enabled. / 开启在线歌词后支持翻译与拼音歌词。

---

## Screenshots (效果展示)

*(展示机型：Xiaomi 15 | 系统版本：HyperOS 3.0.300.7 Beta | 展示应用版本：Version.26.6.2.Stable_C488)*

### App UI (界面风格)
<p align="center">
  <b>Material Design</b> &nbsp;&nbsp;vs&nbsp;&nbsp; <b>MIUIX</b><br><br>
  <img src="screenshots/1.png" width="45%" />
  <img src="screenshots/miuix-1.png" width="45%" />
</p>

### Media Control (媒体控制弹窗)
<p align="center">
  <img src="screenshots/4.png" width="45%" />
  <img src="screenshots/miuix-4.png" width="45%" />
</p>

### Notification (通知形态)
<p align="center">
  <b>Live Update (实况通知)</b> &nbsp;&nbsp;vs&nbsp;&nbsp; <b>Xiaomi Super Island (小米超级岛)</b><br><br>
  <img src="screenshots/2.jpg" width="45%" />
  <img src="screenshots/miuix-2.jpg" width="45%" />
</p>

### Capsule (胶囊形态)
<p align="center">
  <img src="screenshots/3.png" width="45%" />
  <img src="screenshots/miuix-3.png" width="45%" />
</p>

---

## Modes & Requirements (模式与要求)

| Mode / 模式 | Requirements / 要求 | Supported Devices / 支持机型 |
| :--- | :--- | :--- |
| **Live Update (实况通知)** | Android 16+ <br> HyperOS 3.0.300+ | Xiaomi HyperOS (Verified) <br> ColorOS, OneUI, AOSP (Community) |
| **Xiaomi Super Island (小米超级岛)** | HyperOS 3.0 <br> & Android 15+ | HyperOS devices with <br> Root or Shizuku |

### 1. Live Update (实况通知)
- **How it works / 工作方式:** System-driven dynamic lyrics in the notification and lock-screen area. / 由系统实况通知机制驱动，在通知栏与锁屏区域显示动态歌词。
- **EN:** Generally supports **Android 16+**. For **HyperOS**, version **3.0.300+** is required.
- **CN:** 一般要求 **Android 16+**。针对小米设备，需要 **HyperOS 3.0.300+** 版本。

### 2. Xiaomi Super Island (小米超级岛)
- **How it works / 工作方式:** Lyrics rendered inside Xiaomi's island capsule. / 在小米超级岛胶囊内渲染歌词。
- **EN:** Requires **HyperOS 3.0** and **Android 15+**. System requires **Root access** or **Shizuku**.
- **CN:** 要求 **HyperOS 3.0** 与 **Android 15+**。系统需要 **Root 权限** 或 **Shizuku** 环境。

> [!NOTE]
> Systems below Android 16 or HyperOS 3.0 do not support native dynamic lyrics.
> 低于 Android 16 或 HyperOS 3.0 的系统不支持原生动态歌词。

---

## Lyric Acquisition (歌词获取方式)

| Method / 方式 | Description / 说明 |
| :--- | :--- |
| **Media Notification** | Detects lyrics from standard notifications. / 从标准通知栏提取。 |
| **Online Lyrics** | Fetches from online servers. Supports translations & romanization. / 从互联网服务器获取，支持翻译与拼音歌词。 |
| **Superlyric API** | High accuracy (Root/LSPosed required). / 准确度高（需 Root/LSPosed）。 |
| **Lyric Getter** | Supports Meizu & LSPatch (non-root). / 支持魅族状态栏歌词及免 Root 注入。 |
| **Lyricon API** | Root/LSPosed required. / 需 Root/LSPosed。 |
| **Local Lyric** | Based on local .lrc files with auto-matching. / 基于本地 .lrc 歌词文件，支持自动匹配。 |

---

## FAQ (常见问题)

<details>
<summary><b>Q1: How to add parser rules? (如何添加解析规则？)</b></summary>

> **EN:**
> 1. **Enable App Settings**: Ensure "Notification/Car Lyrics" is enabled in your music app.
> 2. **Add Rule**: Manually add or use "Recommend" in the Parser Rules page.
> 3. **Configure**: Select the correct "Separator" and "Order", then restart the music app.
>
> **CN:**
> 1. **开启应用设置**：确认音乐应用内已开启“通知栏歌词”或“车载蓝牙歌词”。
> 2. **添加解析规则**：在“解析规则”页面手动添加或使用“推荐”。
> 3. **配置逻辑**：选择对应的“分隔符”和“顺序”并重启音乐应用。
</details>

<details>
<summary><b>Q2: How to use Xiaomi Super Island? (如何使用小米超级岛？)</b></summary>

> **EN:**
> - **Rooted**: Recommended to use HyperCeiler to bypass the whitelist.
> - **Non-rooted**: Authorize Shizuku and enable "Bypass Xiaomi Super Island Whitelist". Note potential battery impact or message delay.
>
> **CN:**
> - **已 Root**：推荐使用 HyperCeiler 插件解除白名单限制。
> - **未 Root**：授权 Shizuku 并开启“绕过小米超级岛白名单”。注意可能导致耗电增加或消息延迟。
</details>

<details>
<summary><b>Q3: Why can't I see lyrics? (为什么看不到歌词？)</b></summary>

> **EN:**
> 1. **Permissions**: Check "Notification Access".
> 2. **System**: Requires **Android 16+** or **HyperOS 3.0.300+**. HyperOS below 3.0.300 cannot show native live notifications.
> 3. **App Settings**: Ensure lyrics settings are enabled in your music player.
>
> **CN:**
> 1. **检查权限**: 确保“通知使用权”已开启。
> 2. **版本要求**: 需 **Android 16+** 或 **HyperOS 3.0.300+**。低于 3.0.300 的 HyperOS 无法显示原生实况通知。
> 3. **App设置**: 确认音乐 App 的蓝牙/通知歌词开关已打开。
</details>

<details>
<summary><b>Q4: Cannot connect to service? (无法连接服务？)</b></summary>

> **EN:** Permission revoked by system. Please re-grant "Notification Access".
>
> **CN:** 系统回收了权限。请重新手动授予“通知使用权”。
</details>

<details>
<summary><b>Q5: How to backup/restore settings? (如何备份/恢复设置？)</b></summary>

> **EN:** Go to Settings → Backup & Restore. Supports granular category selection (Capsule, Notifications, Appearance, General, Parser Rules, Advanced, etc.) for export/import. Sensitive data such as Last.fm credentials can be included only through an optional password-encrypted backup entry.
>
> **CN:** 前往 设置 → 备份与恢复，支持按类别（胶囊、通知、外观、通用、解析规则、高级等）选择导出或导入。Last.fm 等敏感凭据只有在主动选择并设置备份口令后，才会写入加密备份项。
</details>

<details>
<summary><b>Q6: How to submit feedback? (如何反馈问题？)</b></summary>

> **EN:** Please submit an issue at [GitHub Issues](https://github.com/FrancoGiudans/Capsulyric/issues/new?template=bug_report.yml) with logs from the **Log Console** (tap version/commit to open).
>
> **CN:** 请前往 [GitHub Issues](https://github.com/FrancoGiudans/Capsulyric/issues/new?template=bug_report.yml) 提交反馈，并附带通过点击版本号唤出的 **Log Console** 日志。
</details>

<details>
<summary><b>Q7: After hiding the app icon, how can I reopen Capsulyric? (隐藏桌面图标后如何重新打开应用？)</b></summary>

> **EN:** After hiding the launcher icon, you can still open Capsulyric via:
> 1. **Quick Settings Tile** — Pull down the notification shade and tap the Capsulyric tile.
> 2. **URL Scheme** — Type `capsulyric://settings` in any browser address bar, you can also click [there](capsulyric://settings).
> 3. **Manage Space** — Go to **System Settings → Apps → Capsulyric → Manage Space** to open the **Cache Management** page. Tap the top-left back button there to enter Capsulyric's **App Settings**; using the system Back button returns to System Settings. On some devices (e.g. HyperOS), this button may only appear after tapping "Clear Data".
>
> **CN:** 隐藏桌面图标后，仍可通过以下方式打开 Capsulyric：
> 1. **控制中心磁贴** — 下拉通知栏，点击 Capsulyric 磁贴。
> 2. **URL Scheme** — 在任意浏览器地址栏输入 `capsulyric://settings`，你也可以直接点击[这里](capsulyric://settings)快速进入。
> 3. **管理空间** — 前往 **系统设置 → 应用管理 → Capsulyric → 管理空间**进入**缓存管理**页面。点击页面左上角返回按钮可进入 Capsulyric 的**应用设置**；使用系统返回键则会回到系统设置。部分设备（如 HyperOS）上，该按钮需要点击"清除数据"后才能看到。
</details>

---

## Privacy (隐私说明)

- Reads only media playback notifications: album art, artist, song title, album name, and the playing app's package name. / 仅读取媒体播放通知：专辑图、歌手、歌名、专辑名与播放应用包名。
- Used solely for: playback display, lyric extraction, online lyric matching, Last.fm scrobbles, and diagnostics. / 仅用于：播放信息显示、歌词提取、在线匹配、Last.fm 记录与应用自身日志。
- Never reads chat messages, verification codes, emails, or other non-media notifications. / 不读取聊天消息、验证码、邮件等非媒体通知。
- On-device processing by default; network requests only for features you explicitly enable (Online Lyrics, Last.fm). / 默认本机处理；仅在明确开启在线歌词或 Last.fm 时联网。
- Last.fm credentials: encrypted with Android Keystore-backed AES-GCM and excluded from Android backup/device-transfer rules. Normal exports omit them; an optional, password-encrypted manual backup entry can include them. / Last.fm 凭据：Android Keystore AES-GCM 加密，并排除在 Android 备份/设备迁移之外。常规导出不会包含这些数据；只有主动选择并设置备份口令时，才会写入加密的手动备份项。
- Apple Music lyrics require an optional login token (media-user-token); it is encrypted on-device with Android Keystore AES-GCM, excluded from normal exports, and may be included only in a password-encrypted sensitive backup. / Apple Music 歌词需要可选的登录凭据（media-user-token）；该凭据以 Android Keystore AES-GCM 加密存储于本机，不随常规设置导出，仅在密码加密的敏感备份中可选包含。

Full privacy policy / 完整隐私说明: [PRIVACY.md](docs/PRIVACY.md)

---

## Project Structure (项目结构)

The Android app is organized by responsibility. Main package groups:
项目主体按职责拆分，主要包职责如下：

| Package / 包 | Responsibility / 职责 |
| :--- | :--- |
| `core/` | Shared platform utilities, settings, logging, cache, update, theme. / 通用平台能力、设置、日志、缓存、更新与主题。 |
| `lyrics/` | Lyric sources, online fetching, parsing, scoring, local lyrics, cache, export. / 歌词来源、在线获取、解析、评分、本地歌词、缓存与导出。 |
| `runtime/` | Foreground services, media-session monitoring, notification control. / 前台服务、媒体会话监听与通知控制。 |
| `feature/` | Screen-level features: settings, parser rules, diagnostics, OOBE. / 设置、解析规则、诊断、缓存管理与首次引导等页面级功能。 |
| `ui/` | Reusable UI, Material/Miuix themes, overlay renderers, capsule, Super Island. / 可复用 UI、Material/Miuix 主题、悬浮层、胶囊与超级岛。 |
| `integration/` | Privileged/external API bridges (Shizuku, system-level). / Shizuku 等特权/外部 API 与系统级集成。 |
| `rules/` | Parser-rule models, matching helpers, rule management. / 解析规则模型、匹配辅助与规则管理。 |

Build configuration is split between `app/build.gradle` and reusable scripts under `gradle/scripts/`, keeping versioning, signing, and Android app options separate. / 构建配置由 `app/build.gradle` 与 `gradle/scripts/` 下的脚本共同维护，用于拆分版本号、签名和 Android 应用配置。

For package boundaries and runtime data flow, see [Architecture](docs/ARCHITECTURE.md). / 更详细的包边界与运行时数据流见 [Architecture](docs/ARCHITECTURE.md)。

---

## Build (构建)

**Prerequisites / 环境要求:**
- **JDK 26** (e.g., Temurin 26) — required to compile Java 26 bytecode. / 需要 JDK 26（如 Temurin 26）以编译 Java 26 字节码。
- **Android SDK Platform 37** (API 37) — install via Android Studio SDK Manager. / 通过 Android Studio SDK Manager 安装 API 37 平台。

```bash
git clone https://github.com/FrancoGiudans/Capsulyric.git
cd Capsulyric
./gradlew assembleDebug
```

---

## License (开源协议)

Licensed under [GPL-3.0](LICENSE). / 基于 [GPL-3.0](LICENSE) 开源协议。

Third-party license texts are collected in [LICENSES/](LICENSES/); the full inventory of third-party components (source-tree embeddings and direct Gradle dependencies) is documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). / 第三方许可证全文见 [LICENSES/](LICENSES/)；完整第三方组件清单（源码嵌入与直接依赖）见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

## Credits (致谢)

- [HChenX/SuperLyric](https://github.com/HChenX/SuperLyric) (GPL-3.0)
- [HChenX/SuperLyricAPI](https://github.com/HChenX/SuperLyricApi) (LGPL-2.1)
- [xiaowine/Lyric Getter API](https://github.com/xiaowine/Lyric-Getter-Api) (LGPL-2.1)
- [wxxsfxyzm/InstallerX Revive](https://github.com/wxxsfxyzm/InstallerX-Revived) (GPL-3.0)
- [WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) (Apache-2.0)
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (Apache-2.0)
  - The self-wrapped controls under `app/src/main/java/com/example/islandlyrics/ui/miuix/` and `ui/material/` (e.g. `MiuixBlurDialog`, `MiuixBlurBottomSheet`, `BlurOverlayDropdownPreference`, `MaterialBlur*`) are modified from or built on miuix components (`OverlayDialog`, `OverlayBottomSheet`, `OverlayDropdownPreference`, `TopAppBar`/`Scaffold`, `MiuixPopupUtils`) and `miuix-blur`. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). 
  - 自封装控件（如 `MiuixBlurDialog`、`MiuixBlurBottomSheet`、`BlurOverlayDropdownPreference`、`MaterialBlur*`）修改自或基于 miuix 组件与 `miuix-blur`，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop`, Apache-2.0) — provides the Liquid Glass effects used by the app. / 为本应用提供液态玻璃效果。
- [xzakota/HyperNotification](https://github.com/xzakota/HyperNotification) (Apache-2.0)
- Ported/adapted source embeddings (Lyricify-Lyrics-Helper online lyric providers, InstallerX Revived Shizuku helpers, AOSP hidden API stubs) and the full direct-dependency list are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- 移植/改编的第三方源码（Lyricify-Lyrics-Helper 在线歌词提供方、InstallerX Revived Shizuku 辅助、AOSP hidden API stub）及完整直接依赖清单均记录于 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
