# 01 — Environment Setup

This document explains the development environment for **this specific machine and project**,
using the exact versions and commands that were actually used. Generic instructions that do not
match the generated configuration are deliberately avoided.

---

## 0. The environment that was detected here

| Component | Value | Why it matters |
|---|---|---|
| Workspace | `D:\AccessTest` | Project root |
| System Java | `27` | **Too new** for AGP 8.13 — not used for the build |
| Build JDK | Android Studio bundled `jbr` = OpenJDK **21.0.10** at `C:\Program Files\Android\Android Studio\jbr` | AGP needs JDK 17–21; 21 is the safe choice |
| Gradle | **8.14.6** (wrapper) | Compatible with AGP 8.13.2 and JDK 21 |
| Android Gradle Plugin | **8.13.2** | Supports `compileSdk 36` |
| Kotlin | **2.2.21** | Matches the Compose compiler plugin |
| Android SDK | `C:\Users\Chandana\AppData\Local\Android\Sdk` | platforms `android-36`, `android-36.1`; build-tools `35.0.0`, `36.0.0` |
| Emulator | present; AVD `Medium_Phone` = API **26**, x86, Google APIs, 1080×2400 | WHPX acceleration usable |

> **Key lesson:** the *system* JDK (27) is not the JDK you build with. Android builds are driven
> by a Gradle-managed JDK. Here we point Gradle at Android Studio's bundled JDK 21. If you let it
> use Java 27, AGP will reject it.

---

## 1. Install and configure Android Studio

1. **What:** Install Android Studio (already present here at `C:\Program Files\Android\Android Studio`).
2. **Why:** It bundles the SDK manager, emulator, and a compatible JDK.
3. **Success:** You can open a project and it resolves the SDK automatically.
4. **Error:** "SDK location not found" → create `local.properties` with `sdk.dir=…` (see §5).
5. **Fix:** File ▸ Settings ▸ Languages & Frameworks ▸ Android SDK, set the SDK root.

## 2. Understand the Android SDK

The SDK is the collection of: **platforms** (the Android OS versions you compile against, e.g.
`android-36`), **build-tools** (the compiler/linker/packager like `aapt2`, `d8`), **platform-tools**
(`adb`), and **system-images** (OS images the emulator runs). This project compiles against
platform 36 and packages with build-tools 36.0.0.

## 3. Understand the emulator

The emulator is a real Android device running inside your computer. It needs a **system image**
(an OS build) and a **hardware-acceleration** backend. Here acceleration is **WHPX** (Windows
Hypervisor Platform). We used the existing AVD `Medium_Phone` (API 26).

## 4. Create / configure a virtual device

Android Studio normally does this via Device Manager. To create one manually:
```
sdkmanager "system-images;android-26;google_apis;x86"
avdmanager create avd -n Medium_Phone -k "system-images;android-26;google_apis;x86"
```
**Why a specific API level?** The device you test on must be ≥ your `minSdk` (24 here). API 26
satisfies that.

## 5. Select an appropriate API level

- `compileSdk = 36` — the newest APIs available to build against.
- `targetSdk = 36` — declares the app is tested against modern behaviour rules.
- `minSdk = 24` — the oldest device that can install it; chosen so the API-26 emulator (and most
  real phones) can run it.

## 6. The JDK and Gradle

Gradle is the build tool; it reads `build.gradle.kts` files and runs the Android Gradle Plugin.
The plugin runs `javac`/`kotlinc`/`aapt2`. The JDK that runs Gradle must be compatible with both
Gradle and AGP. We use JDK 21.

Set it for command-line builds (PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```
In Android Studio this is automatic (it uses its own `jbr`).

## 7. Create the Kotlin project

This repository already contains the project. If recreating: New Project ▸ Empty Activity (Compose)
▸ Language Kotlin ▸ build.gradle.**kts**. Application ID: `com.example.groomingresearchdemo`.

## 8. Application ID vs package name

- **applicationId** (`com.example.groomingresearchdemo`) is the app's unique identity on the device
  and in Play.
- **namespace / package** in the source is also `com.example.groomingresearchdemo` here, so `R`
  and `BuildConfig` resolve under it. They can differ, but keeping them equal is simplest.

## 9. Run the app for the first time

With the emulator running:
```
adb devices                      # should list emulator-5554  device
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.groomingresearchdemo/.MainActivity
```
**Success:** Logcat shows `Displayed com.example.groomingresearchdemo/.MainActivity: +1s…` and the
Home dashboard appears. **That exact line appeared in this environment.**

## 10. Build a debug APK

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:assembleDebug
```
**Success:** `BUILD SUCCESSFUL` and the APK at `app\build\outputs\apk\debug\app-debug.apk`.

## 11. Locate the APK

`app/build/outputs/apk/debug/app-debug.apk` (12,032,138 bytes in the final clean build of the
compatibility version; earlier Instagram-only builds were ~11.98 MB).

## 12. Install onto the emulator

`adb install -r app\build\outputs\apk\debug\app-debug.apk` → `Success`.

## 13. Use Logcat

Filter to this app's tag (we only log non-sensitive structural data under it):
```
adb logcat -s GroomingResearchDemo
```
In Android Studio: Logcat panel ▸ search `tag:GroomingResearchDemo`.

## 14. Troubleshooting common errors

| Symptom | Likely cause | Fix |
|---|---|---|
| `Unsupported class file major version` / AGP rejects JDK | Building with JDK 27 | Point `JAVA_HOME` at JDK 21 (`jbr`) |
| `SDK location not found` | No `local.properties` | Add `sdk.dir=C\:\\Users\\Chandana\\AppData\\Local\\Android\\Sdk` |
| `Failed to resolve: com.android.application` | Offline / wrong repos | Ensure `google()` + `mavenCentral()` in `settings.gradle.kts` (already set) |
| `Unresolved reference: collectAsState` | Wrong import package | Use `androidx.compose.runtime.collectAsState` with an `initial` value (this exact bug was hit and fixed — see the journal) |
| Emulator slow / won't boot | No acceleration | `emulator -accel-check` should report WHPX usable |
| `adb: device offline` | Emulator still booting | `adb wait-for-device`, then poll `getprop sys.boot_completed` |

---

## Reproduce from scratch (copy-paste)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\AccessTest
.\gradlew :app:assembleDebug :app:testDebugUnitTest
# emulator already running as emulator-5554:
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.groomingresearchdemo/.MainActivity
adb logcat -s GroomingResearchDemo
```

Next: [02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md](02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md).
