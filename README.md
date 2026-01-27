# Capsulyric 💊

A lightweight music visualizer utilizing the Android 16 Promoted Ongoing Notification API to display lyrics in the status bar.

## Introduction
Capsulyric utilizes the native Status Bar Chip API (Android 16+) to display real-time lyrics and music status. It functions as a persistent notification service that monitors media playback and displays text metadata in the status bar area.

> **Note**: This project is currently in an experimental stage. It has not been fully verified on physical devices and is primarily targeted for Android 16 and HyperOS 3.0.300+ environments.

## Features

- **Status Bar Integration**: Uses the Android 16 `Promoted Ongoing Notification` (Status Bar Chip) to display lyrics directly in the system bar.
- **Lightweight**: Focuses purely on text metadata (Title/Artist/Lyrics) without heavy image processing or album art extraction.
- **Automation**: Automatically detects global playback state to show or hide the notification service.
- **Management UI**: A simple Material Design 3 dashboard for viewing logs, diagnostics, and managing the app whitelist.

## Prerequisites

- **Target OS**: Android 16 (API 36)
- **Minimum OS**: Android 15 (API 35)
- **System Requirement**: HyperOS 3.0.300+ or generic Android 16+ builds supporting the new notification chip API.

## Tech Stack

- Java / Kotlin
- `MediaSessionManager` for playback state detection
- `NotificationListenerService` for metadata retrieval
- Edge-to-Edge UI compliance

## Build Instructions

1.  **Clone the Repository**:
    ```bash
    git clone [https://github.com/FrancoGiudans/Capsulyric.git](https://github.com/FrancoGiudans/Capsulyric.git)
    cd Capsulyric
    ```

2.  **Build**:
    * Run `./gradlew assembleRelease` to generate the APK.

## Credits & Acknowledgements

* **[SuperLyricApi](https://github.com/HChenX/SuperLyricApi)**: Special thanks to HChenX for the inspiration and underlying logic references regarding lyric processing.
* **Google Live Updates Sample**: Implementation references for the Promoted Ongoing Notification API.

