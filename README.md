# Capsulyric 💊


A lightweight music visualizer utilizing the Android 16 Promoted Ongoing Notification API to display lyrics in the status bar.

## Introduction
Capsulyric utilizes the native Status Bar Chip API (Android 16+) to display real-time lyrics and music status. It functions as a persistent notification service that monitors media playback and displays text metadata in the status bar area.

> **Note**: This project is currently in an experimental stage. It has not been fully verified on physical devices and is primarily targeted for Android 16 and HyperOS 3.0.300+ environments.

## ⚠️ Important Dependency
**This app requires the [SuperLyric](https://github.com/HChenX/SuperLyric) LSPosed module to function correctly.**
Capsulyric relies on the broadcast data provided by the SuperLyric module to fetch real-time lyrics. Please ensure your device is rooted and has the SuperLyric module installed and activated in LSPosed before using this app.

## Features

- **Status Bar Integration**: Uses the Android 16 `Promoted Ongoing Notification` (Status Bar Chip) to display lyrics directly in the system bar.
- **Lightweight**: Focuses purely on text metadata (Title/Artist/Lyrics) without heavy image processing.
- **Automation**: Automatically detects global playback state to show or hide the notification service.
- **Management UI**: A simple Material Design 3 dashboard for viewing logs, diagnostics, and managing the app whitelist.

## Prerequisites

- **Target OS**: Android 16 (API 36)
- **Minimum OS**: Android 15 (API 35)
- **System Requirement**: HyperOS 3.0.300+ or generic Android 16+ builds supporting the new notification chip API.
- **Root Environment**: LSPosed Framework installed.

## Disclaimer

This software is provided "as is", without warranty of any kind. The developer is not responsible for any damage to your device, data loss, or system instability that may result from installing or using this application, especially considering it requires a Rooted environment and Xposed modules. Use at your own risk.

## Privacy Policy

**Capsulyric respects your privacy.**
- We do **not** collect, store, or transmit any personal information.
- We do **not** track your location or usage habits.
- The app only reads media metadata (lyrics, song titles) locally on your device to display them in the notification bar. No data leaves your device.

## Credits & Acknowledgements

* **[SuperLyricApi](https://github.com/HChenX/SuperLyricApi)**: Special thanks to HChenX for the inspiration and underlying logic references regarding lyric processing.
* **Google Live Updates Sample**: Implementation references for the Promoted Ongoing Notification API.
