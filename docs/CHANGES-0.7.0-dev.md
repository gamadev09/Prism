# Liquidy 0.7.0-dev

## Changes

- The phone grid centres by default. The original Side margin control offsets that centre, and drawing and touch
  targets agree. Category grids use the same adjustment. The dock keeps its own spacing.
- Failed app scans keep the last complete layout, and saved entries from paused work profiles retain their slots.
  Rendered icons are reused until their app, icon pack or calendar artwork changes.
- Settings and system bar icons follow Android light and dark appearance.
- Frosted glass elements share a completed blur for each wallpaper and radius. Refraction reuses wallpaper shader
  inputs across components. Folder snapshots share a smaller
  source image and preserve the alpha of blurred icons when Android draws the wallpaper. The glass amount slider,
  refraction, folder animations and both blur levels remain active.
- App-opening animations finish or release their surfaces on cancellation or draw failure. They reuse one transaction
  and retry the hidden method lookup when Shizuku grants access.
- Shizuku app launches use a background thread and fall back to Android launch options on failure. The existing
  Magisk backend is still available on rooted devices.

## Shizuku setup

1. Install Shizuku from https://shizuku.rikka.app/download/ and start it through wireless debugging.
2. Open Liquidy Settings > Advanced > App animations > Connect Shizuku (no root).
3. Authorise Liquidy in Shizuku. Wait for Connected, then launch an app from its home icon.
4. Use Disconnect Shizuku on the same page to return to normal animation behaviour.

The preference persists, but Shizuku must be started after a reboot on non-root devices. Binder death,
missing permissions, unsupported reflection/API signatures and rejected starts fall back to standard launch options.
Android's shell manifest includes CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS on the inspected Android 14 source;
permission and hidden API compatibility are still checked at runtime. Manufacturer behaviour may differ.

This is an app-opening backend, not a replacement for Quickstep/Recents or a system launcher. Widget PendingIntents,
work-profile policy, system closing gestures and Recents retain Android behaviour. Work-profile icon launches pass
through the actual UserHandle; shell access is subject to Android policy. A runtime failure disconnects the backend.

## Build and tests

Requires JDK 21 (to run Gradle/AGP), Android SDK platforms 36 (compile-only stubs) and 37.0 (app), and dependency access.

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`. The included Android validation GitHub workflow performs
these commands and uploads an APK artifact before running full debug lint. The workflow has not been submitted or run here.
The project retains upstream's debug signing configuration; an existing installed APK with a different signing
certificate cannot be updated in place. Back up the launcher layout before any reinstall.

Regression tests exercise actual HomeGeometry across phone widths/densities, cell-to-drag-target round trips and
shared grid/dock centring. The build is a release variant to avoid debug rendering overhead.

### Required device verification

- On the Moto G Stylus 5G 2023, compare grid left/right margins; drag each column into folders and the dock.
- Inspect Shizuku, PocketPal and other adaptive icons in Default, Dark, Clear and Tinted styles.
- Compare the previous and patched builds at the same refresh rate and wallpaper, with full glass effects with `adb shell dumpsys gfxinfo
  io.github.janhurn9400.liquidy reset`, swipe pages/open folders, then collect `adb shell dumpsys gfxinfo
  io.github.janhurn9400.liquidy framestats`.
- Shizuku: authorise, deny, stop server, restart server, reboot, disconnect; app launches must remain available.
- Test quick repeat taps, launching an already-running app, returning Home, work profiles, widgets and Recents.

## Primary API references

- https://github.com/RikkaApps/Shizuku-API/blob/master/README.md
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/packages/Shell/AndroidManifest.xml
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/core/java/android/app/IActivityTaskManager.aidl
- https://github.com/LSPosed/AndroidHiddenApiBypass/blob/main/README.md

## Measuring the graphics change

`tools/performance/frame_stats.py` compares two `dumpsys gfxinfo ... framestats` captures from the same phone,
refresh rate and gesture sequence. It prints frame count, median/p95 completion time and the fraction exceeding
the refresh-rate budget. Its parser was checked with synthetic data; real before/after captures are still required.

The patched source compiled and passed all eight unit tests. The release build, release lint checks and APK signature
verification succeeded. The APK uses the workspace's debug signing key. Android requires the same signing key to
update an existing installation. Device rendering, frame timings and Shizuku launches still require a phone test.

Full debug lint also reports existing project issues, including an Android 14 widget API called without an Android 13 guard.
