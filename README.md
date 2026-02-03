# Capsulyric 🏝️

[![Latest Release](https://img.shields.io/github/v/release/FrancoGiudans/Capsulyric?include_prereleases&style=flat-square&label=Latest&color=orange)](https://github.com/FrancoGiudans/Capsulyric/releases/latest)

[![License](https://img.shields.io/github/license/FrancoGiudans/Capsulyric?style=flat-square&color=blue)](LICENSE)

> **Provides status bar lyrics based on Live Update.**
> **提供基于 Live Update 机制的状态栏歌词。**

---

## 📱 Compatibility (兼容性)

| Component | Requirements |
| :--- | :--- |
| **Android Version** | Android 16+ (Baklava) |
| **HyperOS** | Version 3.0.300+ |
| **ColorOS** | Version 16+ |

---

## ⚙️ Working Modes (工作模式)

### 🚀 Mode 1: LSPosed (Recommended)
**Requirement**: [SuperLyric](https://github.com/HChenX/SuperLyric) installed and activated.
> 要求安装并激活 SuperLyric。支持所有 SuperLyric 适配的音乐应用。

### 🛡️ Mode 2: 0-Hook (No Root)
**Supported Apps (默认支持列表)**:

| App | Package Name |
| :--- | :--- |
| **QQ Music**<br>(QQ音乐) | `com.tencent.qqmusic` |
| **NetEase Music**<br>(网易云音乐) | `com.netease.cloudmusic` | 
| **Xiaomi Music**<br>(小米音乐) | `com.miui.player` | 

**Setup Instructions**:
1. Enable the specific "Car Bluetooth/Lyric" setting in your music app.
2. Grant **Notification Access** to Capsulyric.
3. *Note: Some newer versions may require an actual Bluetooth connection.*

---

## 🛡️ Privacy & Disclaimer (隐私与免责)

* **Local Only**: No internet permission. No data transmission.
* **Safe**: Zero hooking mechanisms in the app itself.
* **Disclaimer**: The developer assumes no liability for use.
> 软件完全 0-hook，无网络权限，不传输任何数据。开发者不对软件使用负责。

---

## 🛠️ Build (构建)

```bash
git clone https://github.com/FrancoGiudans/Capsulyric.git
cd Capsulyric
./gradlew assembleDebug
```

---

## 📜 开源协议 / License

本项目基于 [GPL-3.0](LICENSE) 协议开源。

这意味着你可以自由地使用、修改和分发本项目的代码，但**如果你分发了修改后的版本，你也必须使用 GPL-3.0 协议开源**。

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

## 🤝 Credits (致谢)

* [SuperLyric](https://github.com/HChenX/SuperLyric) (GPL-3.0)
* [SuperLyricAPI](https://github.com/HChenX/SuperLyricApi) (LGPL-2.1)
* [InstallerX Revive](https://github.com/wxxsfxyzm/InstallerX-Revived) (GPL-3.0)
