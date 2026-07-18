---
name: android-app-maintainer
description: Maintain, update, debug, or extend Android apps in this project. Use when Codex needs to modify Gradle/manifest/resources/Java/Kotlin code, camera/audio/accessibility behavior, Android 16/API 36 compatibility, Pixel/device behavior, build/lint issues, APK generation, or future changes to the local light detector app.
---

# Android App Maintainer

## Core Workflow

1. Start by inspecting the repo shape with `rg --files`, then read `settings.gradle`, root/module `build.gradle`, `AndroidManifest.xml`, and the files directly related to the request.
2. Read `references/project-android-notes.md` when working in this light detector app or when build environment details matter.
3. For facts that can change, such as Android 16 behavior, SDK/tool versions, Play policy, CameraX/Camera2 guidance, or Pixel quirks, verify against current official Android documentation before relying on memory.
4. Keep changes idiomatic for Android: minimal manifest permissions, explicit `<uses-feature>` declarations, string resources for visible text, resource qualifiers for API-specific XML, and lifecycle-safe camera/audio cleanup.
5. Treat accessibility as a primary requirement. Preserve large touch targets, TalkBack labels, accessible announcements for state changes, high contrast, and non-visual feedback.
6. Run validation before final response. Prefer `lintDebug assembleDebug`; use `scripts/build_local.sh` when working from this WSL project environment.

## Project Defaults

- Target Android 16 with `compileSdk 36` and `targetSdk 36` unless the user explicitly asks otherwise.
- Keep `buildToolsVersion "36.0.0"` for this project unless upgrading the Android Gradle Plugin/toolchain deliberately.
- The app is intentionally simple Java + Android framework APIs. Do not add AndroidX, Compose, CameraX, or Material dependencies for small changes unless they solve a real problem.
- Camera behavior should prefer the back camera, but avoid unnecessary Google Play filtering by declaring `android.hardware.camera` as not required when fallback behavior exists.
- Beep behavior should remain responsive to live light readings and user settings. Keep interval bounds at `100..10000 ms` unless the user changes the requirement.

## Accessibility Checklist

- Use `contentDescription` or `labelFor` for controls that are not self-explanatory to TalkBack.
- Keep interactive controls at least `48dp x 48dp`; this app uses larger controls where practical.
- Announce important state changes such as camera active, paused, permission denied, and setting changes.
- Route beep audio as accessibility/sonification audio where API support exists.
- Do not rely on color alone to convey light level; keep text and audio feedback in sync.

## Android 16 Checklist

- Do not opt out of edge-to-edge. Handle system bars/insets instead.
- Avoid fixed orientation or resizability assumptions; Android 16 ignores many restrictions on large screens.
- Keep predictive-back behavior in mind if adding custom back handling.
- Re-run lint after XML/theme changes; API-specific attributes often belong in `values-vNN`.

## Validation

Use the bundled script from the project root when possible:

```bash
skills/android-app-maintainer/scripts/build_local.sh
```

For normal Android Studio or native Linux SDK setups, this is usually enough:

```bash
./gradlew lintDebug assembleDebug
```

After building, inspect the APK if target SDK or manifest features matter:

```bash
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
```
