# Liquidy native rebuild

## Baseline audit

The starting revision is `b90c9f1` (0.7.0-dev). The historical measurements in [HANDOFF-PERF.md](HANDOFF-PERF.md) concern a different revision and a Xiaomi under LineageOS. They locate a previous glass recording bottleneck but cannot serve as the before numbers for this rebuild. The baseline checkout and release APK are available for paired measurements. The current Android toolchain has no attached target or hardware acceleration device.

| Surface | Starting implementation and planned disposition |
|---|---|
| App discovery and launches | Preserve LauncherApps, app profiles, package callbacks, shortcuts, duplicate instances, and system uninstall flows. Replace the privileged launch service and private Quickstep return protocol with public system transitions. |
| Home grid, dock and layout | Preserve Classic and Duo, edit/reorder, gap and page visibility, drag/reflow, dock limits, overview, widget sizes/stacks, and layout JSON. Inspect pixel geometry and scope per-frame invalidation. |
| Folders | Preserve automatic naming, mini grids, pagination, rearrangement, rename, fly-in, drag-out and context actions. Audit hit geometry and the open/close controller. |
| Today and App Library | Preserve Today widgets, category classification, hidden apps, suggestions, A–Z list and drag to Home. Audit which surfaces draw and compose when offscreen. |
| Search | Preserve interactive pull, pill morph, app, shortcut, local-file/contact and optional web results and IME inset handling. Audit gesture handoff and keyboard dispatch. |
| Context, edit and unlock | Preserve long press, shortcut menus, deletion/hiding, edit controls, wiggle, unlock and haptics. Audit lifecycle and instead of timer polling use system signals and the frame clock. |
| Icons | Preserve pack support, Default, Dark, Clear and wallpaper/tint controls, native monochrome masks and generated masks for unthemed icons. Audit invalidation, I/O and cache ownership. |
| Wallpaper and glass | Preserve native wallpaper, editor (Pair, Colour, Gradient, Photo and crop), brightness/label adaptation, calibration, full blur, refraction and tint. Retain the wallpaper shader and shared blur cache. External wallpaper pixel access remains subject to Android access rules. |
| Widgets | Preserve Android widget host, resize, configure, movement and stacks, and built-in clocks, calendars, batteries, weather, photos and reminders. Remove hidden click handler. Optional weather requires network for new forecasts, with the launcher usable offline. |
| Settings and setup | Preserve haptics, default home, own status bar, glass, icon packs, Duo and quirks, permissions, diagnostic/reporting, repeated setup and About. Replace animation setup with a system-API equivalent. Retain persisted settings and add explicit System, Light and Dark settings appearance. |
| Diagnostics and tuning | Preserve tunables, export, testing controls and user-started reports/recording. Remove synthetic performance hint reports. |

The hot path already uses a custom gesture arena, Compose and shader-backed glass. HomeScreen combines gestures, placement and rendering in one large function. HomeModel mixes durable layout/business state with transient input and visual transitions. Independent jobs and pointer-sample coroutines compete for animation state. Folder geometry and search contain paths that read progress in measurement or composition. Widget tap snapshots, permission polling, and a dedicated unlock polling loop add avoidable work. App repository and wallpaper blur already use background workers.

## Framework decision

Android Views provide an efficient fixed hierarchy, Choreographer and accessibility/IME support. A Views rewrite would require reimplementing the existing slot, gesture and glass boundaries and bridging the widget host again. A Canvas/GL renderer could batch icons but would also need virtual accessibility nodes, text measurement, widgets, inset-aware text input and substantial texture management. Neither option has a comparable benchmark for this codebase.

Keep the Compose shell and existing AndroidView widget interoperability. Use dedicated motion state and custom placement for hot surfaces, state reads in graphics layers, and cached GPU wallpaper and glass rendering. Compose can skip composition and measurement when the state is read in a later phase. No framework is credited with a performance win until the paired traces exist.

The reference fits in [RESEARCH.md](RESEARCH.md#3-springs-apple--compose) specify response, damping, and measured behaviour for paging, folder, search, context menus, Library, jiggle and unlock. They are inherited reference measurements, with their own provenance and error. Apple’s [Animate with springs](https://developer.apple.com/videos/play/wwdc2023/10158/) and [Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) describe the velocity continuity and interruption behaviour. Android’s [Compose phases](https://developer.android.com/develop/ui/compose/phases) and [ActivityOptions](https://developer.android.com/reference/android/app/ActivityOptions) document the rendering and launch boundaries.

## Implemented

| Area | Change |
|---|---|
| Platform app transitions | App icons and built-in widgets use public, origin-based Android scale-up when enabled. Search and other launches without a visible origin use the default transition. Android widgets use their public host click handling. |
| Privileged dependencies | Removed the Shizuku API and provider, HiddenApiBypass, the animation AIDL and shell service, the hidden-framework Gradle module, Magisk tooling, the remote-transition permission and the private Quickstep return protocol. Wallpaper display discovery uses Android's public display list and observed display sizes. |
| Motion | Synchronous pointer writes and frame-clock spring release for home paging, search pull and folder paging. App Library pull now passes the resisted finger velocity into its release spring. Retargeting carries current position and velocity. Tap a closing folder at its origin to reverse the in-flight transition. The existing fitted timings still drive jiggle, context menus, rearrangement, folder zoom, Library and unlock. |
| Home rendering | One hardware transform per visible page replaces placement updates on every icon during a swipe. Glass samples the wallpaper from the current position as the page moves. Large folders lay out the visible page span and use distinct instance IDs for duplicate app icons. Full glass, blur, widget rendering and wallpaper effects stay available. |
| Geometry | The classic grid remains centred. Row layout, widget height and hit targets share a pitch that accounts for the available space above Search and the navigation bar. The first row clears a tall top cutout. |
| Icons | Tint uses a shared colour filter while cached icon masks warm up or if a mask could not be generated. Install or icon-pack changes continue to invalidate affected layers. A rescan recognises an app that already has a duplicate shortcut in the saved layout. |
| Settings | Existing categories and controls remain. Settings appearance has persistent System, Light and Dark choices and matching system bar contrast. Settings permission rows refresh on resume. Row toggles have switch semantics, the glass slider exposes a range and the appearance choices expose radio button state. The native navigation inset is respected. |
| Lifecycle and API compatibility | Unlock uses a scheduled frame triggered by lifecycle, focus and unlock events. Android 13 uses the compatible wallpaper drawable API. Wallpaper crop rasterisation runs on a worker. Locale-aware widgets update when Android changes locale. |

Existing layout JSON, icon appearance, wallpaper, widget, reporting and tuning preference keys keep their format. Settings appearance defaults to System for earlier installs. The old privileged animation preference has no equivalent authorisation and is ignored. The new launch origin option defaults to on. Home folders retain automatic naming when combined and the App Library retains automatic categorisation.

Compose Foundation and AndroidX support layout, semantics, input and lifecycle. Kyant Backdrop and Shapes remain in use for the existing glass and squircle drawing. Material 3 remains in use for the developer tuning controls. The runtime has no new third-party dependencies.

### Android boundaries

Android controls the opening window animation after a launch request and owns predictive back, recents, swipe-to-home, app close and the keyboard. An ordinary Home app can supply an origin to `ActivityOptions.makeScaleUpAnimation` but cannot animate another app's live surface back into its exact icon or drive the system gesture interactively. Manufacturer behaviour can vary. App launch uses `LauncherApps.startMainActivity` so the activity opens in the correct profile.

Wallpaper assigned in Liquidy can be rendered from its known pixels. For externally selected static wallpaper the launcher can sample the wallpaper only when Android grants access. All-files access is optional and enabled through standard Android Settings. Without it, the existing fallback artwork supplies pixels for glass. Live wallpaper pixels and compositor blur support are device-dependent.

Installed apps, folders, layouts, widgets and on-device search operate offline. Updated weather and enabled web suggestions require network access. Widget binding, notification badges, contacts, photos, calendars and usage-based suggestions use Android's user-granted access.

The development APK uses a different certificate from the earlier patched download. Android requires a matching signing certificate for an update, and a same-certificate installation preserves the saved layout. The source can be built with the installed version's original signing key when that key is available.

### Reproducible build

Install JDK 21, the Android SDK platform 37.0, Build Tools 36.0.0 and Platform Tools. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
./gradlew :app:testDebugUnitTest :app:assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

On Windows use `gradlew.bat` and the SDK's `apksigner.bat`. If reproducing the baseline, check out `b90c9f1` and install `platforms;android-36` as well.

## Paired performance validation

| Measure | Baseline | Candidate |
|---|---|---|
| Build and JVM tests | Release build passed, 8 tests passed | Release build and release-vital lint passed, 13 tests passed |
| Signed APK size | 26,463,618 bytes | 26,309,862 bytes |
| Average, p95 and p99 frame time, Android jank, frame deadline misses | Device capture pending | Device capture pending |
| Cold startup, touch latency, RSS/PSS, CPU, GPU and garbage collection | Device capture pending | Device capture pending |
| Native launch and return, unlock, rapid gestures, interruption and large folders on Android | Device run pending | Device run pending |

Debug lint completed with zero errors after disabling the Compose ModifierParameter detector, which crashes with this compiler and lint toolchain.

No physical device or hardware-accelerated emulator was available in the workspace. APK packaging and JVM tests do not establish frame pacing or interactive correctness on the Moto. The code removes per-sample coroutine launches for the selected gestures, bounds folder composition, and avoids bitmap snapshots at app/widget tap. These are implementation changes. The magnitude of any runtime gain remains to be measured.

Use the Moto G Stylus 5G (2023) on Android 14 first. Use the same Home role, apps, folder contents, glass level, wallpaper, animation settings, build type, battery mode and phone temperature across both builds. Match compilation mode, for example `adb shell cmd package compile -m speed -f io.github.janhurn9400.liquidy` on both. Record each supported display mode independently. The nominal frame budgets are 16.67 ms at 60 Hz, 11.11 ms at 90 Hz and 8.33 ms at 120 Hz. Record the display dump and per-frame deadlines, since the effective refresh rate can change during a scenario.

Per APK, run each gesture scenario in short segments. Run `tools/performance/capture.sh reset` before a segment and immediately run `tools/performance/capture.sh record <directory>` afterwards. Use `tools/performance/capture.sh startup <directory> 10` for process-cold launch measurements. Compare equal segments with:

```sh
python3 tools/performance/frame_stats.py baseline/frames.txt candidate/frames.txt --hz 120
```

Gfxinfo keeps a rolling frame history. Its frame durations, application deadline misses, and Android's reported jank count measure different things. For dropped frames, presentation, CPU scheduling, garbage collection and touch-to-present when the necessary trace events exist, collect system traces:

```sh
adb shell perfetto --txt -c - -o /data/misc/perfetto-traces/liquidy.perfetto-trace < tools/performance/liquidy.perfetto.pbtx
adb pull /data/misc/perfetto-traces/liquidy.perfetto-trace
```

Repeat with identical interaction scripts and open the two traces in Perfetto. Use FrameTimeline for the launcher window and surface presentation, per-thread scheduler slices for CPU, and ART events for GC. Collect GPU counters in a separate Android GPU Inspector run where the device supports them. Android Studio allocation and memory profiles should be run separately from frame captures. The scripts use standard ADB and run outside the APK. The parser was checked against a fixture with duplicated and flagged frames and per-frame deadlines.

### On-device regression matrix

| Workload | Verify |
|---|---|
| Rapid folder taps, close reversal, big folders and folder dragging | Folder size, correct keys and hit targets, no stale or duplicate views, correct landing and animation handoff |
| Rapid paging, drag between pages and dock, and Library category/list transitions | Same finger position and release direction, edge resistance, offscreen clipping and correct anchors |
| Open/close Search repeatedly and dismiss the keyboard, then launch apps from Home, folders, Library, Search and widgets | Current query and focus, app starts, correct profile, normal system Home/Recents behaviour |
| Toggle themes and icon tint, install/uninstall applications, set or change the wallpaper | Appearance and insets, unthemed icon fallback, stable layout and correct cache invalidation |
| Rotate or fold where supported, switch gesture and three-button navigation, and test a cutout | Classic/Duo placement, status controls, widgets, cutouts and readable settings |
| Lock/unlock, background/foreground, terminate and restart the launcher, and induce memory pressure | Stable restoration, working home animations, widget state and saved layout |

### Remaining work

Pair device traces and run the regression matrix on the Moto before calling the build production-ready. Compare current animation recordings with the original motion fits and tune only from observed timing/geometry. Measure the folder backdrop snapshot, animated blur and Library transitions separately. Further work includes separating HomeModel layout state from secondary editors and consolidating their gesture controllers. A Gradle Macrobenchmark device module and a broad instrumented UI suite are also pending.


## Follow-up preview

- Small and Large Icons now use one interruptible spring with a shared position.
  Home app and folder icons interpolate scale and position, labels fade early, and badges and
  the size control follow the same progress. The existing grid endpoints and preferences still apply.
- The supplied screenshot showed a 29-pixel Drive mark in a tile about 190 pixels wide.
  The icon mask renderer now fits very small glyphs to the normal visual scale, rejects solid
  plates, and retains tonal detail if it cannot extract a useful silhouette. The style cache regenerates its entries.
- Clear and dark Tinted glass icons sample one prepared wallpaper rather than applying blur
  effects individually. Wallpaper glass caches its shape across position changes.
- Cold Home startup no longer generates a full-size fallback wallpaper on the UI thread.
  Android's wallpaper is visible while the image and immutable copies are prepared off-thread.
  Other transitions, including Search and folder blur, retain their own rendering paths.

The APK labelled Liquidy Preview installs beside Liquidy under a separate package ID, with its own
preferences. The earlier installation and its layout remain intact. Choose Liquidy Preview as the
default Home app to test it. To generate that APK from the source:

```sh
./gradlew :app:assembleRelease -PliquidySideBySide=true
```

For ADB captures of the side-by-side preview, set `LIQUIDY_PACKAGE=io.github.janhurn9400.liquidy.preview` when running `tools/performance/capture.sh`. The Perfetto configuration includes both packages.

The default build uses the original package ID. An in-place update requires the original signing
certificate. The icon mask tests use sizes and foreground counts reproduced from the screenshot.
The new animation uses the existing reflow spring, with timing and frame pacing still to be
validated on the Moto.

## Performance preview dev.3

See [PERFORMANCE-DEV3.md](PERFORMANCE-DEV3.md) for the search/rendering patch, display preference, icon fallback changes, build instructions and explicit measurement limits.
