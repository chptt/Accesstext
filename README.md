# Accesstext — Accessibility Inspection and Message-Level Extraction

A consent-based Android research prototype built for a B.Tech final-year project (BTP) on online
grooming detection. It performs **one manually armed, single-shot accessibility inspection** of a
chosen messaging app:

- a **structural pass** that reports node and text-availability statistics only, and
- an optional **message-level pass** that reconstructs the visible chat rows into ordered, text-only
  records for local review.

It is a research instrument, **not a monitor**: one press = one screen = one result. There is no
polling, scheduled scraping, history navigation, off-screen or deleted-content recovery, network
access, or persistence of extracted text.

---

## Status

| Item | Result |
|---|---|
| Build | `BUILD SUCCESSFUL`, APK 12,114,117 bytes (debug) |
| Unit tests | **160 passed, 0 failed, 0 errors, 0 skipped** (15 classes), JUnit XML checked |
| Device | Installed and run on the `Pixel_API35_Play` emulator (Android 15, x86_64) |
| Real-app extraction | Three completed Instagram runs on the emulator; **no ground truth**, so real-app accuracy is **NOT MEASURED** |
| ML model | None connected. The adapter returns *Model not integrated* and refuses uncertain records |

The 160-test figure and the three-run figures are from 2026-10-20/21. The full evidence, including
per-class counts and archived build logs, is in [docs/12](docs/12_MESSAGE_LEVEL_EXTRACTION_IMPLEMENTATION_LOG.md)
and [docs/13](docs/13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md).

**What is not claimed:** no accuracy, compatibility, or "messages extracted" claim is made for any
real app. Nothing here has been validated against a hand-transcribed real conversation, and no
physical-device run has been recorded yet.

---

## What it does

1. **Consent.** The researcher reads a disclosure and accepts it. Consent and the chosen target are
   the only two values kept in local preferences.
2. **Enable the accessibility service.** This must be done by a human in Android Settings. The app
   cannot and will not enable it programmatically.
3. **Arm one inspection.** The target app and the extraction opt-in are frozen at that moment.
   Nothing runs until a qualifying screen event arrives from that exact package.
4. **One deferred read.** The first qualifying event schedules a single `rootInActiveWindow` read
   **1.5 s later**, so a transition frame can settle. An arm token rejects the read if the
   researcher cancelled or re-armed in the meantime. No second read and no automatic re-arm.
5. **Structural pass.** Counts nodes, text-bearing nodes, character totals, widget classes, depth,
   truncation and unreadable children. It never stores node text.
6. **Optional extraction pass.** Runs over the *same* snapshot, only if the researcher ticked it when
   arming. It classifies candidate message rows and rebuilds visible records without inventing
   senders, timestamps or boundaries; unsupported or incomplete reconstructions say so.
7. **Review and stop.** Results stay in memory for review. Cancel or leaving review clears the
   temporary extracted content. The arm is used up and never re-arms itself.

Outcomes are explicit: `COMPLETED`, `FAILED` (for example, no active root), or `CANCELLED`.
A null root is never reported as a success.

## What it deliberately does not do

- No automatic initiation, timer loop, polling, scheduled scraping or continuous monitoring.
- No scrolling, clicking, navigation, retry, or automatic history reading.
- No access to deleted, hidden, off-screen or previously scrolled-away messages.
- No notifications, clipboard, keystrokes, screenshots or screen recording.
- No network access: the manifest declares **zero `<uses-permission>` entries**, including no
  `INTERNET`.
- No persistence of node text or extracted content: no files, database, analytics or export.
- No raw conversation text in Logcat, reports, crash messages or compatibility reports.
- No attempt to bypass app restrictions, encrypted storage, databases or hidden APIs.
- No fabricated model output, confidence score or compatibility number.

---

## Requirements

- Android **7.0+** (`minSdk 24`); `compileSdk`/`targetSdk` 36.
- JDK **17–21**. The build was made with Android Studio's bundled JDK 21; newer system JDKs are not
  supported by AGP 8.13.2.
- Android SDK Platform 36.
- Pinned toolchain: Gradle **8.14.6**, Android Gradle Plugin **8.13.2**, Kotlin **2.2.21**,
  Compose BOM **2026.01.01**.

## Build, test, install

```bash
# Point Gradle at a supported JDK (Windows example):
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Unit tests:
./gradlew.bat :app:testDebugUnitTest

# Debug APK:
./gradlew.bat :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device/emulator:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.groomingresearchdemo/.MainActivity
```

The APK is debug-signed and intentionally not committed. Play Protect may warn because the signing
key is unknown; a debug build is expected to be treated that way. Use only on a device you own or
are authorised to test.

## Trying it on a phone (one round)

1. Install the APK, open the app, accept consent, and choose the target app.
2. Enable the accessibility service in Settings → Accessibility. This is a human-only action.
3. Tick **message-level extraction** if you want records, then press **Start**.
4. Switch to the target app, open a chat, and scroll or tap once so the screen changes.
5. The one read happens 1.5 s after that first event. Return to the app and open the review screen.
6. Press **Cancel** before the read, or leave review afterwards, to clear temporary content.

The target must be one of the six allowlisted packages: Google Messages, Instagram, Instagram Lite,
WhatsApp, Telegram, or Signal. Messages that arrive later, or that have scrolled off-screen, are not
captured — one arm produces one snapshot.

---

## Project layout

```text
.
├─ app/src/main/java/com/example/groomingresearchdemo/
│  ├─ inspection/                 # state machine, structural inspector, result model
│  │  └─ extraction/              # traverser, classifier, reconstructor, ladder, metrics
│  ├─ service/                    # AccessibilityService, node adapter, logging
│  ├─ state/                      # ResearchStore and UI state
│  ├─ model/                      # preprocessing, synthetic samples, adapter placeholder
│  ├─ report/                     # redacted compatibility report
│  └─ ui/                         # Compose screens
├─ app/src/test/java/...          # 15 JVM test classes, 160 tests
├─ app/src/main/res/xml/          # accessibility-service configuration and allowlist
├─ docs/                          # documentation 00–13, final report, evidence
├─ build.gradle.kts, settings.gradle.kts, gradle.properties
└─ gradle/wrapper/                # Gradle 8.14.6 wrapper
```

## Tests

`app/src/test` runs entirely on the JVM; no emulator is required.

- 15 classes, **160 tests**, covering the state machine (arm gates, cancel, wrong package,
  consume-once, null root, token-guarded settle read), the structural inspector, the 14 mandated
  synthetic conversation fixtures (F1–F14), extraction privacy marker tests, classifier and
  reconstruction rules, the capability ladder, and the redacted report.
- Synthetic fixtures verify mechanics and privacy, not real-app accuracy. Real-app metrics remain
  **NOT MEASURED** because no annotated real conversation exists.

## Privacy and consent design

- Consent is explicit and revocable; revocation clears pending state and local results.
- Every inspection requires a deliberate Start press, an enabled service, and a chosen target.
- The target is checked three ways: the platform-level six-package allowlist, runtime narrowing with
  `setServiceInfo()`, and the arm-time package equality check.
- Node text is used only in memory to build review records and lengths; it is never logged,
  written, exported or transmitted. Marker-string unit tests assert this.
- One reconstruction is held at a time, replaced on the next inspection, and cleared on cancel,
  revoke, or leaving review.
- `allowBackup="false"`; only `consent_status` and `target_package` are persisted.

## Documentation

| # | Document |
|---|---|
| 00 | [Project overview](docs/00_PROJECT_OVERVIEW.md) |
| 01 | [Environment setup](docs/01_ENVIRONMENT_SETUP.md) |
| 02 | [Android accessibility fundamentals](docs/02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md) |
| 03 | [Source code walkthrough](docs/03_SOURCE_CODE_WALKTHROUGH.md) |
| 04 | [Inspection flow](docs/04_ACCESSIBILITY_INSPECTION_FLOW.md) |
| 05 | [Preprocessing and ML integration](docs/05_TEXT_PREPROCESSING_AND_ML_INTEGRATION.md) |
| 06 | [Testing and results](docs/06_TESTING_AND_RESULTS.md) |
| 07 | [Presentation guide](docs/07_BTP_PRESENTATION_GUIDE.md) |
| 08 | [Limitations and future work](docs/08_LIMITATIONS_AND_FUTURE_WORK.md) |
| 09 | [Development journal](docs/09_DEVELOPMENT_JOURNAL.md) |
| 10 | [Quick revision notes](docs/10_QUICK_REVISION_NOTES.md) |
| 11 | [Real-app compatibility report](docs/11_REAL_APP_COMPATIBILITY_REPORT.md) |
| 12 | [Message-level extraction implementation log](docs/12_MESSAGE_LEVEL_EXTRACTION_IMPLEMENTATION_LOG.md) |
| 13 | [Reconstruction validation report](docs/13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md) |
| — | [Final report (PDF)](docs/PROJECT_FINAL_REPORT.pdf) · [HTML source](docs/PROJECT_FINAL_REPORT.html) |

## Known limitations

- **Real-app accuracy is not measured.** Three Instagram runs prove the pipeline executes against a
  live third-party tree; they do not prove the records are correct.
- Text availability is **per screen**, not per app. A chat screen and a non-chat screen of the same
  app expose very different trees.
- Without a geometry baseline, ordering and message boundaries may be unsupported; the output says
  `INCOMPLETE`/`UNSUPPORTED` rather than guessing.
- Vendors customise accessibility trees; results from one app or one device do not generalise.
- Only an Android 15 emulator has been used; Android 7–14 and physical devices are untested.
- The accessibility service must be enabled manually, which prevents automated validation.

## Research ethics

Use this only with informed consent from everyone whose messages appear on the tested device, and
only where the research protocol permits it. It cannot start itself, cannot read history, and does
not collect in the background. Do not adapt it into a covert monitoring tool.

## Licence

No licence file is included. This is a research prototype; add an explicit licence before
redistribution or reuse.
