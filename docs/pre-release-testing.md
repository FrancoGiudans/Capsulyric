# Pre-Release Testing

This project needs two gates before any Preview, Experiment, or Stable package is shared:

1. An automated smoke gate that proves the app and its instrumentation smoke tests still build.
2. A manual release gate that covers the high-risk runtime combinations we ship to users.

## 1. Automated Gate

Run these commands locally before tagging or uploading a package:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebugAndroidTest
./gradlew :app:assemblePrerelease
```

For Stable releases, replace `assemblePrerelease` with:

```bash
./gradlew :app:assembleRelease
```

What this gate catches:

- Kotlin or resource regressions in the shipping app.
- Instrumentation smoke tests that no longer compile.
- Activity launch regressions covered by `PreReleaseSmokeTest`.

## 2. On-Device Smoke Gate

Before a public package is published, run the smoke suite on at least one Android 35+ device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Current smoke coverage focuses on launch-time regressions in the highest-risk settings stack:

- `CustomSettingsActivity` in Material mode with predictive back enabled.
- `AboutActivity` in Material mode with a non-default language.
- `CustomSettingsActivity` in Miuix mode with predictive back enabled.
- `SettingsActivity` in Miuix mode with a non-default language.

If this suite fails, the build is blocked until the root cause is fixed or the test is updated with a documented reason.

## 3. Manual Release Matrix

Automation is the floor, not the ceiling. The following combinations must be checked manually on a release candidate:

- Material UI, English, predictive back on.
- Material UI, Simplified Chinese, predictive back off.
- Miuix UI, English, predictive back on.
- Miuix UI, Simplified Chinese, predictive back on.

For each combination, verify these flows:

- App launches without crash.
- `Settings`, `About`, and `Personalization` pages open without crash.
- Language switching applies immediately and survives activity recreation.
- UI style switching between Material and Miuix still works.
- Predictive back animation returns cleanly without broken layout or crash.
- Update entry points open correctly.
- Permission entry points still open the correct system screens.

## 4. Release-Only Checks

These checks should be done on the final signed build, not only debug:

- Install the signed APK over the previous public build to verify upgrade behavior.
- Open the app after upgrade and confirm setup state, settings, and theme preferences survive.
- Verify the release channel metadata is correct for Stable, Preview, or Experiment.
- Confirm the signed artifact and checksum are both generated and archived.

## 5. Suggested Sign-Off Order

Use this order to avoid wasting time on manual QA when the build is already broken:

1. `compileDebugKotlin`
2. `assembleDebugAndroidTest`
3. `assemblePrerelease` or `assembleRelease`
4. `connectedDebugAndroidTest`
5. Manual release matrix
6. Signed build install and upgrade check

## 6. Release Blockers

Do not publish if any of the following are true:

- Any smoke test crashes or fails.
- Any settings page fails to open in either UI mode.
- Language switching does not update the current activity.
- Predictive back causes a crash or broken return animation.
- The signed artifact cannot upgrade from the current public build.
