# Android Project Notes

## Current App

- Project root: `/mnt/d/repos/android_test`
- App module: `app`
- Package/application id: `com.example.lightdetector`
- App purpose: accessible light detector for blind users.
- Main code paths:
  - `app/src/main/java/com/example/lightdetector/MainActivity.java`
  - `app/src/main/java/com/example/lightdetector/BeepPlayer.java`
  - `app/src/main/java/com/example/lightdetector/LightSignalMapper.java`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/AndroidManifest.xml`

## Implementation Decisions From Initial Build

- Camera uses framework Camera2, not CameraX, to avoid adding dependencies in this small app.
- `findBackCameraId()` selects `LENS_FACING_BACK` first and falls back to the first available camera.
- Light detection samples the Y plane from `YUV_420_888` images and maps luminance through sensitivity gain.
- Beep generation uses `AudioTrack` with `USAGE_ASSISTANCE_ACCESSIBILITY` and `CONTENT_TYPE_SONIFICATION`.
- Settings are stored in `SharedPreferences`: `sensitivityPercent` and `intervalMs`.
- UI is one Activity with framework Views; controls are large and TalkBack-oriented.

## Build And Tooling Lessons

- Android 16 is API 36. The temporary Linux SDK used during the first build had:
  - `/tmp/android-sdk/platforms/android-36/android.jar`
  - `/tmp/android-sdk/build-tools/36.0.0/aapt2`
- The machine also has a Windows SDK at `C:\Users\alfre\AppData\Local\Android\Sdk`, but WSL cannot execute Windows build tools reliably in this environment.
- Java 8 is too old for modern Android Gradle Plugin. The first successful build used a temporary JDK 17 at `/tmp/android-build-tools/jdk`.
- If compiling from WSL, temporarily point `local.properties` at `/tmp/android-sdk`, run Gradle, then restore `local.properties` to the Windows SDK path.
- The build passed with:
  - `lintDebug`
  - `assembleDebug`
- Remaining lint warning after cleanup was only that Gradle 8.14.4 exists while the wrapper uses 8.13.

## Manifest Notes

- Keep `CAMERA` permission.
- Keep explicit camera feature declarations:
  - `android.hardware.camera.any` required, because the app needs some camera.
  - `android.hardware.camera` not required, because the app can fall back if a back camera is absent.
- Backup rules were added to satisfy modern Android lint guidance while keeping settings out of backup.

## Android 16 Notes

- Android 16/API 36 enforces edge-to-edge expectations for target 36 apps; this app handles insets rather than opting out.
- Avoid adding orientation locks. Android 16 ignores many orientation/resizability constraints on large screens.
- If adding custom back navigation, use current predictive-back APIs or explicitly document the compatibility choice.

## Accessibility Notes

- Preserve large controls and text sizes.
- Keep visible labels and TalkBack content descriptions synchronized.
- The user is blind; audio feedback and accessible state announcements are product requirements, not optional polish.
- When adding controls, ensure they can be operated with TalkBack and do not require visual camera preview interpretation.
