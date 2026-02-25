# Capsulyric

[![Latest Release](https://img.shields.io/github/v/release/FrancoGiudans/Capsulyric?include_prereleases&style=flat-square&label=Latest&color=orange)](https://github.com/FrancoGiudans/Capsulyric/releases/latest)
[![License](https://img.shields.io/github/license/FrancoGiudans/Capsulyric?style=flat-square&color=blue)](LICENSE)

> **Provides status bar lyrics based on Live Update.**
> **提供基于 Live Update 机制的状态栏歌词。**

---

## Compatibility (兼容性)

* **Android Version**: Android 16+ (Baklava)
* **HyperOS**: Version 3.0.300+ (Required for Live Update model)

---

## Working Modes (工作模式)

### 1. Live Update
* **Requirements**: Android 16+
* **Special Note**: For HyperOS devices, OS version 3.0.300+ is required.
> 要求 Android 16+。针对小米设备，需 HyperOS 3.0.300+ 版本。

### 2. 小米超级岛 (Xiaomi Super Island)
* **Requirements**: HyperOS 3.0+
* **System**: Root access is required.
> 要求 HyperOS 3.0+，且系统需要 Root 权限。

---

## Lyric Acquisition (歌词获取方式)

1. **媒体通知歌词 (Media Notification Lyrics)**
   * Detects lyrics from standard media notifications.
2. **在线歌词 (Online Lyrics)**
   * Fetches lyrics from online servers based on playback info.
3. **Superlyric API**
   * **Requirements**: Root access is required.
   * High performance and accurate lyric syncing.

---

## Privacy (隐私说明)

* **No Data Transmission**: If **Online Lyrics** is disabled, the app does not transmit any data. Everything stays local.
* **Online Mode**: When **Online Lyrics** is enabled, only playback information (title, artist, album) is sent to the API interface to fetch lyrics.
> **隐私声明**：如果不开启“在线歌词”功能，本软件不会传输任何数据。开启后，只会向 API 接口发送当前播放信息以获取歌词。

---

## Build (构建)

```bash
git clone https://github.com/FrancoGiudans/Capsulyric.git
cd Capsulyric
./gradlew assembleDebug
```

---

## 开源协议 / License

本项目基于 [GPL-3.0](LICENSE) 协议开源。

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

---

## Credits (致谢)

* [SuperLyric](https://github.com/HChenX/SuperLyric) (GPL-3.0)
* [SuperLyricAPI](https://github.com/HChenX/SuperLyricApi) (LGPL-2.1)
* [InstallerX Revive](https://github.com/wxxsfxyzm/InstallerX-Revived) (GPL-3.0)
* [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) (Apache-2.0)
* [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (Apache-2.0)
