# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Fotófono (`com.example.lightdetector`) is a single-Activity Android app that turns ambient light level into an audible beep, built as an accessibility tool for blind/low-vision users. It samples the back camera's luminance and maps it to a beep frequency/volume/interval, entirely on-device with no network access.

Accessibility is a product requirement, not polish: preserve TalkBack labels, `announceForAccessibility` calls, large touch targets, and non-color-only feedback in any change.

## Build & Validation Commands

This is a plain Gradle/AGP Android project (no Kotlin, no AndroidX, no Compose, no CameraX — intentionally minimal, framework-only Java). There is no test source set in the repo, so there are no unit/instrumented tests to run.

Windows (this environment's `local.properties` already points `sdk.dir` at the Windows SDK):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # JDK 21
.\gradlew.bat lintDebug assembleDebug
```

**Setting `JAVA_HOME` is required on this machine.** The default `java` on PATH is JDK 24 (`C:\Program Files\Java\jdk-24`), which Gradle 8.13 rejects with `Unsupported class file major version 68`. Android Studio's bundled JBR is JDK 21 and works. Anything in the 17–21 range is fine.

If working from a WSL/Linux shell instead, use the bundled helper, which temporarily repoints `local.properties` at a Linux SDK/JDK and restores it on exit:
```
skills/android-app-maintainer/scripts/build_local.sh
```
That script expects `ANDROID_SDK_ROOT` (default `/tmp/android-sdk`) to contain `platforms/android-36` and `build-tools/36.0.0`, and `JAVA_HOME` (default `/tmp/android-build-tools/jdk`) to be a JDK 17+. Windows build tools are not reliably runnable from WSL, which is why this indirection exists.

To inspect a built APK's manifest/permissions after a build:
```
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

Three classes in `app/src/main/java/com/example/lightdetector/`, plus one layout:

- **`MainActivity`** — owns everything: camera lifecycle (Camera2, not CameraX), permission handling, UI controls (`SeekBar`s for sensitivity/interval, exposure-lock checkbox), settings persistence, and edge-to-edge inset handling. It runs three concurrent execution contexts:
  - **Main thread** — UI updates, `SharedPreferences` I/O, permission callbacks.
  - **Camera `HandlerThread`** (`cameraThread`/`cameraHandler`) — Camera2 capture session, `onImageAvailable` luma sampling.
  - **Audio `HandlerThread`** owned by `BeepPlayer` — beep generation, isolated from the camera thread.
  Camera capture writes `latestLuma` (`volatile double`) and pushes it into `BeepPlayer.setInput(...)`; there's no shared mutable UI state touched off the main thread except through this one field and the `beepPlayer` handoff.
- **`LightSignalMapper`** — pure/stateless mapping from `(averageLuma, sensitivityPercent)` to a `LightSignal` (percent, frequency Hz, volume, Spanish label). This is the place to change the light→sound curve.
- **`LightSignal`** — plain immutable value holder returned by the mapper.
- **`BeepPlayer`** — runs its own `HandlerThread` at `THREAD_PRIORITY_AUDIO`, self-schedules its next beep (`postDelayed`) based on the latest interval, and synthesizes each beep as a raw sine wave written to a streaming `AudioTrack` (`USAGE_ASSISTANCE_ACCESSIBILITY` / `CONTENT_TYPE_SONIFICATION`). Reads of `latestLuma`/`sensitivityPercent`/`intervalMs` are guarded by a private lock, decoupling the audio cadence from the camera capture cadence.

Camera selection (`findBackCameraId`) prefers `LENS_FACING_BACK` and falls back to the first available camera — there is no front-camera path. Analysis resolution (`chooseAnalysisSize`) picks the largest available `YUV_420_888` size at or under 640×480 for lightweight luma sampling.

Settings (`sensitivityPercent`, `intervalMs`, `exposureLocked`) persist in `SharedPreferences("settings", MODE_PRIVATE)` and are excluded from Android auto-backup (see `res/xml/backup_rules.xml` / `data_extraction_rules.xml`).

## Project Conventions (from `skills/android-app-maintainer/SKILL.md`)

A project skill exists at [skills/android-app-maintainer/SKILL.md](skills/android-app-maintainer/SKILL.md) with agent-facing guidance; the durable parts:

- Target `compileSdk 36` / `targetSdk 36` and keep `buildToolsVersion "36.0.0"` unless deliberately upgrading the toolchain.
- Do not add AndroidX, Compose, CameraX, or Material dependencies for small changes — the app is deliberately plain Java + framework APIs.
- Keep interval bounds at `100..10000 ms` unless the user asks to change them.
- Handle system-bar insets rather than opting out of edge-to-edge; Android 16 (API 36) ignores many orientation/resizability assumptions on large screens.
- API-specific XML/theme attributes belong in resource-qualified folders (e.g. `values-v27/`), not inline SDK checks.
- More background (build environment quirks, manifest rationale, Android 16 notes) is in [skills/android-app-maintainer/references/project-android-notes.md](skills/android-app-maintainer/references/project-android-notes.md).
