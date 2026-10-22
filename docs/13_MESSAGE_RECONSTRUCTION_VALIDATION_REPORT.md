# Message Reconstruction Validation Report

Document 13 of the BTP research prototype. Companion to
[12_MESSAGE_LEVEL_EXTRACTION_IMPLEMENTATION_LOG.md](12_MESSAGE_LEVEL_EXTRACTION_IMPLEMENTATION_LOG.md)
(implementation) and [11_REAL_APP_COMPATIBILITY_REPORT.md](11_REAL_APP_COMPATIBILITY_REPORT.md)
(structural compatibility observations R1–R8).

Written 2026-10-20. Every number in this file was produced by a command that was executed and an
output that was read. Where something was **not** measured, this file says `NOT MEASURED` and names
the missing prerequisite — it never substitutes a plausible value.

---

## 1. Scope, and the sentence this report exists to prevent

**Question asked:** can visible, accessible UI elements on one screen be reconstructed into
message-level records, for an explicitly selected conversation the researcher is authorised to study?

**Question this report does NOT answer, and no reader should take it as answering:** can this tool
extract conversations from Instagram, Instagram Lite, WhatsApp, Signal, Telegram or Google Messages?
Not at any accuracy level anyone could quote. Three real-app extraction runs have now completed on
`com.instagram.android` on this emulator (2026-10-20 18:10:38, 18:11:37 and 18:28:08, recorded in
§5.2), which
proves the pipeline *runs* against a live third-party tree — it does not prove the output is a
correct conversation. Neither screen has been transcribed, so there is no denominator and no score.
WhatsApp, Signal, Telegram and Google Messages have had **no** extraction run at all.

The distinction matters because the synthetic metrics in §5 look like accuracy figures. They are
accuracy figures, but their denominator is a tree this project wrote. A classifier scored on
fixtures it was designed against is a **specification test**, not evidence about a third-party app.
Two builds of the same product already differ substantially in tree shape (docs/11 R2 vs R3), and
the same screen re-read seven minutes later drifts in its own text-bearing counts (R5 vs R7). Real
apps are not our fixtures.

---

## 2. Environment actually tested

| Item | Value | How verified |
|---|---|---|
| Host OS | Windows 10, 10.0.26200, x64 | `cmd` build host |
| Emulator | AVD `Pixel_API35_Play`, serial `emulator-5554` | `adb devices` → `emulator-5554 device` |
| Android on device | Android **15**, API **35**, model `sdk_gphone64_x86_64` | `getprop ro.build.version.release` / `.sdk` / `ro.product.model` |
| JDK used for builds | Android Studio embedded JBR (`/c/Program Files/Android/Android Studio/jbr`), JVM 17 | `JAVA_HOME` set explicitly per run |
| Toolchain | Gradle 8.14.6, AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2026.01.01 | `app/build.gradle.kts`, wrapper |
| SDK levels | `compileSdk 36`, `targetSdk 36`, `minSdk 24` | `app/build.gradle.kts:9-15` |
| App under test | `com.example.groomingresearchdemo` v1.0 (versionCode 1), debug, APK **12,114,117 bytes** | Phase E build output; `install -r` → `Success` |
| Target app selected | `com.google.android.apps.messaging`, versionCode 235003880, versionName `messages.android_20240528_00_RC01_alldpi.x86_64.phone`, targetSdk 35 | `dumpsys package` |
| Also installed | Instagram 452.0.0.48.64 (vc 385810272); Instagram Lite 533.0.0.9.105 (vc 1089004261) | `dumpsys package` |
| Allowlisted but absent | WhatsApp, Telegram, Signal — **not installed** on this image | `dumpsys package` → no `versionName` |
| Accessibility service state | Enabled: `com.example.groomingresearchdemo/…service.ChatInspectionService` | `settings get secure enabled_accessibility_services` |

**About that last row.** The service is enabled because the researcher toggled it by hand in Settings
during an earlier session. This harness has refused to enable it programmatically
(`settings put secure enabled_accessibility_services …`), refused a cross-app `uiautomator dump`, and
refused `adb shell input tap` against another app. The toggle is the consent gate, not an obstacle;
the reasoning is in docs/11 §7 ("Why the harness did not enable the service") and the parallel
"documented, not fabricated" policy in docs/06 §6. Enabling it by hand is also why no automated
real-app metric in this report can be produced without a human.

**Test layer:** JUnit 4, JVM only. No Robolectric, no Espresso, no instrumented test. No dependency
was added for this task.

---

## 3. Capability status table

Legend — **Verified**: an executed test or observed device output demonstrates it.
**Partially verified**: demonstrated on synthetic input only, or demonstrated with a documented
degradation on device. **Not verified**: implemented but never observed working on the thing that
matters. **Failed**: observed to be impossible under current constraints. **Untested**: no evidence
either way.

| # | Capability | Status | Evidence / reason |
|---|---|---|---|
| 1 | User-triggered one-shot inspection still gates on consent + service + target | **Verified** | `InspectionControllerTest` 15 pre-existing tests, unchanged and passing; `InspectionControllerExtractionTest.everyGateThatRefusesAnInspectionAlsoRefusesTheExtractionStage` |
| 2 | Structural counts R1–R8 reproducible after this change | **Verified** | `theStructuralStageRunsIdenticallyWhetherOrNotExtractionIsArmed`; `StructuralInspector.kt` byte-identical (mtime 2026-10-19 23:34) |
| 3 | Node geometry, flags, parent and visibility read defensively | **Partially verified** | 11 traverser tests incl. `aRefusedPropertyCostsThatPropertyNotTheNode`; but `nodeId()` has no device implementation (§6 finding 1) |
| 4 | Duplicate-visit suppression | **Partially verified** | Works in tests via node id and wrapper identity (`aNodeReachedTwiceThroughTheSameParentIsSampledOnceByIdentity`, `twoWrapperObjectsCarryingTheSamePlatformNodeIdCollapseToOneSample`); **not observable on device**, where `getSourceNodeId()` is not public |
| 5 | Rule-based candidate classification with named exclusions | **Verified (synthetic)** | `CandidateClassifierTest` 16 tests, all exclusions asserted by reason string |
| 6 | Multi-line message kept as one record | **Verified (synthetic)** | `f6_aMultiLineBubbleIsOneRecordAndKeepsItsLineBreaks`, `aMultiLineBubbleStaysOneRecord` |
| 7 | Repeated identical text kept as two records | **Verified (synthetic)** | `f7_repeatedIdenticalTextStaysTwoRecords`, `theSameWordsSaidTwiceStayTwoRecords` |
| 8 | Parent/child text duplication suppressed | **Verified (synthetic)** | `f4_nestedContainersEchoingOneBubbleYieldOneRecordNotTwo` (`containerEchoesSuppressed == 2`), `aContainerEchoingItsChildIsSuppressedWhileTheChildSurvives` |
| 9 | Two text nodes in one row never merged | **Verified (synthetic)** | `twoTextNodesSharingARowAreLeftAmbiguousAndNeverMerged`; see §6 finding 2 for how the row test degrades on device |
| 10 | Geometry-based top-to-bottom ordering | **Verified (synthetic)** | `f8_backwardsEnumerationIsReorderedByGeometryNotTrustedAsTraversalOrder`; order accuracy 8/8 in §5 |
| 11 | Order honestly reported as unestablished when geometry is missing | **Verified** | `aCandidateWithoutGeometryStopsTheWholeRunFromClaimingAnOrder` → `TRAVERSAL_UNVERIFIED`, all records stay `CANDIDATE`, `acceptedCount == 0` |
| 12 | Boundaries-not-determinable ⇒ `INCOMPLETE`, not a guessed transcript | **Verified (synthetic)** | `f12_ambiguousBoundariesAreKeptAndBlockAReconstructedVerdict` (3 ambiguous, `INCOMPLETE`, 0 accepted) |
| 13 | Empty / unsupported screen ⇒ `UNSUPPORTED`, not an empty conversation | **Verified (synthetic)** | `f5_aScreenOfChromeIsUnsupportedRatherThanAnEmptyConversation`, `f13_anEmptyTreeAndANullRootAreBothUnsupportedNotSilentlyEmpty` |
| 14 | Mid-inspection tree mutation detected | **Verified (synthetic)** | `f10_aTreeThatMutatesMidTraversalIsReportedAsUnreliable`, `aNodeThatClaimsMoreChildrenThanItReturnsIsDetected` |
| 15 | Partially visible screen flagged as partial | **Verified (synthetic)** | `f11_aConversationRunningOffScreenIsLabelledPartial`, `aViewportThatCutsARecordMakesTheSamplePartialAndSaysSo`; ladder blocks level 5 |
| 16 | Node cap produces a *partial* result, not a silent complete one | **Verified** | `theNodeCapTruncatesAndRecordsIt` (G6 closed); structural stage still ignores truncation, deliberately left as-is |
| 17 | No invented sender, timestamp, message or boundary | **Verified** | `nothingIsInventedAboutSendersOrTimesByDefault`, `f3_absentTimestampsStayAbsentRatherThanBecomingReconstructionOrder`, `anUnknownTimestampStaysNullRatherThanBecomingZeroOrNow` |
| 18 | Speaker labels from node-label evidence | **Partially verified** | 2/2 on synthetic S6 **only under the opt-in rule**, and the output self-reports as `unvalidated against annotated real-app data`; off by default ⇒ `NOT MEASURED` |
| 19 | Speaker side from alignment alone | **Verified as deliberately weak** | `theAlignmentRuleReportsASideNotAPerson` — emits a side token, never a person; `anAlignmentTokenIsNeverRewrittenAsAPerson` at the adapter |
| 20 | Timestamp identification | **Failed to obtain any real instance** | 0/1 on the one synthetic screen that has a rendered stamp. A genuine zero, measured — not an unmeasured gap |
| 21 | Five-level ladder gates independently | **Verified** | `InspectionLadderTest` 9 tests, incl. "candidates detected but boundaries unsettled ⇒ levels 1+2 only" |
| 22 | Message text never logged, reported or persisted | **Verified (code paths)** | `ExtractionPrivacyTest` 6 tests; zero `<uses-permission>`, no `INTERNET`; device logcat after cold start has 3 status lines, no content (§7) |
| 23 | Temporary, deliberate text review with destruction on exit | **Partially verified** | `ReviewScreen` + `forgetExtractedContent()` wired on both exits and tested at the store level; **never exercised on device** |
| 24 | Adapter refuses non-accepted records | **Verified** | `anIncompleteReconstructionIsRefusedAndReturnsNoModelInput`, `anUnsupportedReconstructionIsRefused`, `onlyAcceptedRecordsCrossTheBoundary` |
| 25 | Extraction cannot label anything as grooming | **Verified** | `theAdapterNeverProducesAGroomingJudgement`; `PlaceholderGroomingModelAdapter` still `NotIntegrated`, `isReady == false` |
| 26 | Cancellation disarms and skips extraction | **Verified (synthetic)** | `f14_cancellingTheArmMeansTheExtractionStageNeverRuns`; cancel sets `armedExtraction` false, `handleEvent` returns null, `completedCount == 0` |
| 27 | Cancel/Stop works against a live service on device | **Verified (device)** | 18:09:08 logcat: `Retained message-level content forgotten. Counts remain.` then `cancelInspection -> true`, then `Local results, session reports and any retained extraction cleared.` — a real human press, and the arm cleared |
| 28 | Real-app message extraction runs against a live third-party tree | **Partially verified** | Three COMPLETED runs on `com.instagram.android` (18:10:38, 18:11:37, 18:28:08) with `extractionOptIn=true`; all produced a status, counts, `order=GEOMETRY` and no crash (§5.2). Correctness of the text is **not** established — no annotated sample |
| 29 | Real-app precision / recall / order / attribution | **NOT MEASURED** | Requires the consented annotated sample (docs/11 §9). Two Instagram runs exist but neither screen was transcribed, so no denominator exists. No number is offered |
| 30 | Build + install + cold start on Android 15 | **Verified** | Phase E/F: `BUILD SUCCESSFUL in 9s`, `Success`, `topResumedActivity=…MainActivity`, pid 13377, 0 fatal lines |

---

## 4. Synthetic validation set

Two distinct fixture families, deliberately separated because they are scored differently.

### 4.1 Behavioural fixtures — 14 cases, pass/fail, no metric

One per §8-mandated case (F1–F14 in docs/12 Phase D). Scored as assertions about pipeline behaviour:
what status is returned, which records are placed, what is *not* invented. No percentage is computed
from these; a fixture that "passes" means the pipeline behaved as specified on a tree built to that
spec, nothing about a real app.

### 4.2 Annotated screens — 6 cases, metric-bearing

Defined in `ExtractionMetricsTest` with hand-declared ground truth: expected turns in reading order,
expected interface labels, expected speaker map, expected timestamp-node count. All strings are
synthetic and self-labelled (`"alpha synthetic turn"`, `"Synthetic Name One"`,
`"10:45 synthetic stamp"`). No private conversation appears in any screen.

| Screen | Shape | Annotated turns | Annotated labels | Speakers | Stamp nodes |
|---|---|---|---|---|---|
| S1 | four clean bubbles in a RecyclerView | 4 | 0 | 0 | 0 |
| S2 | three bubbles + `EditText` composer + `Button` send | 3 | 2 | 0 | 0 |
| S3 | one multi-line bubble + two identical-text bubbles | 3 | 0 | 0 | 0 |
| S4 | **one row** holding name + body + rendered stamp in the same vertical band | 1 | 0 | 1 | 1 |
| S5 | chrome only: Search, More options, composer, Send | 0 | 4 | 0 | 0 |
| S6 | short label node above two turns (the shape rule S1 claims to recognise) | 2 | 0 | 2 | 0 |
| **Pooled** | | **13** | **6** | **3** | **1** |

S4 and S6 are the interesting ones. S4 is the shape that breaks naive pipelines — three text nodes
inside one message row, only one of which is the message. S6 is the shape that tempts a sender rule,
where the label's visibility is unknown so it lands at `MEDIUM`, and the label rule fires precisely
because it is not `HIGH`.

---

## 5. Metrics

Every metric names its denominator and its numerator's definition. `NOT MEASURED` means the
numerator or denominator does not exist yet; a `0/n` means the denominator exists and the pipeline
scored nothing — which is a result, not a gap.

### 5.1 Synthetic pool (screens S1–S6), default configuration

| Metric | Definition | Value | Reading |
|---|---|---|---|
| Candidate-message precision | accepted records that match an annotated turn ÷ all accepted records | **12/13** (0.923) | One false positive: S6's sender label kept as a message |
| Candidate-message recall | accepted records matching an annotated turn ÷ annotated turns | **12/13** (0.923) | One miss: S4's body, left `AMBIGUOUS` in the crowded row |
| Message-reconstruction precision | placed **and** correct records ÷ placed records | **12/13** | Undefined on S4, which places nothing — reported as no denominator, not as 0/0 |
| Exact-text match rate | verbatim-agreeing matched turns ÷ annotated turns | **12/13** (0.923) | Text survives unedited when it survives at all; no normalisation was applied to flatter this |
| Message-order accuracy | correctly ordered consecutive annotated pairs ÷ pairs where both turns were placed | **8/8** (1.000) | Pairs exist only on S1 (3), S2 (2), S3 (2), S6 (1) |
| Speaker-attribution accuracy | correctly attributed turns ÷ turns the pipeline attempted to attribute | **NOT MEASURED** (attempted 0, annotated-with-speaker denominator 3) | No attribution rule is enabled by default, so the numerator is not zero — it does not exist |
| Speaker-attribution accuracy, opt-in rule S1 | as above, S6 only | **2/2** | Self-declares `unvalidated against annotated real-app data`. Two data points on a fixture built for it. Not a claim about any app |
| Timestamp-identification accuracy | recovered timestamps ÷ annotated timestamp nodes | **0/1** (0.000) | A **genuine zero**: S4 has a rendered stamp and the pipeline correctly refuses to promote a bare text node into a timestamp without positive identification |
| Ambiguous-result rate | screens ending `INCOMPLETE` ÷ screens run | **1/6** | S4, by design |
| False-positive rate on interface text | annotated labels promoted to accepted records ÷ annotated labels | **0/6** (0.000) | Composer, send, search and overflow all rejected, each with its own named reason |
| Per-screen recall denominator | — | S5: annotated turns 0 ⇒ denominator 0 ⇒ **undefined**, reported as such | `Ratio(0,0).value == null`, asserted directly |

### 5.2 Real apps

**Accuracy metrics — all still NOT MEASURED.** No real-app screen has been transcribed, so no
denominator exists for any of them.

| App | Runs with extraction | Accuracy metrics | Missing prerequisite |
|---|---|---|---|
| `com.instagram.android` (452.0.0.48.64) | **3** (18:10:38, 18:11:37, 18:28:08) | **NOT MEASURED** | A consented transcription of the exact screen (§docs/11 §9) taken at run time |
| Google Messages, Instagram Lite, WhatsApp, Signal, Telegram | **0** | **NOT MEASURED** | Human at the device, target switched, one armed press |

Turn coverage `B/T` remains **NOT MEASURED** and forbidden until a transcription exists (docs/11 §9
step 1).

**What the two Instagram runs actually printed** (`logcat -s GroomingResearchDemo:V`, verbatim, counts
only — the log line carries no text by design and none appeared):

```
18:10:38.962 Inspection outcome=COMPLETED target=com.instagram.android event=true packageMatched=true root=true nodes=56 textBearing=32 withText=20 descOnly=12 emptyText=24 chars=268 depth=6 classes=8 unreadable=0 truncated=false
18:10:38.962 Extraction  status=INCOMPLETE   sampled=56 candidates=11 accepted=11 ambiguous=4 rejected=41 dupesSuppressed=0 echoesSuppressed=0 order=GEOMETRY speakers=NOT_ATTEMPTED timestamps=false partial=true

18:11:37.173 Inspection outcome=COMPLETED target=com.instagram.android event=true packageMatched=true root=true nodes=58 textBearing=33 withText=20 descOnly=13 emptyText=25 chars=253 depth=6 classes=8 unreadable=0 truncated=false
18:11:37.174 Extraction  status=RECONSTRUCTED sampled=58 candidates=17 accepted=17 ambiguous=0 rejected=41 dupesSuppressed=0 echoesSuppressed=0 order=GEOMETRY speakers=NOT_ATTEMPTED timestamps=false partial=false

18:28:08.085 Extraction  status=RECONSTRUCTED sampled=56 candidates=16 accepted=16 ambiguous=0 rejected=40 dupesSuppressed=0 echoesSuppressed=0 order=GEOMETRY speakers=NOT_ATTEMPTED timestamps=false partial=false
```

The third row is quoted alone: it was read from the log buffer after the fact, and its paired
`Inspection outcome=…` structural line had already scrolled out of `adb logcat -d`'s tail. The emulator
was then shut down, so the buffer is gone and that line cannot be recovered. It is recorded as a
partial observation rather than reconstructed from the other two runs.

Preceding those, one run **failed on a real transition frame**:

```
18:09:58.347 Inspection outcome=FAILED target=com.instagram.android event=true packageMatched=true root=false nodes=- …
```

**Four things these rows establish, and one they do not.**

1. **The pipeline survives a live third-party tree.** 56 and 58 nodes traversed, geometry-based order
   resolved (`order=GEOMETRY`), 40–41 nodes rejected as non-message per run, no crash, no truncation.
   That is the first time any of this has run against a real app.
2. **RC-04 fired.** docs/11 lists RC-04 ("null root on a transition frame") as Pending because it had
   never been observed in eight runs. It has now been observed, at 18:09:58: `event=true
   packageMatched=true root=false` → `FAILED`, correctly distinguished from a screen with no text.
3. **Device redaction holds on real content.** Across a consent revoke, two cancels, four arms and
   three completed extractions on Instagram, the app's own log lines contain counts, flags and package
   names and **not one message string**. This is §4's guarantee observed on real data, not just
   asserted in tests.
4. **Speakers and timestamps were recovered from nothing, as designed** (`speakers=NOT_ATTEMPTED`,
   `timestamps=false`), so what those runs produced is *ordered message-shaped text*, not a labelled
   conversation.
5. **What they do not establish:** the first two runs are ~60 seconds apart on the same app, the
   structural shape barely moved (56→58 nodes, `withText` 20→20), yet the verdict swung from
   `INCOMPLETE`/`partial=true`/11 accepted to `RECONSTRUCTED`/`partial=false`/17 accepted, and the
   third run landed at 16 accepted on 56 sampled. The honest reading is **unexplained**: either the
   screen itself changed (a different thread, or a scroll) or the geometry row-grouping flipped on
   nearly identical input. Nothing here distinguishes the two, and an unlabelled run cannot. That
   swing is exactly why the next milestone is a transcribed screen rather than more runs — and why
   `accepted=17` must not be repeated anywhere as "17 messages extracted".
6. **The researcher reported false positives by eye.** Reading the review screen after the 18:28 run,
   the observation was that non-message on-screen text was being counted as messages. That is
   consistent with the predicted failure mode — synthetic screen S6 loses exactly one point to a
   sender label kept as a message, and Instagram chrome such as a contact name, an "Active …" line or
   a composer placeholder is a short leaf `TextView` with no geometry objection. It is recorded as a
   **qualitative, un-itemised** observation: no specific record was annotated as wrong, so it produces
   no false-positive rate and no rule was added in response. Turning it into a number needs the
   per-record annotation described in §9 step 1.

`dupesSuppressed=0` on all three runs is **not** evidence that dedup found nothing to do; on device
`nodeId()` is always null (§6 finding 1), so the counter is not meaningful there.

### 5.3 Test totals

160 unit tests, 0 failures, 0 errors, 0 skipped — 57 pre-existing (all still passing, none deleted or
weakened) plus 103 new, of which four are the settle-delay token tests added 2026-10-21. The 2026-10-20
per-class table is in docs/12 Phase D; the four later tests are listed in the docs/12 addendum.

---

## 6. Structural failure examples — no private content

All examples are synthetic or structural. None contains real message text.

- **Crowded row (S4).** A name, a body and a rendered timestamp share one vertical band. The row
  detector returns `multiple-text-nodes-in-one-row`, all three become `AMBIGUOUS`, nothing is
  accepted, status is `INCOMPLETE` with a reason. The alternative behaviours this design rejects are
  merging the three strings into one "message" and picking the longest as "the" message — both would
  fabricate a transcript. This is the recall miss behind 12/13.
- **Container echo (F4).** A bubble container reports the same text as its child. Both wrappers echo;
  `containerEchoesSuppressed == 2` and exactly one record survives per bubble. Without this rule, a
  two-turn thread reports four turns — the most common way a naive extractor inflates.
- **Chrome-only screen (S5/F5).** Four interface labels, zero turns. Returns `UNSUPPORTED`, not an
  empty conversation. Reporting an empty conversation would imply the thread is empty; reporting
  `UNSUPPORTED` says the screen gave nothing to work from.
- **Two builds, two shapes (R2 vs R3, docs/11).** Same product, different tree shape. Any
  app-specific assumption without observed evidence would break here, which is why no rule in the
  classifier keys on a package name and no view ID is read at all.
- **Same screen, drift (R5 vs R7, docs/11).** 46 nodes both times; `withText` 4→5, `descOnly` 8→7,
  `chars` 70→74, seven minutes apart. Shape is stable, content moves. A back-to-back repeat proves the
  traversal is deterministic, not that the screen is immutable.
- **Status word inside a real message.** `"did you read it yet"` contains no chrome token and is not
  chrome; a naive "reject anything matching a status phrase" rule deletes real messages. Covered by
  `aStatusWordInsideARealMessageDoesNotDeleteTheMessage`. Conversely `"ok"` survives the chrome list
  because a short reply is exactly what a chrome filter eats.

**Two findings only a device can produce** (docs/12 Phase F, restated because they qualify any
future real-app number):

1. **`nodeId()` is always null on device.** `getSourceNodeId()` is not public API — verified with
   `javap` against `platforms/android-36/android.jar`. Reaching it would require a hidden API, which
   §4 forbids, so the traverser falls back to wrapper identity. Duplicate-visit suppression is
   implemented and exercised in fixtures but **not observable** on a real screen through the id path.
   A device `duplicatesSuppressed` figure is weaker evidence than a test one.
2. **`parentNodeId` is always null on device, which silently widens the same-row test.** The row
   detector asks for "same parent **and** vertical overlap". With both parent ids null, `null == null`
   makes the parent clause vacuously true, so on device the test is geometry-only across the whole
   sampled screen. Still defensible — overlap is ≥50% of the shorter box, so separate rows do not
   collide — but a device `multiple-text-nodes-in-one-row` objection must not be quoted as if the
   sibling constraint held.

---

## 7. Privacy safeguards verified

| Safeguard | How verified | Result |
|---|---|---|
| Inspection starts only from a deliberate user action | `arm()` requires consent + service + target; 15 unchanged controller tests | ✅ |
| Extraction choice captured at arm time, not widened later | `theExtractionChoiceIsCapturedAtArmTimeNotAtEventTime` (`ALREADY_ARMED` cannot change it) | ✅ |
| Visible indication while armed | Button label changes to "Start Inspection + Message-Level Stage" (`ExperimentScreen.kt:137`); results screen shows active state | ✅ |
| Stop/Cancel disarms immediately | `cancel()` clears `armedTarget` + `armedExtraction`, records `CANCELLED` with `stats = null`; F14 | ✅ (synthetic) |
| No automatic start when a messaging app opens | `forceDisarm()` on connect and disconnect; nothing in the service arms without the Start button | ✅ |
| No background polling / scheduled scraping / continuous monitoring | No `Timer`, `WorkManager`, `AlarmManager` or coroutine `launch(`. There is exactly one `Handler` + `postDelayed` call site (added 2026-10-21, docs/12 addendum): a 1.5 s settle delay that defers the single tree read for a qualifying event. It is not a loop or polling interval — one event schedules one read, the first to run consumes the arm and later ones are rejected, and an arm token drops a queued read whose arm was cancelled or replaced. The only `kotlinx.coroutines` imports are `StateFlow`/`MutableStateFlow` in `ResearchStore` for on-demand UI state — no scope, no dispatcher, no deferred work | ✅ |
| Currently-visible UI only; no off-screen or deleted content | Single `rootProvider()` snapshot per inspection; no scroll, no navigation, `MAX_TRAVERSAL_NODES = 5000` | ✅ |
| No bypassing app restrictions, databases, network, encryption, hidden APIs | No `INTERNET`, zero `<uses-permission>`, no `run-as`, no content-provider read, no reflection into hidden members (the two places that would need them were declined — §6 finding 1) | ✅ |
| No notification / clipboard / keystroke collection | No `NotificationListenerService`, no clipboard access, no `InputMethod` hook; nothing added to the manifest | ✅ |
| Three-layer package bound preserved | config XML `packageNames` allowlist (six entries, unchanged) → runtime `setServiceInfo()` narrowing → `armedTarget` equality; `anEventFromAnotherPackageLeavesBothTheArmAndTheExtractionFlagIntact` | ✅ |
| Text never in Logcat, reports, crash messages | `ExtractionPrivacyTest`: marker `PRIVATE-MESSAGE-MARKER-4b7b` reaches records but appears in no report, no `toString()` of `Traversal`/`NodeSample`/`CandidateJudgement`/`MessageRecord`/`Reconstruction`, no reason or evidence string, no `candidateId`; hand-written `toString()` on all five; `CompatibilityReportBuilder` untouched, its privacy footer still asserted present | ✅ |
| No new persistence key | grep of `ResearchStore` for preference/file writes finds none for extraction; one reconstruction held in memory only; `allowBackup="false"` | ✅ |
| Temporary content cleared on finish/cancel/leave | `forgetExtractedContent()` on arm change, revoke, and both `ReviewScreen` exits; `withoutRecords()` keeps diagnostics | ✅ (code), ⬜ (device) |
| No analytics, ads, or unrelated permissions | No dependency added; `app/build.gradle.kts` mtime 2026-10-19 22:22 | ✅ |
| No fabricated model output | `PlaceholderGroomingModelAdapter` returns `NotIntegrated`, `isReady == false`; adapter refuses non-accepted input | ✅ |
| Device behaviour after cold start | `logcat -c` → start → 740 lines, `grep -c "FATAL EXCEPTION\|AndroidRuntime"` → **0**; the app's only lines: `ResearchStore initialised. consent=ACCEPTED target=com.google.android.apps.messaging serviceEnabled=true`, `ChatInspectionService connected (…)`, `Accessibility service CONNECTED. Arm forced to NOT_ARMED.` — status and package names, no node content | ✅ |

---

## 8. Limitations

1. **Real-app extraction now exists, but with no ground truth.** Three runs on `com.instagram.android`
   (§5.2) prove the pipeline executes against a live third-party tree; no inspected screen was
   transcribed, so nothing about their correctness is known, and the researcher's by-eye report of
   false positives is un-itemised. Four of the six allowlisted apps have never been run at all, and
   one run ended `FAILED` on a null root. This is still the headline limitation.
2. **Fixtures were designed against the specification they test.** 12/13 on synthetic screens is
   conformance, not generalisation. Real chat UIs add avatars, unread dividers, reply quotes,
   reactions, delivery receipts and rich cards that no fixture here models.
3. **Node identity is unavailable on device** (§6.1), so dedup and the sibling clause of the row test
   degrade exactly as described.
4. **Levels 4 and 5 of the ladder are unreachable without a human.** Speaker attribution needs an
   annotated sample; complete-conversation validation needs a researcher's attestation. The design
   refuses to infer either from "the list happened to start at the top".
5. **Screen labelling is still open.** R1, R4–R8 have no confirmed screen type; RC-01/RC-02 ran but
   were never labelled; RC-04 (null root on a transition frame) has never fired. Three apps and eight
   windows is a handful, so no general compatibility claim is made from it.
6. **Timestamp recovery scored 0/1.** The mechanism exists and refuses to invent, but no positive
   identification path has yet been shown to fire on any screen, real or synthetic.
7. **No instrumented tests.** On-device behaviour of the extraction stage rests on a manual checklist
   that has not been run.
8. **Truncation semantics differ by design** between the two stages: the structural stage records
   `truncated` and still returns `COMPLETED`; the extraction stage converts it to a partial sample.
   The former was left alone because changing it would invalidate R1–R8.
9. **Single emulator, single Android version, x86_64 image.** No physical device, no API-24–26
   coverage despite `minSdk 24`, no arm64, no manufacturer skins.
10. **WhatsApp, Signal and Telegram are allowlisted but not installed**, so nothing about them is
    even structurally observed.

---

## 9. Future work, in the order that unblocks the most

1. **Produce one consented annotated thread** — Google Messages on `emulator-5554`, hand-transcribed
   per docs/11 §9. This is the critical path: it converts the first `NOT MEASURED` rows in §5.2 into
   denominator-bearing numbers and makes ladder level 4 reachable.
2. **Run the docs/12 Phase F checklist once**, extraction ticked, and record the row in §3 — including
   which ladder levels were met and the reason strings for those that were not.
3. **Label R4–R8 and the RC rows** so the existing structural evidence can be read as thread-screen
   evidence rather than unknown-screen evidence.
4. **Characterise the crowded-row failure on a real app** (S4's shape) before attempting to improve
   recall; a fix designed against a fixture would just move the fixture.
5. **Find a legal positive-timestamp signal.** Without one, timestamp accuracy stays 0/n and no
   time-ordered analysis is defensible.
6. **Only then** consider the `ValidatedRecordAdapter` → `ConversationPreprocessor` handoff on real
   data, still with the model un-integrated, and only with the researcher's own consented text.

Explicitly not planned, because §4 forbids them: scrolling, history navigation, clicking, background
monitoring, notification/clipboard/keystroke routes, view-ID-keyed rules, extra permissions, network
transmission, model training on extracted text.

---

## 10. Evidence index

| Claim | Artefact |
|---|---|
| 156 / 0 / 0 / 0, freshly observed | [`docs/evidence/phaseD_test_run_2026-10-20_175006.log`](evidence/phaseD_test_run_2026-10-20_175006.log) (EXIT=0, `BUILD SUCCESSFUL in 11s`, `24 actionable tasks: 24 executed`, 0 `e:`/`w:` lines) + JUnit XML under `app/build/test-results/testDebugUnitTest/` (15 files, per-class tables in docs/12 Phase D) |
| APK builds and installs | [`docs/evidence/phaseE_build_run_2026-10-20_175027.log`](evidence/phaseE_build_run_2026-10-20_175027.log) (EXIT=0, `BUILD SUCCESSFUL in 9s`, `37 actionable tasks: 37 executed`), `app/build/outputs/apk/debug/app-debug.apk` = 12,114,117 bytes, mtime 2026-10-20 17:50 |
| Phase A baseline these runs are compared against | [`docs/evidence/phaseA_baseline_run.txt`](evidence/phaseA_baseline_run.txt) — the `:app:testDebugUnitTest` run (EXIT 0, `BUILD SUCCESSFUL in 23s`, 24 tasks executed) behind the recorded 57-test baseline. The 12,032,138-byte APK figure is recorded in docs/12 Phase A from the earlier session and was not re-measured here |
| Cold start on Android 15 without a fatal exception | `adb install -r` → `Success`; `dumpsys activity activities` → `topResumedActivity=ActivityRecord{… .MainActivity t37}`, task `visible=true`; `pidof` → 13377; 740 logcat lines, 0 fatal hits. **The raw logcat dump was deliberately not archived**: it contains other applications' lines (input-method `EditorInfo`, task tracker, Play Store cache messages), which §4 forbids collecting as an alternative route. Only the three lines this app itself emitted are quoted, in §7 |
| `getSourceNodeId()` is not public | `javap` against `ANDROID_SDK/platforms/android-36/android.jar` |
| Structural stage unchanged | `StructuralInspector.kt`, `Enums.kt`, `CompatibilityReportBuilder.kt`, `ChatInspectionService.kt` mtimes 2026-10-19 23:34–23:36 |
| No permission or dependency added | `app/src/main/AndroidManifest.xml` (zero `<uses-permission>`, `allowBackup="false"`, `<queries>` only), `app/build.gradle.kts` mtime 2026-10-19 22:22 |
| Metrics denominators | `app/src/test/java/com/example/groomingresearchdemo/inspection/extraction/ExtractionMetricsTest.kt` (11 tests, `Ratio(n, d)`, `Ratio(0,0).value == null` asserted) |
| Redaction | `ExtractionPrivacyTest.kt` (6 tests), `CandidateClassifierTest.noEvidenceTokenEverQuotesTheNodesText` |
| Five gates independent | `InspectionLadderTest.kt` (9 tests) |
| R1–R8, RC table, transcription method | docs/11 §6, §9 |

---

## 11. Privacy statement for this document

This report contains no message text, no sender name, no contact name, no account identifier and no
conversation content. The strings inside §4–§6 are synthetic fixture literals written by this project
and are marked `synthetic` at every occurrence. The device evidence is a process id, a task state and
three status lines emitted by the app itself. The app has no `INTERNET` permission, writes nothing to
file or preferences, and holds at most one in-memory reconstruction that a deliberate navigation
destroys.
