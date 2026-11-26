# Liquidy phone audit — 24–25 November 2026

## Scope and device

Moto G Stylus 5G (2023), Android 14, 1080×2400, density 2.5. Installed release: `io.github.janhurn9400.liquidy`, version `0.7.0-native-dev.3`, code 14.

This is an observed device audit, not a certification that every feature works. The final four batches completed successfully as shell processes; that does not make every UI assertion pass. Remaining coverage is listed explicitly below.

Evidence is local in `shots/device-audit/` (gitignored). Some captures contain personal device content and are not embedded here. No reports, messages, or issues were submitted.

## Findings

| ID | Priority | Finding and evidence | Reproduction / qualification |
|---|---|---|---|
| P1 | High | Poor release frame pacing. Initial existing counters: 1,571 frames, 506 janky (32.21%), median 32 ms, p95 57 ms, p99 150 ms. A subsequent mixed interaction window: 826 frames, 191 janky (23.12%), median 27 ms, p95 61 ms. | These are mixed-session samples, not isolated benchmarks or an FPS measurement. They substantiate poor frame pacing but do not identify a single cause. |
| P2 | Investigation | Android repeatedly logs `Method exceeds compiler instruction limit: 17577` for a generated `HomeScreen` method. | Captured in runtime logs during the release audit. Investigate splitting the large composable and profile compilation/execution. The warning alone does not prove the source of P1. |
| V1 | Medium | A sharp rectangular blue region is visible inside the rounded dock. | Present in `initial.png`, `final-home.png` and other release captures. Check wallpaper sampling, clipping, and glass coordinates. |
| V2 | Medium | Clear-style icons show squared patches near some upper corners. | Select Customize → Clear; see `clear-verified.png`. Confirm exact shader/mask cause separately. |
| N1 | Medium | Back from the wallpaper colour sheet exits to the previously open application instead of dismissing just the sheet. | Edit Wallpaper → Colour → Android Back. Observed return to BitChord; no matching Liquidy fatal exception was established. `wallpaper-colour.png` followed by `wallpaper-gradient.png` records the sequence. |
| F1 | Medium / missing behavior | Holding a dragged app at a folder's left edge does not switch folder pages. | Productivity last page → hold Calculator → move to left edge and hold five seconds. `folder-crosspage-held.png`. Source corroboration: `dragInFolder` only calculates insertion within its captured page; it has no edge-page controller. Home-page edge dragging works. |
| T1 | Low | Permissions footer says only weather sends data externally, while enabled web suggestions send typed queries to Google. | Compare `permissions.png` and `home-options.png`. Correct the contradictory copy. |
| T2 | Low / unavailable feature | Report a Problem advertises email reporting but provides no email action. | `report-page.png`; `Diagnostics.kt` retains a placeholder address and gates email reporting. The text should reflect the available actions until an address is configured. |
| R1 | Needs reproduction | Layout shown immediately after drops can disagree with the arrangement after entering page overview. | Seen during widget overflow/restoration sequences (`home-restored-grid.png`, `page-overview.png`, `overview-return.png`). Free-placement gaps and occupied-cell insertion complicate this sequence. Do not yet classify as saved-data loss. |
| R2 | Needs reproduction | Final sleep/wake batch captured wallpaper without Home content. | `batch4-final-home.png`. Must distinguish unlock animation timing, an empty Today page, and actual missing content. Earlier blank-screen claims were too strong: saved Today contents were already empty. |

## Coverage

“Observed” below means the specified action was exercised, not exhaustive coverage of that component.

| Area | Observed checks | Result / limits |
|---|---|---|
| Home | Paging, edit entry/exit, free placement, dragging across page edges, overflow | Working paths observed; placement/overview discrepancy R1 remains. |
| Dock | Existing four-app rendering and badges | Visual seam V1; dock reordering and capacity boundaries not comprehensively covered. |
| Folders | Open/close, paging, rename/clear/type/restore, reorder, create from two apps, drag out, add back | Working paths observed; cross-page folder drag F1. |
| Widgets | Existing weather and Android music stack, picker search, Batteries preview/add/resize/remove | Working paths observed; not all widget types, providers, configuration sheets, or stack operations were exercised. |
| Today | Navigate to existing empty Today | Empty state verified from saved layout; adding/reordering Today widgets remains unverified. |
| Search | Pill opening, typing, replacement, app filtering, unmatched query, web suggestions, calculator (`2+2 = 4`), contact results, Back, repeated opening/dismissal | Observed in initial work and batch 1. No calls or messages initiated. Files/photos opening, voice input, shortcut launch, and ranking behavior are not exhaustively covered. |
| App Library | Category grid, query/filter, no-match query, cancellation, context menu, scrolling | Observed in batch 2. Biometric hide/unhide, alphabetical-index gestures, and all category transitions remain unverified. |
| Icon appearance | Default, Dark, Clear, Tinted selections; restoration to original Tinted controls | Styles rendered; V2. Installed icon packs and every adaptive/legacy icon were not verified. |
| Wallpaper | Editor, Pair preview, Colour and Gradient chooser, Photo picker | Preview paths observed; N1. Applying a new wallpaper, photo crop/pinch, blur application, and glass calibration were not completed. Original wallpaper retained. |
| Settings | All seven main sections; Light theme and return to System; refresh-rate list; select 120 Hz and return to System | Batch 3 display dump reported active render rate 120.00001 Hz. This establishes display mode selection, not 120 FPS. Other rates/persistence/thermal behavior remain unverified. |
| Permissions | Permission status page; recording action's notification prompt and cancellation | Denial/regrant recovery for all permissions was not completed. |
| Reporting | Diagnostics page and Copy Diagnostics action; start recording action | Notification permission prompt reached and cancelled. Recording, stopping, attachment, GitHub handoff, and clipboard content were not fully validated. Nothing submitted. |
| Lifecycle | Fresh launch, app launch/return attempts, sleep/wake, restart | Batch 4; R2 needs reproduction. The activity dump did not provide enough evidence to certify every launch target. |
| Setup | Source inspection only | Full replay/completion was not run, because the welcome intent persists `done=false`. |
| Foldables/tablets | Source inventory only | Cannot verify Duo, folding, dual displays, and extended dock on this phone. |

## Final four-command run

`shots/device-audit/four_batches.py` executes exactly four ADB shell batches and saves their screenshots and textual logs:

1. Search/calculator/filtering/keyboard interruption.
2. App Library filtering/context/scrolling.
3. Settings appearance, display mode, diagnostics, recording permission cancellation.
4. Folder paging, launch/return attempts, sleep/wake, fresh launch.

All four exited with code 0 and produced 19 screenshots in total. Exit status is command execution evidence, not UI correctness. Batch 1 restarts the process before its final gfxinfo capture, so those final counters are startup counters and must not be presented as a search benchmark.

## Restoration and build separation

The original installed APK was saved as `shots/device-audit/original-installed.apk`, SHA-256 `3fbf9e8d5bb0d4f7270bcbf993434d0d835651942a4a6b2de92992f59c9eccb4`.

A matching locally signed debug build was temporarily installed to access private preferences. No production source fixes were made. Debug performance is excluded from release conclusions. Private preferences were backed up; the original single-page positions were reconstructed against the initial screenshot, the temporary folder was removed, and Calculator was restored to index 3 in Productivity. Widget configurations and existing folders were retained. The music widget was already a stack with weather, which its initial visible surface did not reveal.

The saved release APK was reinstalled successfully before the final four batches. Version code/name and absence of the debuggable flag were checked. Original settings appearance and refresh-rate preference were returned to System. The temporary ten-minute screen timeout was restored to 60 seconds. Weather/time/notification content naturally changed during testing.

After the blank post-sleep capture, a corrective restart and eight-second settling period produced `final-restored-settled.png`. It was visually checked against the initial screenshot: original widget, folder, app, and dock positions are restored. The first post-sleep capture remains R2; successful recovery does not resolve that observation.

## Remaining coverage after the first four batches

The table above is the remaining-work list. In particular, full setup, permission recovery, every widget configuration/provider, complete stack editing, biometric hiding, recording handoff, wallpaper application, sustained 60/90/120 Hz profiling, and foldable behavior are not complete. No claim of testing everything is made.

## Expanded single-command run — completed 25 November

The later `python shots/device-audit/complete_audit.py` run extends the earlier coverage. Its machine-readable record is `shots/device-audit/remaining-results.json`. It executed 28 non-destructive scenarios plus restoration; no scenario was logged with status ERROR. An operation being attempted is distinguished from a visually verified outcome below.

### Additional verified coverage

- Setup replay progressed through the applicable phone steps and returned to Home (`complete-setup-0.png` through `complete-setup-6.png`). Default-Home and foldable steps are conditional; this is not coverage of their alternative branches.
- All six built-in widget types were injected at 2×2, 4×2 and 4×4 sizes: Clock, Calendar, Batteries, Weather, Photos and Reminders. All 18 rendered in the captured Home layouts, reviewed together in `widget-matrix.png`. These are debug-hook size cases, not proof that every injected size is offered by the picker.
- Configuration entry was exercised for the cases; Clock city, Calendar calendars and Photos album screens were visually reviewed. Selecting every possible city, calendar or album, and long-term data updates, remain outside this run.
- Contact, Calendar, approximate Location and Photos permissions were individually revoked for denial-path captures and restored to their original granted states. Contact search displayed its request-access affordance after revocation (`complete-denied-READ_CONTACTS.png`). The other captures exercise Search with the permission unavailable; they do not certify every dependent widget or system-dialog branch.
- Stack swipe/context and the debug merge hook were attempted and captured. The hook requires two eligible non-stack small widgets, so this is not a certified new-stack creation test: the original music widget was already a stack.
- Lower Appearance settings were captured. Every installed third-party widget provider and icon pack was not exercised.

### Release performance samples

For each requested display rate the runner reset gfxinfo after returning from Settings, performed 12 injected search opening/dismissal cycles, and captured counters before restarting the process. The display dump confirmed the requested active render rate in each sample.

| Actual display rate | Frames | Android janky frames | Median frame time | p95 | p99 |
|---|---:|---:|---:|---:|---:|
| 60 Hz | 865 | 60 (6.94%) | 24 ms | 48 ms | 57 ms |
| 90 Hz | 1,404 | 102 (7.26%) | 23 ms | 38 ms | 53 ms |
| 120 Hz | 1,564 | 95 (6.07%) | 19 ms | 34 ms | 53 ms |

Raw evidence is `complete-release-{60,90,120}hz-gfxinfo.txt` and the corresponding display dumps. These are short serial samples, not a controlled randomized comparison or sustained thermal/power test. Different frame counts, cache warming, keyboard timing and Android scheduling limit comparisons between rates. Frame-duration percentiles are not FPS. Android's jank classification does not imply the other frames meet an ideal 8.33 ms budget at 120 Hz.

At 60 Hz, GPU median time was 6 ms while total median frame time was 24 ms. This supports profiling CPU/UI-thread work and scheduling in addition to shaders; it does not isolate a cause. The compiler-limit warning P2 remains a lead, not a demonstrated causal diagnosis.

### Final restoration verified

The runner restored the saved preference archive, installed the exact saved original release APK, restored the screen timeout to 60 seconds, restarted, waited for settling, and saved `complete-final-restored.png`. That screenshot was visually checked against the original layout: Weather at the top, the music stack on the left, the five original folders and individual apps in their original Home positions, and the original four dock apps. Calculator's original fourth position in Productivity was reconstructed before this backup. No temporary test widget or temporary folder remains. Network/weather values and notifications naturally changed.

### Explicit limits

This completed the single-command automated run, not every possible behavior of the app. Fingerprint authentication and foldable/dual-display behavior still require user input or different hardware. Actual uninstall completion and public report submission were deliberately not executed. Recording beyond its permission prompt, applying/cropping a new system wallpaper, every third-party widget/provider, complete stack editing/Smart Rotate, long-duration refresh-rate profiling, and all configuration-selection branches remain unverified. They must not be represented as passing tests.


## First fixes after the audit

- Widget gallery and detail sheets follow the Settings Light/Dark/System preference. Search text, labels, grabbers, separators, close glyphs and page indicators adapt together.
- Hello animation and subsequent setup pages have dark backgrounds, readable text and matching system bar icons. The glass writing uses a stronger white tint in dark appearance.
- Widget gallery respects safe drawing/keyboard insets; detail action and page indicators use the available height. Long app titles reserve room for Close.
- Customization sheet anchors above the navigation area on phones as well as Duo layouts. Context menus clamp to safe vertical bounds and available width. Shared glass sheets reserve navigation space.
- Back dismisses the widget detail page, shared glass sheets and wallpaper colour sheet before reaching the parent screen.

Validation: `:app:testDebugUnitTest :app:assembleRelease --offline` succeeded; 23 existing unit tests passed, zero failures/errors. Installed the release APK as an update on the connected Moto. Checked widget list in light and dark appearance, dark widget detail, detail Back, customization spacing, dark Hello/setup pages, and wallpaper colour Back returning to the editor. Canceled wallpaper preview and restored system night mode to Yes. No icons/widgets were added, moved or removed during these checks.

This is the first UI fix batch. The performance findings and other outstanding audit items above remain open. Padding across every menu, other display sizes, landscape and large font settings has not been fully validated. Device screenshots are under `shots/fix-*.png`; final build output is `shots/fix-final-validation.log`.


### Settings padding follow-up

Fixed the missing top safe-drawing inset in Launcher Settings. Phone category pages now reserve a 56 dp navigation row for Back and the centered title instead of floating Back above scroll content. Main-list top spacing is 24 dp; detail and nested-page top spacing is 16 dp, and General no longer adds an extra 46 dp gap. Nested pages retain their own Back navigation without a second floating category Back button.

Release assembly and release lint passed (`shots/settings-padding-build.log`). Installed as an update and navigated all seven settings categories, Report a Problem, and Icon Pack on the Moto. Reviewed main, General and Appearance screenshots, including a stable capture after navigation. No settings values or home-screen items were changed. Screenshots: `shots/settings-*-after.png`, `shots/settings-appearance-stable.png`. Tablet/foldable layouts were not checked on a device.


### Wallpaper jump between Home, Library and Today

Reproduced on the Moto: Home exposed Android wallpaper, but side-page blur mounted a separate bitmap. With compositor blur disabled (`mBlurEnabled=false`), that bitmap used a different crop and wallpaper landmarks visibly jumped at the handoff. The HomeScreen fallback now keeps the same bitmap mounted at zero blur and during side-page blur. Page position controls blur only, rather than also switching background sources. Devices with compositor blur continue using Android wallpaper and compositor blur. The system wallpaper itself was not replaced by this change.

Release build and release lint passed (`shots/wallpaper-motion-build.log`); installed as an update. Repeated Home -> Library -> Home -> Today -> Home three times. Compared wallpaper landmarks in before/after captures: the fallback crop now stays fixed across the three pages. Evidence: `shots/wp-comparison.png`, `shots/wp-fixed-comparison.png`, individual `wp-fixed-*.png` frames. Home-screen items were not edited. The fallback now uses its existing bitmap for sharp Home as well, so its crop can differ from the previous Android-rendered Home crop. Other vendors and live wallpapers were not validated.


### Folder rendering patch (two-ADB-command limit)

Used exactly two ADB invocations: one scripted Home-folder/Library-category interaction sample with gfxinfo, and one logcat collection/return command. The sampled counters recorded 603 frames, 155 janky frames (25.70%), p50 26 ms, p95 73 ms; these describe the sampled sequence, not an isolated benchmark for either folder type.

Patched Home folder opening to share one quarter-resolution GPU readback between background and panel instead of two full-screen readbacks. Blur runs on the reduced source off the UI thread. Background blur levels remain small and are scaled during drawing, avoiding full-screen allocation/upload for both levels. Panel samples reuse the capture; matching panel/background blur radii reuse computation. Snapshot state is keyed to the folder, wallpaper and screen size.

Library categories now crossfade cached wallpaper blur and blur only the fading Library layer. They no longer trigger whole-Home backdrop capture/category blur. Flying cluster icons use layer translation rather than animated layout offsets, with an explicit motion source so glass sampling follows their transforms.

Only local release assembly was used for validation after patching. No further ADB calls, installation, or on-device comparison were performed, respecting the command limit. Build log: `shots/folder-performance-final-build.log`. Before-sample artifacts: `shots/folder-lag-gfx.txt` and `shots/folder-lag-logcat.txt`. Visual fidelity and performance improvement still require a later device check.
