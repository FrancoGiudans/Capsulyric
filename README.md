# Capsulyric 🏝️

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
**Supported Apps (支持列表)**:

| App | Package Name | Setup (设置) |
| :--- | :--- | :--- |
| **QQ Music**<br>(QQ音乐) | `com.tencent.qqmusic` | App Settings → QPlay & Car → Enable **"Car Bluetooth Lyric"**<br>(设置 → QPlay与车载 → 开启“车载蓝牙歌词”) |
| **NetEase Music**<br>(网易云音乐) | `com.netease.cloudmusic` | Settings → Tools → Enable **"Car Bluetooth Lyrics"**<br>(设置 → 工具 → 开启“外接设备蓝牙歌词”) |
| **Xiaomi Music**<br>(小米音乐) | `com.miui.player` | Enabled by default (Car mode)<br>(默认支持) |

**Setup Instructions**:
1. Enable the specific "Car Bluetooth/Lyric" setting in your music app.
2. Grant **Notification Access** to Capsulyric.
3. *Note: Some newer QQ Music versions may require an actual Bluetooth connection.*

---

## 🛡️ Privacy & Disclaimer (隐私与免责)

* **Local Only**: No internet permission. No data transmission.
* **Safe**: Zero hooking mechanisms in the app itself.
* **Disclaimer**: The developer assumes no liability for use.
> 软件完全 0-hook，无网络权限，不传输任何数据。开发者不对软件使用负责。

---

## 🛠️ Build (构建)

```bash
git clone https://github.com/YourRepo/IslandLyrics.git
cd IslandLyrics
./gradlew assembleDebug
```

---

## 🤝 Credits (致谢)

* [SuperLyric](https://github.com/HChenX/SuperLyric) (GPL-3.0)
* [SuperLyricAPI](https://github.com/HChenX/SuperLyricApi) (LGPL-2.1)
