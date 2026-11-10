# AccessText

A consent-based Android research prototype for a B.Tech final-year project (BTP) on online grooming detection.  
It performs **one manually armed, single-shot accessibility inspection** of a chosen messaging app —
reporting node statistics and, optionally, reconstructing the visible chat rows into ordered text-only records for local review.

> **Research instrument, not a monitor.** One deliberate press = one screen snapshot = one result.  
> No polling, no background collection, no network access, no persistence of extracted text.

---

## Table of Contents

1. [What it does](#what-it-does)
2. [What it deliberately does not do](#what-it-deliberately-does-not-do)
3. [Build status](#build-status)
4. [Requirements](#requirements)
5. [Getting started](#getting-started)
6. [Project layout](#project-layout)
7. [Running tests](#running-tests)
8. [Privacy and consent design](#privacy-and-consent-design)
9. [Known limitations](#known-limitations)
10. [Documentation](#documentation)
11. [Research ethics](#research-ethics)
12. [Licence](#licence)

---

## What it does

1. **Consent** — The researcher reads a disclosure and accepts it. Consent and the chosen target are the only two values kept in local preferences.
2. **Enable the accessibility service** — Must be done by a human in Android Settings. The app cannot and will not enable it programmatically.
3. **Arm one inspection** — Home's **Open Inspection Controls** button leads to the Experiment screen, where you choose one of two Start buttons: *node counts only*, or *node counts + message extraction*. The target app and mode are frozen at that moment.
4. **One deferred read** — The first qualifying event schedules a single `rootInActiveWindow` read **1.5 s later** so a transition frame can settle. An arm token rejects the read if cancelled or re-armed in the meantime.
5. **Structural pass** — Counts nodes, text-bearing nodes, character totals, widget classes, depth, truncation, and unreadable children. Node text is never stored.
6. **Optional extraction pass** — Runs over the same snapshot only if the extraction Start button was pressed. Classifies candidate message rows and rebuilds visible records; unsupported or incomplete reconstructions say so explicitly.
7. **Review and stop** — Results stay in memory for review. Cancelling or leaving review clears temporary content. The arm is consumed and never re-arms itself.

Outcomes are explicit: `COMPLETED`, `FAILED`, or `CANCELLED`. A null root is never reported as success.

---

## What it deliberately does not do

- No automatic initiation, timer loop, polling, scheduled scraping, or continuous monitoring.
- No scrolling, clicking, navigation, retry, or automatic history reading.
- No access to deleted, hidden, off-screen, or previously scrolled-away messages.
- No notifications, clipboard, keystrokes, screenshots, or screen recording.
- **No network access** — the manifest declares zero `<uses-permission>` entries, including no `INTERNET`.
- No persistence of node text or extracted content — no files, database, analytics, or export.
- No raw conversation text in Logcat, reports, crash messages, or compatibility reports.
- No attempt to bypass app restrictions, encrypted storage, databases, or hidden APIs.
- No fabricated model output, confidence score, or compatibility number.

---

## Build status

| Item | Result |
|------|--------|
| Build | `BUILD SUCCESSFUL` — APK ~12 MB (debug) |
| Unit tests | **160 passed, 0 failed, 0 errors, 0 skipped** (15 test classes) |
| Device tested | `Pixel_API35_Play` emulator (Android 15, x86_64) |
| Real-app runs | 3 completed Instagram runs on the emulator |
| Real-app accuracy | **NOT MEASURED** — no annotated ground truth exists |
| ML model | None connected — adapter returns *Model not integrated* |

Evidence including per-class test counts and archived build logs is in
[docs/12](docs/12_MESSAGE_LEVEL_EXTRACTION_IMPLEMENTATION_LOG.md) and
[docs/13](docs/13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md).

---

## Requirements

| Tool | Version |
|------|---------|
| Android | minSdk **24** (7.0+), compileSdk/targetSdk **36** |
| JDK | **17–21** (Android Studio bundled JDK 21 recommended) |
| Android SDK Platform | **36** |
| Gradle | **8.14.6** (wrapper included) |
| Android Gradle Plugin | **8.13.2** |
| Kotlin | **2.2.21** |
| Compose BOM | **2026.01.01** |

> Newer system JDKs are not supported by AGP 8.13.2. Use the JDK bundled with Android Studio.

---

## Getting started

### 1. Clone

```bash
git clone https://github.com/chptt/Accesstext.git
cd Accesstext
```

### 2. Configure the SDK path

Create `local.properties` at the repo root (this file is git-ignored):

```properties
sdk.dir=/path/to/your/Android/Sdk
```

On Windows (Android Studio default):

```properties
sdk.dir=C\:\\Users\\<YourName>\\AppData\\Local\\Android\\Sdk
```

### 3. Run unit tests

```bash
# Windows
gradlew.bat :app:testDebugUnitTest

# macOS / Linux
./gradlew :app:testDebugUnitTest
```

### 4. Build the debug APK

```bash
gradlew.bat :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### 5. Install and launch

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.groomingresearchdemo/.MainActivity
```

Play Protect may warn because the signing key is unknown — expected for a debug build.  
Use only on a device you own or are authorised to test.

### 6. Using the app (one inspection round)

1. Open the app, accept consent, and choose a target app from the allowlist.
2. Enable the accessibility service: **Settings → Accessibility → AccessText**.
3. Tap **Open Inspection Controls** on Home, then press one of the two Start buttons.
4. Switch to the target app, open a chat, and scroll or tap once so the screen changes.
5. The read fires 1.5 s after that first event. Return to the app to open the review screen.
6. Press **Cancel** before the read, or leave review afterwards, to clear temporary content.

**Allowlisted packages:** Google Messages · Instagram · Instagram Lite · WhatsApp · Telegram · Signal

---

## Project layout

```text
.
├── app/
│   ├── src/main/java/com/example/groomingresearchdemo/
│   │   ├── inspection/           # State machine, structural inspector, result model
│   │   │   └── extraction/       # Traverser, classifier, reconstructor, ladder, metrics
│   │   ├── service/              # AccessibilityService, node adapter, logging
│   │   ├── state/                # ResearchStore and UI state
│   │   ├── model/                # Preprocessing, synthetic samples, adapter placeholder
│   │   ├── report/               # Redacted compatibility report
│   │   └── ui/                   # Compose screens
│   ├── src/test/java/            # 15 JVM test classes — 160 tests total
│   └── src/main/res/xml/         # Accessibility-service config and package allowlist
├── docs/                         # Markdown docs 00–13, final report PDF + HTML
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/               # Gradle 8.14.6 wrapper
```

---

## Running tests

All 160 tests run entirely on the JVM — no emulator required:

```bash
gradlew.bat :app:testDebugUnitTest
```

**Coverage includes:**
- State machine: arm gates, cancel, wrong-package rejection, consume-once, null root, token-guarded settle read
- Structural inspector
- 14 mandated synthetic conversation fixtures (F1–F14)
- Extraction privacy marker assertions
- Classifier and reconstruction rules
- Capability ladder
- Redacted compatibility report

> Synthetic fixtures verify mechanics and privacy, not real-app accuracy.  
> Real-app metrics remain **NOT MEASURED** — no annotated real conversation exists.

---

## Privacy and consent design

- Consent is explicit and revocable; revocation clears pending state and local results.
- Every inspection requires a deliberate Start press, an enabled service, and a chosen target.
- The target is verified three ways: six-package allowlist, runtime `setServiceInfo()` narrowing, and arm-time package equality check.
- Node text is used only in memory to build review records; it is never logged, written, exported, or transmitted. Marker-string unit tests assert this.
- One reconstruction is held at a time, replaced on the next inspection, cleared on cancel, revoke, or leaving review.
- `allowBackup="false"` — only `consent_status` and `target_package` are persisted.

---

## Known limitations

- **Real-app accuracy is not measured.** Three Instagram runs prove the pipeline executes; they do not prove the records are correct.
- Text availability is per screen, not per app. A chat screen and a non-chat screen expose very different trees.
- Without a geometry baseline, ordering and message boundaries may be unsupported; the output says `INCOMPLETE`/`UNSUPPORTED` rather than guessing.
- Vendors customise accessibility trees; results from one app or device do not generalise.
- Only Android 15 emulator has been tested; Android 7–14 and physical devices are untested.
- The accessibility service must be enabled manually, which prevents automated end-to-end validation.

---

## Documentation

| # | Document |
|---|----------|
| 00 | [Project overview](docs/00_PROJECT_OVERVIEW.md) |
| 01 | [Environment setup](docs/01_ENVIRONMENT_SETUP.md) |
| 02 | [Android accessibility fundamentals](docs/02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md) |
| 03 | [Source code walkthrough](docs/03_SOURCE_CODE_WALKTHROUGH.md) |
| 04 | [Accessibility inspection flow](docs/04_ACCESSIBILITY_INSPECTION_FLOW.md) |
| 05 | [Text preprocessing and ML integration](docs/05_TEXT_PREPROCESSING_AND_ML_INTEGRATION.md) |
| 06 | [Testing and results](docs/06_TESTING_AND_RESULTS.md) |
| 07 | [BTP presentation guide](docs/07_BTP_PRESENTATION_GUIDE.md) |
| 08 | [Limitations and future work](docs/08_LIMITATIONS_AND_FUTURE_WORK.md) |
| 09 | [Development journal](docs/09_DEVELOPMENT_JOURNAL.md) |
| 10 | [Quick revision notes](docs/10_QUICK_REVISION_NOTES.md) |
| 11 | [Real-app compatibility report](docs/11_REAL_APP_COMPATIBILITY_REPORT.md) |
| 12 | [Message-level extraction implementation log](docs/12_MESSAGE_LEVEL_EXTRACTION_IMPLEMENTATION_LOG.md) |
| 13 | [Message reconstruction validation report](docs/13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md) |
| — | [Final report (PDF)](docs/PROJECT_FINAL_REPORT.pdf) · [HTML source](docs/PROJECT_FINAL_REPORT.html) |

---

## Research ethics

Use this only with informed consent from everyone whose messages appear on the tested device, and only where the research protocol permits it. It cannot start itself, cannot read history, and does not collect in the background. Do not adapt it into a covert monitoring tool.

---

## Licence

No licence file is included. This is a research prototype; add an explicit licence before redistribution or reuse.
