# Capsulyric 💊

![Badge](https://img.shields.io/badge/Android-16-green) ![Badge](https://img.shields.io/badge/HyperOS-Compatible-blue) ![License](https://img.shields.io/badge/License-MIT-yellow)

A lightweight, native "Dynamic Island" music visualizer for Android 16+, utilizing the official Promoted Ongoing Notification API.

## Intro
Capsulyric brings the elegance of the "Dynamic Island" concept to Android native interfaces. It serves as a persistent, interactive music controller that lives gracefully in your status bar area (on supported devices/ROMs) or as a sleek overlay. It is designed for minimal resource usage and maximum aesthetic appeal.

## Features

- **🏝️ Native Live Island**: leverages the Android 16 Status Bar Chip API (where available) for a seamless system integration.
- **🚀 Performance**: built for speed. Zero-image processing for the island visualization itself; uses pure text metadata for instant response.
- **🤖 Auto-Automation**: Intelligent global playback state monitoring. It automatically shows itself when music starts and hides when it stops.
- **🎨 Material Dashboard**: A beautiful, Material Design 3 configuration dashboard allowing users to manage whitelists, view diagnostics, and control service behavior.

## Tech Stack

- **Languages**: Java / Kotlin
- **Core APIs**:
    - `MediaSessionManager` (for global playback detection)
    - `NotificationListenerService` (for metadata extraction)
    - `WindowInsets` & Edge-to-Edge UI
- **Target SDK**: Android 16 (API 36)

## Prerequisites

- **Minimum OS**: Android 15 (API 35)
- **Target OS**: Android 16 (API 36)
- **Specific Support**: Validated on HyperOS 2.0+ devices.

## Build Instructions

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/yourusername/Capsulyric.git
    cd Capsulyric
    ```

2.  **Signing Configuration**:
    *   Create a valid Java Keystore (`keystore.jks`).
    *   Create a `local.properties` file in the project root if it doesn't exist.
    *   Add your signing secrets (DO NOT commit this file):
        ```properties
        store.file=your_keystore_path.jks
        store.password=your_store_password
        key.alias=your_key_alias
        key.password=your_key_password
        ```

3.  **Build**:
    *   Open in Android Studio.
    *   Run `./gradlew assembleRelease` to generate the production APK.

## Credits

Based on Google's live-updates sample and inspired by dynamic UI trends.
