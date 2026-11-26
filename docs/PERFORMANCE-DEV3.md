# Performance preview 0.7.0-native-dev.3

This patch is based on `65375b2`. Priority is rendering responsiveness while retaining the existing launcher features, glass, spring motion, and settings. This is an incremental patch, not a completed UI rewrite.

## Changes and evidence

| Path | Previous behaviour | Current behaviour | Expected effect |
|---|---|---|---|
| Search visibility | Getter read frame-changing spring values in the home composition | Derived Boolean invalidates consumers only at visibility changes | Avoids home recomposition solely for visibility polling |
| Search apps and suggestions | Filter/sort/deduplicate when the composable called the function | Derived lists update with query, app collection, ranking or recents | Less repeated CPU work while animating |
| Wallpaper behind search | Live screen-sized variable-radius blur in app fallback | Background-generated cached blurred image crossfades with the sharp image | Removes the search-only live wallpaper filter once the cache is ready |
| Other animated blurs | New blur effect for every float radius | Bounded shared cache with integer pixel radii | Reduces filter creation, retains GPU blur |
| Search dismissal | Non-observable close flag and no cancellation cleanup | Observable state reset in finally | Correct invalidation and cleanup on interruption |
| Device search | Old contact/photo/file rows remained during new query debounce | Results cleared when the query changes | Avoids presenting stale hits as new matches |
| Icon masks | Composited gradient considered before adaptive foreground; only solid rectangles rejected | Native monochrome, then adaptive foreground, then extracted artwork; reject dense masks including filled disks; fit oversized symbols | Prevents some background silhouettes and clipping |
| Unsupported masks | Full-tile grayscale/duotone fallback | Inset tonal fallback preserves recognisable internal detail | Uniform margins instead of edge-to-edge fallback art |

The cached-wallpaper transition blends sharp and fully blurred endpoints. It is a performance tradeoff: intermediate frames are not mathematically identical to animating a Gaussian radius. The system wallpaper blur path remains available. Home icons still use a live blur and the search pill still performs layout during its size morph. Further device profiling may justify changing these paths.

The mask classifier is heuristic. The screenshots identify visual failure cases but do not supply the installed apps' drawables or icon-pack assets. The actual reported icons must be checked on the phone. No claim that every icon has been fixed is made. Luminance-preserving fallback is deliberate where a one-colour mask would erase the logo. Layer cache version 5 regenerates old masks off the UI thread.

## Display rate

Settings → Appearance → Display refresh rate offers System and rates the display advertises at its current resolution. It persists the selected rate and reapplies it on resume/display changes. A missing mode falls back to System. It sets the public `WindowManager.LayoutParams.preferredRefreshRate` hint, without forcing resolution changes or introducing a timer. Animation clocks remain frame-synchronised. Android may override the hint for power, thermal or display policy reasons. Selecting 120 Hz does not establish 120 FPS.

API reference: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#preferredRefreshRate

## Compatibility and installation

The old preview's private signing key was unavailable in this workspace. The preview build therefore uses application ID `io.github.janhurn9400.liquidy.preview.performance`, label **Liquidy Performance**. It installs alongside the prior preview and original launcher. Its application data is separate. Existing original installations and preferences are untouched; there is no automatic transfer between Android application sandboxes. The default source build retains the original application ID and preference formats.

No privileged dependency or new external library was added. Settings options, folders, search sources, widgets, themes and animation controls are preserved.

## Build

Use JDK 21, Android SDK platform 37.0, build tools 36.0.0, and the Gradle 9.7.0 wrapper. Set `ANDROID_HOME` or a local `sdk.dir`.

```sh
./gradlew -PliquidySideBySide=true :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
```

APK output is `app/build/outputs/apk/release/app-release.apk`. Development signing uses the local debug keystore. Retain that keystore privately to create compatible updates. Never use a development key for a production distribution.

## Device validation and performance comparison

No Android device was attached during this patch. Device frame times, jank percentages, startup, memory and power measurements are unavailable for both revisions. Source-level reductions in work are not a measured speedup. No 60/90/120 FPS guarantee is made.

On the Moto G Stylus 5G (2023), compare the old preview and this candidate under the same wallpaper, app count, icon style, battery state and effective display rate. Warm caches first, then record cold startup separately. Record twenty search open/close gestures with several interrupted reversals, repeated typing/deleting, page swipes and folder openings. Repeat at System, 60 Hz and 120 Hz if advertised. Check the new selector after process termination and theme changes, and exercise gesture and three-button navigation.

```sh
LIQUIDY_PACKAGE=io.github.janhurn9400.liquidy.preview.performance tools/performance/capture.sh reset
# Exercise the launcher now.
LIQUIDY_PACKAGE=io.github.janhurn9400.liquidy.preview.performance tools/performance/capture.sh record captures/dev3
python3 tools/performance/frame_stats.py captures/dev2/frames.txt captures/dev3/frames.txt --hz 120
```

Use the actual display rate for `--hz`, not just the selected preference. The script reports average, p95/p99 and deadline misses. Perfetto configuration includes both preview packages. Frame timestamps alone do not prove touch-to-photon latency or GPU utilisation.

## Remaining verification

Actual icon artwork, motion appearance, IME interruption behaviour, glass transition quality, memory pressure and sustained device frame pacing require on-device tests. Settings selectors and bitmap rendering were not visually tested on an emulator. See the validation section below for checks completed in this workspace.

## Workspace validation

- 23 JVM unit tests passed, with zero failures or errors. This includes six icon-mask classification cases and four display-rate selection cases, plus existing geometry, spring, settings-colour and blur tests.
- Shell syntax validation for the capture script and `git diff --check` passed.
- `adb devices -l` returned no connected devices. Device frame pacing and screenshots of the new build were not tested.

- Combined Gradle validation completed successfully. Release APK assembly passed and APK v2 signature verification passed. Manifest reports version code 14, version 0.7.0-native-dev.3, application ID io.github.janhurn9400.liquidy.preview.performance and label Liquidy Performance.
- Lint completed with 0 errors and 103 warnings. The pre-existing ModifierParameter detector exclusion remains because of the documented toolchain crash. Warnings are not treated as resolved by this patch.
