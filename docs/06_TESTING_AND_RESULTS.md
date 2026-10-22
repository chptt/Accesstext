# 06 — Testing and Results

The test strategy and the **actual** results obtained in this environment. Tests that ran are
marked with real evidence; tests that could not run here are marked honestly.

---

## 1. Test categories

- **Unit tests (JVM):** the pure `inspection/`, `model/` and `report/` code — run with
  `:app:testDebugUnitTest`.
- **State-management tests:** `InspectionControllerTest` (one-shot machine + consent, service and
  target gates).
- **Structural-inspection tests:** `StructuralInspectorTest` (counts, depth, widget classes, text
  availability, unreadable subtrees).
- **Redaction tests:** `CompatibilityReportTest` (node text must never reach a report).
- **Preprocessing tests:** `ConversationPreprocessorTest`, `AuthorizedTextParserTest`.
- **Model-placeholder test:** `PlaceholderGroomingModelAdapterTest`.
- **Accessibility-service manual tests:** require enabling the service in Settings on a device.
- **Real-app compatibility tests:** the RC series in
  [11_REAL_APP_COMPATIBILITY_REPORT.md](11_REAL_APP_COMPATIBILITY_REPORT.md) — **pending**.
- **Emulator smoke tests:** install + launch + navigate + persistence (run here).
- **Build verification:** APK produced and inspected (run here).

## 2. Overall unit-test result (real)

```
57 tests, 0 failures, 0 errors, 0 skipped
  InspectionControllerTest ............ 15 passed
  StructuralInspectorTest ............. 13 passed
  ConversationPreprocessorTest ........ 13 passed
  AuthorizedTextParserTest ............  9 passed
  CompatibilityReportTest .............  4 passed
  PlaceholderGroomingModelAdapterTest .. 3 passed
```
Command: `.\gradlew :app:testDebugUnitTest` → `BUILD SUCCESSFUL`.
Reports: `app/build/test-results/testDebugUnitTest/*.xml`.

## 3. Spec test items → concrete tests

| Test ID | Objective | Steps (automated) | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|---|
| T01 | Consent declined blocks arming | `arm(consentAccepted=false,…)` | `CONSENT_REQUIRED`, NOT_ARMED | matches | **Pass** | `armDeniedWithoutConsent` |
| T02 | Consent revoked clears arm+results | run then `revokeAndClear()` | NOT_ARMED, result null, count 0 | matches | **Pass** | `consentRevocationClearsArmAndResults` |
| T03 | Cancel before event | `arm` then `cancel()` | CANCELLED, eventReceived=false | matches | **Pass** | `cancelBeforeEventRecordsCancelledOutcome` |
| T04 | Wrong package ignored | `handleEvent("com.whatever")` | null, stays ARMED | matches | **Pass** | `wrongPackageEventIsIgnoredAndStaysArmed` |
| T05 | Null root | `handleEvent(target){ null }` | FAILED, rootAvailable=false, stats null | matches | **Pass** | `nullRootReportsFailedDistinctly` |
| T06 | Empty tree | inspect single node | visited=1, text=0, depth=1 | matches | **Pass** | `singleEmptyNodeTree` |
| T07 | No text-bearing nodes | inspect no-text tree | textBearing=0 | matches | **Pass** | `treeWithNoTextBearingNodes` |
| T08 | Several text-bearing nodes | inspect mixed tree | visited=4, text=3 | matches | **Pass** | `treeWithSeveralTextBearingNodes` |
| T09 | Max depth | inspect chain(4) | depth=4 | matches | **Pass** | `maximumDepthIsMeasuredFromRoot` |
| T10 | Whitespace normalization | `normalizeWhitespace` | collapses+trims | matches | **Pass** | `whitespaceNormalizationCollapsesAndTrims` |
| T11 | Empty message removal | `cleanMessages` | blanks dropped | matches | **Pass** | `blankMessagesAreRemoved` |
| T12 | Repeated legit messages kept | `cleanMessages` | 3 "ok" survive | matches | **Pass** | `legitimateRepeatedMessagesArePreserved` |
| T13 | Malayalam + emoji preserved | `normalizeWhitespace`/`toModelInput` | unchanged | matches | **Pass** | `malayalamAndEmojiArePreservedExactly` |
| T14 | Chronological order preserved | `preserveMessageOrder` | sorted by ts | matches | **Pass** | `chronologicalOrderIsRepairedWhenAllTimestampsPresent` |
| T15 | Missing speaker handling | `toModelInput` | unlabeled line | matches | **Pass** | `missingSpeakerProducesUnlabeledLine` |
| T16 | Missing timestamp handling | `cleanMessages` | ts stays null | matches | **Pass** | `missingTimestampIsNotFabricated` |
| T17 | Deterministic model input | run twice | equal | matches | **Pass** | `modelInputGenerationIsDeterministic` |
| T18 | Placeholder says not integrated | `predict` | NotIntegrated | matches | **Pass** | `reportsModelNotIntegrated` |

Extra safety tests (not required by the spec but present): `traversalRespectsNodeCapAndFlagsTruncation`,
`cannotDoubleArm`, `consumedArmDoesNotRefireOnNextEvent`, `forceDisarmPreventsStrayArmsOnRestart`,
`allSamplesValidateCleanly`, `lineCountMatchesCleanedCount`.

### 3b. Tests added for the real-app compatibility update

| Test ID | Objective | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|
| T19 | Arming refused when no app under test is selected | `TARGET_REQUIRED`, stays NOT_ARMED | matches | **Pass** | `armDeniedWithoutATargetApp` |
| T20 | Target captured at arm time; a later selection change cannot satisfy the arm | other package ignored, armed package consumed | matches | **Pass** | `targetIsCapturedAtArmTimeAndIgnoresLaterSelectionChange` |
| T21 | A non-target event never opens the accessibility tree | root provider never invoked | matches | **Pass** | `wrongPackageEventIsIgnoredAndStaysArmed` |
| T22 | Widget-class histogram counts each class | `TextView: 2`, RecyclerView: 1 | matches | **Pass** | `nodeClassHistogramCountsEachClass` |
| T23 | A node with no class name is still counted | recorded as `<unknown-class>` | matches | **Pass** | `missingClassNameIsRecordedRatherThanDropped` |
| T24 | Text and contentDescription counted separately | 1 text, 1 label-only, 4 chars | matches | **Pass** | `textAndContentDescriptionAreCountedSeparately` |
| T25 | Text-availability percentage derived correctly | 2 of 5 nodes → 40% | matches | **Pass** | `textAvailabilityPercentageIsDerivedFromVisitedNodes` |
| T26 | A subtree that refuses enumeration is counted and noted | `unreadableChildNodes=1` + note | matches | **Pass** | `unreadableChildrenAreCountedAndNoted` |
| T27 | A tree exposing no labels says so explicitly | note "No node in the visited tree…" | matches | **Pass** | `aTreeWithNoExposedTextSaysSoInItsNotes` |
| T28 | **Node text never appears in a compatibility report** | marker string absent from rendered report | matches | **Pass** | `nodeTextNeverAppearsInTheReport` |
| T29 | A failed attempt is reported as FAILED, not as an empty success | `Outcome: FAILED`, "No tree was traversed" | matches | **Pass** | `failedInspectionIsRecordedAsAFailureNotAnEmptySuccess` |
| T30 | Unknown install state prints Unknown, not Yes | `Installed on device: Unknown` | matches | **Pass** | `unknownInstallationStateIsPrintedAsUnknown` |
| T31 | Authorized import splits `Speaker: body` and never invents a timestamp | speaker set, `timestampMillis=null` | matches | **Pass** | `speakerPrefixIsSplitOnTheFirstColonSpace`, `timestampsAreNeverInvented` |
| T32 | Authorized import: blank lines survive parsing, dropped by cleaning | 4 parsed → 2 cleaned | matches | **Pass** | `blankLinesSurviveParsingAndAreDroppedByCleaning` |
| T33 | Malayalam + emoji survive the import path unchanged | output equals input | matches | **Pass** | `unicodeAndEmojiSurviveTheWholeImportPath` |

## 4. Build verification (real)

| Field | Value |
|---|---|
| Command | `.\gradlew --offline :app:clean` then `.\gradlew --offline :app:assembleDebug :app:testDebugUnitTest` |
| Result | `BUILD SUCCESSFUL` (tests re-executed with `--rerun`, see the note below) |
| APK path | `app/build/outputs/apk/debug/app-debug.apk` |
| APK size | 12,032,138 bytes |
| APK contents | `AndroidManifest.xml`, `resources.arsc`, `classes.dex`…`classes10.dex` |
| applicationId | `com.example.groomingresearchdemo` (from `output-metadata.json`) |
| minSdkForDexing | 24 |

**Verification note (worth mentioning in a viva):** that clean run reported `:app:testDebugUnitTest
FROM-CACHE`, meaning Gradle restored the earlier results instead of re-executing them. The tests were
therefore re-run with `--rerun` (`:app:testDebugUnitTest` shown as executed, `BUILD SUCCESSFUL`) before
any count was quoted here. Test totals in §2 and §3 come from the per-class `tests/failures/errors/
skipped` attributes in `app/build/test-results/testDebugUnitTest/*.xml`, not from memory.

## 5. Emulator smoke tests (real, executed on `Medium_Phone` API 26)

| Test ID | Objective | Steps | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|---|
| E01 | Install | `adb install -r app-debug.apk` | Success | `Success` | **Pass** | terminal |
| E02 | Launch | `am start …/.MainActivity` | activity displayed, no crash | `Displayed …MainActivity: +1s5xx ms`, no FATAL | **Pass** | logcat |
| E03 | UI renders | screencap | Home dashboard visible | rendered (see `evidence/smoke_home.png`) | **Pass** | screenshot |
| E04 | No fake success | observe status | service shows unverified, Start disabled | matches (see screenshot) | **Pass** | screenshot |
| E05 | Navigate to consent | tap Review Consent | consent screen | reached | **Pass** | uiautomator dump |
| E06 | Accept consent | tap Accept | `Consent ACCEPTED.` | logged | **Pass** | logcat |
| E07 | Consent persists | force-stop + relaunch | init logs `consent=ACCEPTED` | logged | **Pass** | logcat |
| E08 | Updated build installs and launches | `adb install -r` after the compatibility changes | Success, no FATAL | `Success`; `Displayed …MainActivity` | **Pass** | terminal, logcat |
| E09 | New status rows render | screencap of home dashboard | target, armed-for and reports-held rows visible; Start still disabled while service unverified | matches (`evidence/emulator_open_now.png`, and `evidence/emulator_compatibility_build.png` from the final clean build) | **Pass** | screenshot |
| E10 | Target is reported at init | read logcat | `ResearchStore initialised. consent=ACCEPTED target=com.instagram.android serviceEnabled=false` | logged | **Pass** | logcat |
| E11 | Final clean artefact re-verified | reinstall the 12,032,110-byte APK, relaunch, screencap | dashboard renders the 7 status rows; service still `Not enabled / unverified` | matches (`evidence/emulator_compatibility_build.png`, logcat at 00:05:47) | **Pass** | screenshot, logcat |
| E12 | Allowlist widened for Instagram Lite | clean build, `adb install -r` the 12,032,138-byte APK on API 35, relaunch, screencap | install `Success`; `topResumedActivity` = our `MainActivity`; dashboard renders; Consent still `Accepted` (data partition survived reboot); service still `Not enabled / unverified`; **Compatibility target still reads `com.instagram.android`, which is not installed on this device** | matches (`evidence/api35_lite_target.png`, 228,963 bytes, 14:46) | **Pass** | terminal, screenshot |

**Scope note on E12:** widening the allowlist was verified at build level — the merged manifest
contains `com.instagram.lite` — and the app still launches cleanly with it. The **Instagram Lite chip
itself was not confirmed on-screen**, because that needs the researcher to open *Choose app under
test*; the screenshot instead exposed the real risk (a persisted target that no longer matches the
device). The first capture of this run was the 35,248-byte Android splash window, not the UI, and was
discarded rather than reported as a render.

## 6. Manual tests NOT run here (documented, not fabricated)

| Test ID | Objective | Why not run | Status |
|---|---|---|---|
| M01 | Enable service in Settings, arm inspection, switch to the app under test, observe structural counts | Enabling an AccessibilityService is a deliberate human action; the researcher must do it | **Pass** — performed by hand by the researcher on 2026-10-20; `settings get secure enabled_accessibility_services` lists our service, logcat shows `CONNECTED` then `startInspection -> ARMED` |
| M02 | Real structural statistics from a messaging app | Follows from M01 | **Pass (8 observations)** — R1–R8 in `docs/11` §6. Conversation screens: Lite 16 text nodes / 205 chars, Instagram 18 / 245. R1 on a non-chat screen: 0 / 0. Messages rows measured but screens unlabelled |
| M03 | Behaviour across app versions | Needs multiple installs | **Pass (3 apps)** — Lite 533.0.0.9.105, Instagram 452.0.0.48.64, Messages messages.android_20240528 |
| RC-01…RC-05 | Real-app compatibility observations (see [11_REAL_APP_COMPATIBILITY_REPORT.md](11_REAL_APP_COMPATIBILITY_REPORT.md) §6) | Needs the manual service toggle | **RC-03, RC-05 Pass; RC-01/RC-02 run but unlabelled; RC-04 Pending** |

An attempt to execute M01/RC-01 automatically via
`adb shell settings put secure enabled_accessibility_services …` was **rejected by the safety
policy in this environment — correctly**. That command grants an accessibility permission
programmatically, which is precisely the safeguard this design refuses to bend; a run that
side-stepped the consent gate would not be measuring the consented system. So the on-device
compatibility numbers stay pending rather than being filled in by any other means.

> The **logic** these tests exercise (arm → event → package check → root → traverse → report) is
> covered by the unit tests above, including target capture, wrong-package isolation, null root,
> unreadable subtrees and report redaction. What unit tests cannot establish is how a specific
> real app behaves — that is what M01–M03 and RC-01–RC-05 exist for.

## 7. How to interpret the numbers

- **nodesVisited** — how many accessibility nodes existed in the active tree at that instant.
- **textBearingNodes** — how many exposed non-blank text/contentDescription. A measure of *exposure*,
  not of messages.
- **nodesWithText vs nodesWithContentDescriptionOnly** — the split matters: a `contentDescription`
  is an author-written label on a widget (often an icon), which is a different kind of signal from
  rendered text.
- **totalTextCharacters** — the sum of text **lengths**. It says how much label volume the tree
  exposes; it says nothing about what any of it says.
- **emptyOrUnavailableTextNodes** — nodes with no accessible text (images, custom-drawn, decorative).
- **maxDepth** — how deeply nested the tree was; a rough complexity signal.
- **distinctNodeClasses / widget classes** — how varied the exposed UI is; a `RecyclerView`-heavy
  tree and a canvas-drawn tree are very different compatibility prospects.
- **unreadableChildNodes** — where the platform refused to enumerate children. A non-zero count is
  a finding about the app, not a bug in the traversal.
- **FAILED with rootAvailable=false** — the event fired but there was no root at that moment; a
  legitimate, honestly-reported outcome, not a crash.

## 8. False conclusions to avoid (say these in your viva)

- A **large** textBearingNodes count does **not** mean all messages were captured.
- A **COMPLETED** inspection does **not** mean a conversation was extracted — only that a tree was
  counted.
- **Preprocessing tests passing** does **not** mean detection accuracy is good — there is no model.
- **The app running on the emulator** does **not** mean the real-app path was exercised — it has
  not been (see §6); the compatibility result table is deliberately empty.
- A **0% text-availability** reading does **not** mean the experiment failed; it means that screen
  is inaccessible, which is a legitimate negative result.
- **Text-bearing node count ÷ message count** is not coverage unless a real ground-truth transcript
  of that same screen exists (see [11_REAL_APP_COMPATIBILITY_REPORT.md](11_REAL_APP_COMPATIBILITY_REPORT.md) §9).

## 9. Regression testing

The unit tests are the regression net: any change to the controller, inspector, or preprocessor is
caught by re-running `:app:testDebugUnitTest`. Determinism (fixed clock in tests, pure functions)
makes them stable across runs.

Next: [07_BTP_PRESENTATION_GUIDE.md](07_BTP_PRESENTATION_GUIDE.md).
