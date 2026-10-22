# 12 — Message-Level Extraction Implementation Log

This is the working, stage-by-stage record for the message-level accessibility extraction and
conversation-reconstruction work. It is written so that a B.Tech panel can ask "what did you
actually measure?" and every answer below points at a file, a command, or a test result.

**Read [11_REAL_APP_COMPATIBILITY_REPORT.md](11_REAL_APP_COMPATIBILITY_REPORT.md) first.** That
document is the compatibility record; this document extends it. Nothing here overwrites it, and no
number in it is reused as if it meant something it does not.

Two rules govern every entry in this log:

1. **A claim is only made where it was executed and observed.** Anything else says `PENDING`,
   `NOT MEASURED`, or `NOT IMPLEMENTED`.
2. **A successful build is not a successful extraction.** Those are separate lines in every table
   below and they are never merged.

---

## Phase A — Repository audit

*Audited 2026-10-20. No implementation file was modified during Phase A.*

### A.1 What the repository already is

`D:\AccessTest` is a single-module Android application, `com.example.groomingresearchdemo`, written
in Kotlin with Jetpack Compose Material 3. It is **not** a git repository (no `.git` directory), so
there is no `git log` or `git blame` to consult; history lives in
[09_DEVELOPMENT_JOURNAL.md](09_DEVELOPMENT_JOURNAL.md).

Source footprint: **27 main Kotlin files**, **7 test Kotlin files**, one accessibility service
config XML, one manifest, Gradle Kotlin DSL.

The existing product answers exactly one question: *does a real messaging app expose accessibility
structure on a given screen?* It answers it by counting. It has, by explicit design, no ability to
produce message text. That is the single most important fact for this task, because the new
objective — reconstructing message-level records — is a capability the current code deliberately
does not have, and adding it must not quietly dismantle the reason it does not have it.

### A.2 File map (paths that matter for this task)

| Area | File | Role |
|---|---|---|
| Bound / constants | [AppConstants.kt](../app/src/main/java/com/example/groomingresearchdemo/AppConstants.kt) | Six-package `TARGET_OPTIONS`, `MAX_TRAVERSAL_NODES = 5000`, `MAX_CLASS_HISTOGRAM_KEYS = 40`, `MAX_NOTES = 6`, `MAX_REPORTS_KEPT = 20`, `LOG_TAG` |
| Node abstraction | [inspection/AccessibilityNodeView.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/AccessibilityNodeView.kt) | Android-free interface: `text()`, `contentDescription()`, `className()`, `childCount()`, `getChild(i)` — **5 methods, nothing else** |
| Production adapter | [service/RealAccessibilityNodeView.kt](../app/src/main/java/com/example/groomingresearchdemo/service/RealAccessibilityNodeView.kt) | Wraps `AccessibilityNodeInfo`; documents why `recycle()` is not called (API 34+ no-op) |
| Traversal | [inspection/StructuralInspector.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/StructuralInspector.kt) | BFS, `ArrayDeque`, per-node `runCatching`, emits aggregates only |
| Output model | [inspection/InspectionResult.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionResult.kt) | `StructuralStats` (12 fields, all counts/depths/class names/notes) + `InspectionResult` |
| State machine | [inspection/InspectionController.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionController.kt) | One-shot arm/consume/cancel; lazy `rootProvider`; target captured at arm time |
| Enums | [inspection/Enums.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/Enums.kt) | `ConsentStatus`, `ArmStatus`, `ExperimentOutcome`, `ArmResult` |
| Service | [service/ChatInspectionService.kt](../app/src/main/java/com/example/groomingresearchdemo/service/ChatInspectionService.kt) | Thin: `onAccessibilityEvent` → `ResearchStore.handleAccessibilityEvent`; `applyTargetFilter()` narrows `serviceInfo.packageNames` |
| Logging | [service/LogUtils.kt](../app/src/main/java/com/example/groomingresearchdemo/service/LogUtils.kt) | Single narrow entry point, 3 functions |
| Store | [state/ResearchStore.kt](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchStore.kt) | Process singleton, `StateFlow`, **two persisted keys only**, memory-only `sessionReports`, `logResult()` |
| UI state | [state/ResearchUiState.kt](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchUiState.kt) | Immutable snapshot; consent / service / arm / result kept orthogonal |
| Navigation | [ui/RootScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/RootScreen.kt) | 9-screen manual enum (`HOME…IMPORT`), `InfoCard`, `StatusRow` composables |
| Arm / cancel UI | [ui/screens/ExperimentScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ExperimentScreen.kt) | **The user-triggered entry point** (see A.4) |
| Results UI | [ui/screens/ResultsScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ResultsScreen.kt) | Per-stage outcome + counts, no content |
| Report UI | [ui/screens/ReportScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ReportScreen.kt) | Session report picker, in-memory only |
| Report renderer | [report/CompatibilityReportBuilder.kt](../app/src/main/java/com/example/groomingresearchdemo/report/CompatibilityReportBuilder.kt) | Counts-only text + `PRIVACY_FOOTER` |
| Preprocessing | [model/ConversationPreprocessor.kt](../app/src/main/java/com/example/groomingresearchdemo/model/ConversationPreprocessor.kt) | 5 pure functions + `validateModelInput()` |
| Record type | [model/ChatMessage.kt](../app/src/main/java/com/example/groomingresearchdemo/model/ChatMessage.kt) | `speaker: String?`, `text: String?`, `timestampMillis: Long?` — all nullable |
| Model seam | [model/GroomingModelAdapter.kt](../app/src/main/java/com/example/groomingresearchdemo/model/GroomingModelAdapter.kt) | `isReady`, `predict()`; `PlaceholderGroomingModelAdapter` returns `NotIntegrated` |
| Synthetic fixtures | [model/SampleConversations.kt](../app/src/main/java/com/example/groomingresearchdemo/model/SampleConversations.kt) | Fixed conversations for the lab screen |
| Authorized text | [model/AuthorizedTextParser.kt](../app/src/main/java/com/example/groomingresearchdemo/model/AuthorizedTextParser.kt) + [ui/screens/AuthorizedImportScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/AuthorizedImportScreen.kt) | The **only** place message text is processed; researcher-pasted, memory-only |
| Test fake | [test/…/inspection/FakeNode.kt](../app/src/test/java/com/example/groomingresearchdemo/inspection/FakeNode.kt) | `FakeNode(textValue, descValue, classValue, throwOnChildren)`, `addChild`, `chain(length)` |

Build config: [app/build.gradle.kts](../app/build.gradle.kts) — `compileSdk 36`, `targetSdk 36`,
`minSdk 24`, JVM 17, Compose BOM 2026.01.01, **test dependency is JUnit 4 only** (no Robolectric, no
Espresso, no coroutine-test). `unitTests.isReturnDefaultValues = true`.

### A.3 Baseline build and test results (re-executed during this audit)

Command run from `D:\AccessTest`:

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat --offline :app:testDebugUnitTest --rerun-tasks
```

`--rerun-tasks` was used deliberately: Gradle's `FROM-CACHE` / `UP-TO-DATE` behaviour can report a
green result for tests that did not actually execute in this invocation, and this log only records
results that ran.

Actual observed output (tail):

```
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 23s
24 actionable tasks: 24 executed
```

Per-class counts read from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:

| Test class | tests | failures | errors | skipped |
|---|---|---|---|---|
| `inspection.InspectionControllerTest` | 15 | 0 | 0 | 0 |
| `inspection.StructuralInspectorTest` | 13 | 0 | 0 | 0 |
| `model.ConversationPreprocessorTest` | 13 | 0 | 0 | 0 |
| `model.AuthorizedTextParserTest` | 9 | 0 | 0 | 0 |
| `report.CompatibilityReportTest` | 4 | 0 | 0 | 0 |
| `model.PlaceholderGroomingModelAdapterTest` | 3 | 0 | 0 | 0 |
| **Total** | **57** | **0** | **0** | **0** |

This is the regression baseline. Any later number must be compared against **57 / 0 / 0 / 0**.

**APK status at audit time:** `app/build/outputs/apk/debug/app-debug.apk` = **12,032,138 bytes**,
timestamped 2026-10-20 14:41 — i.e. built in an earlier session, **not re-built during Phase A**.
The Phase A run above executed test tasks only, so it does not prove the APK still builds. Rebuilding
is Phase E work and its result will be recorded there, not assumed here.

**Real-device status carried into this task (from docs/11 §6):** 8 observations R1–R8 on
`emulator-5554` (API 35, `Pixel_API35_Play`), Google Messages + Instagram 452.0.0.48.64 + Instagram
Lite 533.0.0.9.105, all `COMPLETED`, all `root=true`, `unreadable=0`, `truncated=false`.
**RC-01/RC-02 rows are still screen-unlabelled and RC-04 is still Pending.** No extraction coverage
number exists, and docs/11 §9 forbids quoting one until a transcribed ground-truth thread exists.

### A.4 The user-triggered inspection entry point, traced end to end

This is the path a new extraction stage must hook into — not replace.

1. **User presses** `Start One-Time Inspection` —
   [ExperimentScreen.kt:75-86](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ExperimentScreen.kt#L75).
   The button is `enabled` only when consent is `ACCEPTED` **and** `serviceEnabled` **and**
   `armStatus == NOT_ARMED`. A second gate exists in the controller, so the UI gate is not the only
   one.
2. `ResearchStore.refreshServiceStatus()` then `ResearchStore.startInspection()` —
   [ResearchStore.kt:233-243](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchStore.kt#L233).
3. `InspectionController.arm(consent, serviceEnabled, targetPackage)` —
   [InspectionController.kt:50-60](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionController.kt#L50).
   Gates checked separately, each returns its own `ArmResult`. On success: `armedTarget = target`,
   `armStatus = ARMED`. **Nothing has been read from any app at this point.**
4. **User** brings the target app forward; Android delivers a window event.
   `ChatInspectionService.onAccessibilityEvent()` —
   [ChatInspectionService.kt:48-65](../app/src/main/java/com/example/groomingresearchdemo/service/ChatInspectionService.kt#L48).
5. → `ResearchStore.handleAccessibilityEvent(packageName, rootProvider)` —
   [ResearchStore.kt:265-281](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchStore.kt#L265).
   The `rootProvider` is a **lambda**; `rootInActiveWindow` is not touched until the controller
   decides to inspect.
6. → `InspectionController.handleEvent()` —
   [InspectionController.kt:90-129](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionController.kt#L90).
   Non-matching package returns `null` **without consuming the arm**. On a match it sets
   `EXECUTING` *before* reading the root (consume-once), calls `rootProvider()`, then
   `inspector.inspect(root)`, then returns to `NOT_ARMED` with `armedTarget = null`. **No re-arm.**
7. **Cancellation** — `Stop / Cancel Inspection`
   ([ExperimentScreen.kt:88-97](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ExperimentScreen.kt#L88),
   enabled only while `ARMED`) → `ResearchStore.cancelInspection()` → `controller.cancel()` records a
   `CANCELLED` outcome and **runs no traversal at all**.
8. **Visible active indicator**: `armIndicator()`
   ([ExperimentScreen.kt:132-141](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ExperimentScreen.kt#L132))
   prints *Not armed* / *Waiting for a qualifying accessibility event* / *Inspection executing* / the
   terminal outcome. The dashboard also shows armed-vs-configured package separately.

### A.5 How nodes are traversed and statistics calculated

`StructuralInspector.inspect()`
([StructuralInspector.kt:19-103](../app/src/main/java/com/example/groomingresearchdemo/inspection/StructuralInspector.kt#L19)):

- Iterative BFS with an explicit `ArrayDeque<Pair<AccessibilityNodeView, Int>>`; root is depth 1.
  Chosen over recursion so a deep messaging hierarchy cannot blow the stack.
- Stops at `maxNodes` (5000) and sets `truncated = true` plus a note.
- Per node: `className()` → histogram (bounded at 40 keys, first-seen keys always counted) and
  distinct set; `text()` → if non-blank, `nodesWithText++` and `totalTextCharacters += text.length`;
  else `contentDescription()` → `nodesWithContentDescriptionOnly++`; either → `textBearingNodes++`.
- Children read inside `runCatching`; on failure `unreadableChildNodes++` and a note naming the depth
  and class is added. **A hostile subtree cannot crash the inspection.**
- Every read (`readText`, `readDescription`, `readClassName`) is individually `runCatching`-wrapped,
  so a node that throws on one property still contributes its other properties.
- Notes are de-duplicated and capped at 6.

Derived: `emptyOrUnavailableTextNodes = nodesVisited - textBearingNodes`, and
`textAvailabilityPercent = textBearingNodes * 100 / nodesVisited` (0 for an empty traversal).

**The text is dropped.** `readText()` returns a `String?` used only for blankness and length; it is
not stored, returned, logged, or reachable from `StructuralStats`. Confirmed at
[InspectionResult.kt:6-8](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionResult.kt#L6).

### A.6 Privacy audit: is raw text ever retained, logged, exported, or transmitted?

Answer for the **current** code: **no**, and each of the four verbs was checked separately.

| Check | Method | Result |
|---|---|---|
| Transmitted | grep `INTERNET\|HttpURLConnection\|OkHttp\|Retrofit\|WebView` across `app/src/main` | **0 matches.** The manifest ([AndroidManifest.xml](../app/src/main/AndroidManifest.xml)) declares **no `<uses-permission>` element at all** — only `<queries>` for six packages and the `BIND_ACCESSIBILITY_SERVICE`-protected service. There is no network stack to leak through. |
| Logged | grep `Log\.[iwed]\|LogUtils\.\|println\|System.out` across `app/src/main` | **17 call sites, all reviewed.** Every one carries a lifecycle event, a package name, a boolean, or a count. `ResearchStore.logResult()` ([ResearchStore.kt:283-297](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchStore.kt#L283)) emits `outcome / target / event / packageMatched / root / nodes / textBearing / withText / descOnly / emptyText / chars / depth / classes / unreadable / truncated` — **numbers only**. No node text reaches Logcat today. |
| Persisted | grep `File(\|openFileOutput` → **0 matches**; `SharedPreferences` appears only in `ResearchStore` | Exactly **two keys**: `consent_status` and `target_package` ([ResearchStore.kt:40-42](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchStore.kt#L40)). `sessionReports` is a plain `mutableListOf` guarded by `synchronized`, memory-only, capped at 20, and `allowBackup="false"` in the manifest. |
| Alternative collection | grep `ClipboardManager\|NotificationListener\|UsageStats\|getRunningTasks` → **0 matches** | No clipboard, notification-listener, usage-stats or task-list collection exists. |
| Background collection | grep `WorkManager\|AlarmManager\|Timer\|delay(` → **0 matches**; grep `Handler(\|postDelayed` → **one call site** (added 2026-10-21, see the addendum at the end) | No timer, worker or alarm. One `Handler` defers the single tree read by 1.5 s per qualifying event, guarded by an arm token; it is not a loop or polling interval, and the first queued read to run consumes the arm. Coroutines appear **only** as `StateFlow`/`MutableStateFlow` type imports for UI state; the inspection path is synchronous. The service never polls: it waits for an event and consumes one arm. |
| Node lifetime | read `RealAccessibilityNodeView` header comment | `recycle()` deliberately **not** called — deprecated at API 33 and a no-op once Android 14 removed the pool; target is API 36. Nodes are reachable only from the inspector's queue and go out of scope when `handleEvent` returns. No node reference is stored on `ResearchStore` or `InspectionController`. |
| Export | `ReportScreen` renders into a `SelectionContainer` for the researcher to copy by hand | Copying is a human action outside the app, same as pasting into a lab notebook. The app performs no export; there is no share intent, file write, or upload. |

**Redaction is asserted, not promised.** `CompatibilityReportTest.nodeTextNeverAppearsInTheReport()`
([CompatibilityReportTest.kt:46-58](../app/src/test/java/com/example/groomingresearchdemo/report/CompatibilityReportTest.kt#L46))
builds a tree whose text and description are the marker `PRIVATE-MESSAGE-MARKER-8f21c`, renders a
report, and asserts the marker appears nowhere while the derived counts do appear. **Any change
made in Phase C must keep this test green and must not weaken it.**

### A.7 Existing preprocessing and model interfaces — actual implementation status

Inspected, not assumed.

- **Implemented and tested:** `ConversationPreprocessor` — `normalizeWhitespace`, `cleanMessages`,
  `preserveMessageOrder`, `toModelInput`, `preprocess`, `validateModelInput` →
  `PreprocessValidation(ok, modelInput, cleanedMessageCount, problems)`. 13 unit tests green.
  Its documented invariants are exactly the ones the new adapter must inherit: conservative
  normalisation (whitespace collapse + trim only; no lowercasing, no punctuation or emoji stripping),
  Unicode preserved, **repeated legitimate messages kept**, blank messages dropped, chronological
  sorting applied **only when every timestamp is present**, missing speakers rendered as unlabeled
  lines rather than a fake name.
- **The expected input schema is `List<ChatMessage>`**, i.e. `(speaker: String?, text: String?,
  timestampMillis: Long?)`. Serialised model input is one message per line, `Speaker: body` when a
  speaker exists, bare `body` when it does not. `validateModelInput` checks line count == cleaned
  message count.
- **Model: not integrated, correctly.** `PlaceholderGroomingModelAdapter.isReady = false` and
  `predict()` returns a shared `NotIntegrated("Model not integrated")` holder regardless of input —
  it cannot produce a label or a score even by accident. 3 tests green. `ModelPrediction.Scored`
  exists so the UI shape will not change when a real adapter is injected; nothing constructs it.
- **Label requirements:** no label vocabulary, sequence length, context window, or confidence scale
  is assumed anywhere. That is stated in the adapter's own header and is a constraint this task must
  not break.
- **Context-determination interface:** does not exist. There is no turn-windowing, no per-turn
  scoring, and no context graph. Recording it as absent rather than improvising one is the honest
  position.
- **`AuthorizedTextParser`** is the precedent to follow: it sets `timestampMillis = null`
  unconditionally, because pasted text carries no reliable time. It parses `Name: body` and refuses
  to invent a speaker for any other line.

### A.8 What docs/11 establishes, and what it does not

**Establishes** (8 observations, R1–R8): events are delivered for all three tested apps; an armed
one-shot inspection reliably obtains a non-null `rootInActiveWindow`; tree size, depth, widget-class
variety and label availability are measurable and stable in shape; and **a readable text surface
exists on conversation screens** — R2: 16 text nodes / 205 chars; R3: 18 / 245; R6: 8 / 135 — while a
non-chat window (R1) showed `withText = 0`, `chars = 0` with 11 `contentDescription` labels.

**Records, and this task inherits:**
- Text availability is a property of **the window**, not of the app. Reporting R1 alone would have
  published the opposite of the truth.
- `textBearing = withText + descOnly`, so `textBearing` **must never be quoted alone** — R1's 57% and
  R3's 54% look alike and mean opposite things.
- Tree **shape** is deterministic; **text content drifts** (R5 vs R7, same 46-node screen seven
  minutes apart: `withText` 4→5, `descOnly` 8→7, `chars` 70→74). A back-to-back repeat proves the
  traversal is deterministic, not that the screen is immutable.
- Two builds of the same product differ substantially in shape (R2 vs R3), which is why no
  generalisation from one row is allowed.
- **Limitations stated in the report itself:** it does *not* establish that complete conversations
  can be extracted, and it does *not* identify individual messages, speakers or timestamps. §1
  answers "Which nodes correspond to chat messages?" with **"No — not determinable from the tree"**.
  §10 documents that the harness refused to programmatically grant the accessibility permission;
  the human toggle is the consent gate, not an inconvenience.
- **Existing safeguards:** three-layer package bound (config XML `packageNames` → runtime
  `setServiceInfo()` narrowing → `armedTarget` equality), target captured at arm time, lazy root
  provider, node cap, `allowBackup="false"`, no `INTERNET`, no `<uses-permission>` at all,
  memory-only reports, cancel-equals-disarm, `forceDisarm()` on connect and disconnect.

### A.9 Known defects and gaps against the new objective

Numbered so each can get an acceptance criterion in A.10 and be closed (or left honestly open) in
later phases.

| # | Gap in the current code | Evidence | Why it blocks message-level extraction |
|---|---|---|---|
| G1 | `AccessibilityNodeView` exposes **no geometry and no interaction flags** — only text, description, class, child count, child. | [AccessibilityNodeView.kt:11-24](../app/src/main/java/com/example/groomingresearchdemo/inspection/AccessibilityNodeView.kt#L11) | §5 needs screen-relative bounds, clickability, scrollability, visibility; §6 rule 1 needs geometry to preserve top-to-bottom order. Without bounds there is no defensible ordering signal at all — BFS order is *traversal* order, not *reading* order. |
| G2 | The traversal emits **aggregates only**. There is no per-node record to classify. | `StructuralInspector.inspect()` returns `StructuralStats?` with 12 count/depth/map fields | Candidate identification is a per-node decision. Either a second pass or an extended output is required; the first must not be weakened (its redaction guarantee is tested). |
| G3 | **Text is discarded by design** the instant its length is measured. | [StructuralInspector.kt:105-109](../app/src/main/java/com/example/groomingresearchdemo/inspection/StructuralInspector.kt#L105) | §5 permits "a temporary in-memory text candidate only when needed", so retention must be added *and* bounded, cleared on finish/cancel, and kept out of stats/log/report — a deliberate change in posture, not a free one. |
| G4 | **No `seen` set**: BFS enqueues every child unconditionally. | [StructuralInspector.kt:67-72](../app/src/main/java/com/example/groomingresearchdemo/inspection/StructuralInspector.kt#L67) | Harmless for counts, fatal for message records: with `flagRetrieveInteractiveWindows` a node reachable twice is counted twice, and parent/child text duplication (§6 rule 3) is exactly the case that must be deduplicated. |
| G5 | **Parent link is never read**, so "container that aggregates descendant text" cannot be detected. | No `parent()` in the interface | A container whose `text` equals the concatenation of its children is the main false-positive source in chat UIs. |
| G6 | `truncated` is recorded but **does not change the outcome**. A 5000-node cap hit still yields `COMPLETED`. | [InspectionController.kt:107-121](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionController.kt#L107) | For counts that is tolerable; for transcripts it silently produces a *partial* sample presented as complete. §6 rule 9 requires the distinction. |
| G7 | **No speaker or timestamp concept exists anywhere in the accessibility path.** `ChatMessage` has the fields; nothing in `inspection` can fill them. | grep: `speaker` appears only under `model/` | §6 requires nullable, evidence-backed attribution and "never invent". Today there is no attribution mechanism at all — that is greenfield, not a fix. |
| G8 | **The five §7 states are not modelled.** `ExperimentOutcome` has 4 values (NONE/COMPLETED/FAILED/CANCELLED); the closest thing to a ladder is `eventReceived`/`packageMatched`/`rootAvailable`. | [Enums.kt:15](../app/src/main/java/com/example/groomingresearchdemo/inspection/Enums.kt#L15) | "Inspection completed", "Candidates detected", "Structure reconstructed", "Speaker attribution validated", "Complete conversation validated" must be five *independently gated* displays. One Boolean cannot serve them. |
| G9 | **No adapter from message records to preprocessing input.** `ConversationPreprocessor` accepts `List<ChatMessage>`; the inspection path produces `StructuralStats`. | — | §9's handoff does not exist yet. |
| G10 | **`CompatibilityReportBuilder` has no candidate-level section**, and docs/11 §6 must keep recording counts only. | read in full | New output must not smuggle text into the compatibility report or overwrite the existing report. |
| G11 | **Screens are not labelled.** R1, R4–R8 have no confirmed screen type; RC-01/RC-02 remain unlabelled and RC-04 has never fired (`root=true` in all 8). | docs/11 §6, RC table | §8: "Do not claim general compatibility from a handful of observations." Three apps and eight windows is a handful. |
| G12 | **No ground truth exists.** docs/11 §9 defines the transcription method and states that until it exists no coverage percentage may be quoted. | docs/11 §9 | Every §8 metric on *real* apps is therefore `NOT MEASURED` until a consented, manually annotated sample is produced. Synthetic metrics are computable; real-app metrics are not, yet. |
| G13 | Test infrastructure is **JUnit 4 only, JVM only, no Robolectric**, and `FakeNode` supports no bounds/flags/parent. | [build.gradle.kts:71](../app/build.gradle.kts#L71), [FakeNode.kt](../app/src/test/java/com/example/groomingresearchdemo/inspection/FakeNode.kt) | Good for the classifier (must stay Android-free), but `FakeNode` must be extended before any of the 14 §8 cases involving geometry, duplication or mid-inspection change can be written. Adding dependencies is not required and will be avoided. |
| G14 | No defect found in the privacy controls. | A.6 | §4 asks that any existing violation be documented and minimally corrected. **None was found**, so no corrective change is made — and none should be introduced under the banner of "extraction". |

### A.10 Planned changes and acceptance criteria (Phase B design)

**Design principle: add a stage, do not replace one.** The structural pass stays exactly as it is —
it is the tested compatibility evidence and the reason R1–R8 are defensible. Message-level
extraction becomes a **second, separately armed, separately reported, memory-only stage** that
consumes the same node graph. That keeps `nodeTextNeverAppearsInTheReport()` meaningful and keeps
docs/11 intact.

| # | Planned change | Files | Acceptance criterion | Closes |
|---|---|---|---|---|
| P1 | Extend `AccessibilityNodeView` with structural metadata: `boundsInScreen()` (as an Android-free rect value type), `isClickable()`, `isScrollable()`, `isVisibleToUser()`, `depth`-carrying traversal context, `parent()`. Implement each in `RealAccessibilityNodeView` behind `runCatching`; each must degrade to "unavailable", never throw. Still **no** `viewIdResourceName` and **no** package metadata. | `inspection/AccessibilityNodeView.kt`, `service/RealAccessibilityNodeView.kt`, `inspection/StructuralInspector.kt` | Existing 57 tests still green. A node with no bounds returns a sentinel, not a crash. `FakeNode` gains the same surface. | G1, G5 |
| P2 | Add a **seen-set keyed on node identity** so a node reached twice is processed once; record `duplicateVisits` as a diagnostic count. | `inspection/StructuralInspector.kt` | A tree with a shared child yields `nodesVisited` equal to distinct nodes, and the duplicate count is visible. | G4 |
| P3 | Add an **in-memory candidate stage**: `inspection/candidate/` with a transparent, rule-based `CandidateClassifier` (pure Kotlin, no Android imports) that emits `CandidateNodeRecord` carrying the §5 metadata plus a temporary text candidate, and a `NonMessageRules` set covering navigation labels, search controls, text-entry fields, send buttons, menus/dialogs, headers/profile labels, loading indicators and announcements. | new `inspection/candidate/*.kt` | Classifier is deterministic and unit-testable on synthetic fixtures alone. Every exclusion is a named rule with a stated reason, not a hard-coded view ID. | G2, G3 |
| P4 | Add `MessageReconstructor` (pure Kotlin): ordered dedup, parent-aggregate suppression, geometry-based top-to-bottom ordering with explicit "order not established" fallback, and `MessageRecord(candidateId, text, speakerLabel?, timestamp?, bounds?, confidence, evidence, status)`. **No alignment-based speaker inference** unless implemented as an explicitly labelled, separately evaluated heuristic; timestamps only when a node is positively identified as one. | new `inspection/reconstruct/*.kt` | §6 rules 1-10 each map to at least one test. Uncertain candidates land in `AMBIGUOUS`, never in an accepted transcript. Boundaries not determinable ⇒ `INCOMPLETE`/`UNSUPPORTED`, and guessed text is never presented as a conversation. | G6, G7 |
| P5 | Model the five levels as **independently gated** values, not a Boolean: `InspectionLevel { NOT_RUN, COMPLETED, CANDIDATES_DETECTED, STRUCTURE_RECONSTRUCTED, SPEAKER_ATTRIBUTION_VALIDATED, COMPLETE_CONVERSATION_VALIDATED }`, each derived from its own criterion and each displayed only when met. | `inspection/Enums.kt`, `inspection/InspectionResult.kt` | Test: a run that finds candidates but cannot bound messages shows level 2 and *not* level 3. Level 5 is unreachable without validated complete-sample evidence. | G8 |
| P6 | Retention policy: candidate text lives in a memory-only holder cleared when the inspection finishes or is cancelled, never entering `StructuralStats`, `logResult()`, `CompatibilityReportBuilder`, preferences, or files. Add a test asserting the marker string appears in **no** loggable or reportable artefact while the *counts* do. | `state/ResearchStore.kt`, `report/CompatibilityReportBuilder.kt`, new test | The existing redaction test passes unchanged; a new test covers the candidate stage; grep proves no new persistence key. | G3, G10 |
| P7 | Extend the results screen with the §7 count set and the five-state ladder; keep the default view a **structural summary**. Sample text, if shown at all, requires a separate explicit temporary review action carrying a privacy notice and offering no save or export. | `ui/screens/ResultsScreen.kt`, `ui/RootScreen.kt` | Text is not rendered unless the researcher takes the extra deliberate action; leaving the screen drops it. | G8, G10 |
| P8 | `ValidatedRecordAdapter`: `MessageRecord` → `List<ChatMessage>` for `ConversationPreprocessor`. Preserve order; carry `speakerLabel` only when reliably identified; represent unknowns as `null` (never `"unknown"`, never a placeholder name, never a synthetic timestamp). **Refuse** to emit model input for records whose status is not accepted. | new `inspection/harness/ValidatedRecordAdapter.kt` | Adapter tests: unknown speaker ⇒ `ChatMessage(speaker = null …)`; unknown timestamp ⇒ `null`; ambiguous-only input ⇒ no model input produced. `PlaceholderGroomingModelAdapter` still returns `NotIntegrated`, so nothing is labelled grooming by an accessibility heuristic. | G9 |
| P9 | Validation harness + 14 named synthetic fixtures with expected annotations, and a metric definitions table with explicit denominators. | new `src/test/.../extraction/*.kt` | All 14 cases covered; every metric has a written definition and denominator; unmeasured metrics report `NOT MEASURED`, distinct from a genuine zero. | G12 |
| P10 | Device manual-test checklist for authorised, user-triggered visible-screen inspection. **No automated device run is claimed.** | docs/12, docs/13 | Phase F records the blocker if the human toggle or an annotated sample is absent, rather than simulating success. | G11, G12 |

**Explicitly out of scope**, so the panel is not misled: no scrolling, no history navigation, no
clicking, no auto-retry, no background monitoring, no notification/clipboard/keystroke collection,
no widening of the three-layer package bound, no added permission, no network, no model training,
and no coverage percentage until a consented annotated sample exists.

---

## Phase B — Design outcome

Phase B was decided by one question from the audit: *what is the cheapest change that adds
message-level extraction without touching the evidence R1–R8 already rest on?* The answer drove the
whole design.

### B.1 The decision that shaped everything else: add a stage, do not edit one

`StructuralInspector.kt` was left **byte-identical**. Its timestamp in the working tree is still
2026-10-19 23:34, the moment before this task began. Three reasons, in order of weight:

1. It is the code that produced R1–R8. If the traversal that produced a number changes, the number
   stops meaning what the report says it means, and docs/11 becomes a description of code that no
   longer exists.
2. Its redaction guarantee is *tested* (`nodeTextNeverAppearsInTheReport()`), and that test is only
   interesting while the structural stage genuinely never reads text into a retained value.
3. The compatibility question ("how much of this screen is exposed?") and the extraction question
   ("which node sits where, relative to which?") need different outputs. Forcing one pass to answer
   both would have meant either weakening the counts or over-loading the classifier.

So the extraction stage is a **second pass over the same node graph**, separately opted into,
separately reported, memory-only. It reads the identical `rootProvider()` snapshot the structural
stage read — the tree is opened exactly once per inspection either way, so this adds no new access,
only new interpretation.

### B.2 Where the new capability had to be injected

The audit showed the only structural surface available to the extraction layer was the
`AccessibilityNodeView` interface, and it carried five members, none of them geometric. Two designs
were possible:

| Option | Shape | Rejected / chosen because |
|---|---|---|
| Wrap nodes in a parallel "rich node" type used only by extraction | New type alongside the old, extraction reads geometry, structural path keeps reading the five members | Rejected: two node abstractions means the structural pass could be reached by the new code by accident, and every defensive read would need writing twice. |
| **Grow the interface with defaulted members** | `boundsInScreen()`, `isClickable()`, `isScrollable()`, `isVisibleToUser()`, `parent()`, `nodeId()`, `aggregatesDescendantText()` all arrive with **defaults**, so existing implementers and the structural path compile and behave exactly as before | **Chosen.** A default is the only way to add a member without forcing `StructuralInspector` and every existing fake to change. It also encodes the required defensive posture: a property the platform refuses reads as "unavailable", never as a throw. |

Consequence worth stating plainly: `StructuralInspector` still does not call any of the new members,
so the new surface is inert for it. That is how the R1–R8 comparability is guaranteed structurally,
not by promise.

### B.3 The node-identity problem, decided before coding

Double-visit suppression (§5 "duplicate traversal") needs an id. `javap` against
`platforms/android-36/android.jar` confirmed **`getSourceNodeId()` is not public**. So:

- Using the framework's per-tree id would need a hidden API ⇒ **forbidden by §4**. Not done.
- The interface therefore exposes `nodeId()` returning `Long?` with a `null` default, and the
  traverser falls back to **wrapper object identity** in a `IdentityHashMap` when it is null.
- On a real device `nodeId()` is always null, so dedup there runs on wrapper identity. That is a
  genuine limitation, recorded in Phase F rather than hidden, because a wrapper identity is only
  stable within one traversal — which is exactly the window in which dedup matters.

### B.4 Ordering: geometry first, traversal last, "unestablished" always available

§6 rule 1 makes visible top-to-bottom order the authority, and rule 2 permits a structural fallback
only when it is demonstrably meaningful. The design therefore has three named bases, not two
(`OrderBasis` in [Reconstruction.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/extraction/Reconstruction.kt)):
`GEOMETRY` when every candidate is placed, `NOT_APPLICABLE` when there is nothing to order, and
`TRAVERSAL_UNVERIFIED` when a candidate could not be placed. The third is the honest one: BFS order
is *enumeration* order, so when it is used the reconstruction is capped at `INCOMPLETE` and no record
is promoted to `ACCEPTED`. Making that state reachable turned out to require a design fix during
implementation — see C.4.

### B.5 The five levels are five gates

§7 forbids treating "inspection completed", "candidates detected", "structure reconstructed",
"speaker attribution validated" and "complete conversation validated" as one success value. The
planned `InspectionLevel` **ordinal enum was dropped in favour of a data class with five Booleans**
(`InspectionLadder`): an enum with a highest-satisfied level is precisely the collapse the spec warns
about, because a single `level >= 3` check in the UI lets level 1 satisfy level 5's display slot by
arithmetic. Each field has its own criterion, and each unmet field carries its own human-readable
reason. Levels 4 and 5 additionally require evidence the app cannot generate — an annotated sample,
a researcher's attestation — so in an unattended run they read "not met", which is the correct answer.

### B.6 Retention posture, deliberately reversed and deliberately bounded

G3 said text is discarded the instant its length is measured. §5 permits "a temporary in-memory text
candidate only when needed". The design keeps that permission narrow and mechanical:

- exactly **one** reconstruction is retained at a time, in memory, in `ResearchStore`;
- it never enters `StructuralStats`, `logResult()`, `CompatibilityReportBuilder`, preferences, files
  or the database — there is no new persistence key;
- leaving the review surface calls `forgetExtractedContent()`;
- `toString()` is hand-written on **every** content-carrying type, because a generated `data class`
  `toString()` is the quietest way to put a message body into a crash log (§4 forbids that by name).

### B.7 What was excluded at design time

No scrolling, no history navigation, no clicking, no auto-retry, no background monitoring, no
notification/clipboard/keystroke collection, no `viewIdResourceName`, no package/install metadata in
the candidate layer, no widening of the three-layer package bound, no added permission, no network,
no model training, no coverage percentage before an annotated sample exists. Alignment-based speaker
inference was **not removed but fenced**: it is opt-in, off by default, and its own output is
labelled `ALIGNMENT_HEURISTIC` so it can never be mistaken for evidence.

---

## Phase C — Implementation

Eight stages, each compiled and tested before the next was started. Every "verified" line below is a
command that was executed and an output that was read.

### C.1 Node surface extension — closes G1, G5

**Original behaviour.** `AccessibilityNodeView` exposed `text()`, `contentDescription()`,
`className()`, `childCount()`, `getChild(index)`. Nothing geometric, no parent, no interaction flags.
`RealAccessibilityNodeView` wrapped an `AccessibilityNodeInfo` and read those five.

**Change made.** Seven defaulted members added to the interface, each implemented in
`RealAccessibilityNodeView` inside `runCatching` so a refused read degrades to "unavailable":
`boundsInScreen(): NodeBounds?`, `isClickable()`, `isScrollable()`, `isVisibleToUser(): Boolean?`,
`parent()`, `nodeId(): Long?`, `aggregatesDescendantText()` (default implementation, bounded to
`MAX_INSPECTED_CHILDREN = 64` children). New Android-free value type `NodeBounds` with `isValid`,
`centerY`, `isClippedBy(viewport)` and `verticalGap(upper, lower)`.

**Files.** [AccessibilityNodeView.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/AccessibilityNodeView.kt)
(modified), [NodeBounds.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/NodeBounds.kt)
(new), [RealAccessibilityNodeView.kt](../app/src/main/java/com/example/groomingresearchdemo/service/RealAccessibilityNodeView.kt)
(modified).

**Why.** Without bounds there is no defensible ordering signal at all, and without `parent()` the
container-echo false positive — the dominant one in chat UIs — cannot be detected. `isVisibleToUser()`
is nullable on purpose: "unknown" and "invisible" lead to different decisions and must not be
collapsible.

**Not done, on purpose.** No `viewIdResourceName`, no package metadata, no `recycle()` call. The
KDoc in `RealAccessibilityNodeView` records the reason: `recycle()` is deprecated at API 33 and a
no-op after 34, and node references are never retained past the traversal in the first place.

**Verified.** `:app:compileDebugKotlin` clean; the pre-existing `StructuralInspectorTest` 13 tests
still green with the interface wider than when they were written.

### C.2 Second-pass traverser with double-visit suppression — closes G4

**Original behaviour.** None — this class did not exist. `StructuralInspector`'s BFS enqueues every
child unconditionally, which is harmless for counts and fatal for per-node records.

**Change made.** `MessageLevelTraverser` walks the same tree independently and emits
`List<NodeSample>`: a seen-set keyed on `nodeId()` with an `IdentityHashMap` fallback, `duplicatesSuppressed`
and `nodesLostDuringTraversal` diagnostics, the same `MAX_TRAVERSAL_NODES = 5000` cap, and a viewport
carried out for clipping analysis.

**Files.** [MessageLevelTraverser.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/MessageLevelTraverser.kt)
(new), [candidate/NodeSample.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/candidate/NodeSample.kt)
(new).

**Why a second traversal instead of extending the first.** `StructuralInspector`'s output is the
compatibility evidence; changing its output type would have invalidated every recorded R-row. The
extraction stage re-reads the same snapshot, so the tree itself is still opened once.

**Defensive rules encoded here.**
- **One read per property.** `text`, `hasText` and `contentDescription` all derive from a *single*
  framework read (`rawText`, `rawDescription`). The framework is allowed to hand back a different
  value on the next call; two reads could produce a sample that reports "text present" while
  carrying none. Pinned by `textAndItsPresenceFlagComeFromTheSameRead`.
- A `childCount()` that throws counts as a **lost subtree**, not as a leaf. Claiming "this node had no
  children" because the platform refused to answer would be inventing a negative.
- A node that declares *n* children and yields *m < n* also counts as lost structure, including the
  case where it declares children and yields none at all — the live signature of a tree mutating
  underneath the traversal.
- `truncated` is carried forward and forces the sample to be labelled partial (G6), rather than being
  recorded and then ignored as the structural stage does.

### C.3 Rule-based candidate classifier — closes G2, G3

**Original behaviour.** Text was measured for length and dropped. No per-node record existed.

**Change made.** `inspection/candidate/CandidateClassifier.kt`: pure Kotlin, zero Android imports,
deterministic. Emits `CandidateJudgement(decision, strength, evidence, objection)` over the §5
metadata plus a temporary text candidate. Named exclusion rules cover navigation labels, search
controls, text-entry fields, send buttons, menus/dialogs, headers/profile labels, loading indicators
and accessibility announcements; receipt/announcement shapes and a chrome-phrase list are matched by
*shape*, and an unrecognised class name (`com.shrug.bubble.CustomMessageText`) is judged by leaf +
text-shaped evidence rather than by a blocklist, so no app-specific assumption is baked in.

**Files.** [candidate/CandidateClassifier.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/candidate/CandidateClassifier.kt)
(new).

**Why transparent.** §5 requires the classifier be understood before sophisticated reconstruction, and
§6 requires every exclusion be explainable. Each exclusion returns a *reason string*, so the results
screen can say why a node was dropped without showing what it said.

### C.4 The layering bug the tests caught, and the fix

Writing the classifier tests exposed a design flaw, not a coding mistake: `EvidenceStrength.LOW`
forced `CandidateDecision.AMBIGUOUS`, which made `OrderBasis.TRAVERSAL_UNVERIFIED` and
`MessageStatus.CANDIDATE` **unreachable** — i.e. two of the states §6 and §7 require could never be
displayed, and the honest "we cannot order this" outcome was impossible to produce.

**Root cause.** Placement was being decided inside the classifier, where it does not belong. Missing
geometry is an *ordering* problem, not an *identity* problem.

**Fix.** The classifier now separates two axes:
- `role-unresolved` (a node whose role is genuinely unknown) → `AMBIGUOUS`;
- `geometry-missing` (a plausible message we cannot place) → stays `CANDIDATE` at `LOW` strength.

Safety is preserved downstream and not by the classifier: a geometry-less candidate **cannot reach
`ACCEPTED`**, because `MessageReconstructor` will not accept a record it cannot order. The rule moved
from "weaken the evidence label" to "refuse the verdict", which is where §6 rule 10 puts it. Two
tests were rewritten to pin the corrected behaviour
(`missingGeometryIsKeptAsACandidateButCannotRaiseConfidence`,
`anUnknownClassWithNoRoleSignalStaysAmbiguous`).

### C.5 Message reconstruction — closes G6, G7

**Change made.** `MessageReconstructor` (pure Kotlin) implements §6 rules 1–10 over the judgement
stream: geometry-based top-to-bottom ordering with row-band grouping, ordered dedup, parent-aggregate
suppression (the container is dropped, the child survives), repeated identical text kept as separate
records, multi-line text kept whole, no merging of adjacent nodes without structural evidence, no
splitting on punctuation or line breaks, nullable `speakerLabel` and `timestamp` filled only from
positive evidence, `candidateId` synthesised as `"i42-0001"` (never derived from a framework id), and
status landing in `UNSUPPORTED` / `INCOMPLETE` / `RECONSTRUCTED`.

**Files.** [extraction/MessageReconstructor.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/extraction/MessageReconstructor.kt),
[extraction/Reconstruction.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/extraction/Reconstruction.kt),
[extraction/MessageLevelExtractor.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/extraction/MessageLevelExtractor.kt)
(all new).

**Nothing is invented.** A timestamp requires a node positively identified as one; a sender label
requires either node-label evidence (opt-in rule `S1`, output labelled
`NODE_LABEL_EVIDENCE`) or the explicitly fenced `ALIGNMENT_HEURISTIC`; unknown boundaries produce
`INCOMPLETE` with reasons, never a guessed transcript. Every unmet condition appends a bounded,
text-free reason string.

**`MessageLevelExtractor` is `open`.** Not for production polymorphism: it is the only way to inject a
stage that throws, which is how the controller's `runCatching` around it gets proven to record a
reason instead of losing the inspection.

### C.6 Five independent gates — closes G8

`InspectionLadder.of(result, attributionValidatedAgainstGroundTruth, completeSampleAttestedByResearcher)`
derives the five Booleans from their own criteria plus the two pieces of evidence the app cannot
produce, and `unmetReasons` explains each "no". Placed in `inspection/extraction/`, **not** as the
`InspectionLevel` enum that A.10 P5 proposed — see the deviation note in C.9.

**Files.** [extraction/InspectionLadder.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/extraction/InspectionLadder.kt)
(new). `Enums.kt` untouched.

### C.7 Retention, redaction and the UI boundary — closes G3, G10

- `InspectionResult` gains an `extraction: Reconstruction?` field; `Reconstruction.withoutRecords()`
  keeps every count and both status flags while dropping all text, which is what the session-report
  path uses.
- `ResearchStore` holds exactly one reconstruction, forgets it when the arm changes, on revoke, and on
  leaving the review surface.
- Hand-written `toString()` on `Traversal`, `NodeSample`, `CandidateJudgement`, `MessageRecord` and
  `Reconstruction`, each reporting `textPresent=…` instead of the text.
- `ResultsScreen` shows the §7 count set, the reading-order basis, the yes/no attribution lines, the
  partial-sample warning and the five-row ladder — **counts only**. Content appears on a new
  `ReviewScreen`, reached by a second named action, with a privacy notice, no export/share/clipboard
  path, and `forgetExtractedContent()` on both exits.
- `ChatInspectionService` and `CompatibilityReportBuilder` were **not modified at all** (timestamps
  still 2026-10-19 23:36 / 23:35). The report can still not carry text, because nothing new writes to
  it.

**Files.** [InspectionResult.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionResult.kt),
[InspectionController.kt](../app/src/main/java/com/example/groomingresearchdemo/inspection/InspectionController.kt),
[state/ResearchStore.kt](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchStore.kt),
[state/ResearchUiState.kt](../app/src/main/java/com/example/groomingresearchdemo/state/ResearchUiState.kt),
[ui/RootScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/RootScreen.kt),
[ui/screens/ExperimentScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ExperimentScreen.kt),
[ui/screens/ResultsScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ResultsScreen.kt)
(all modified), [ui/screens/ReviewScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ReviewScreen.kt)
(new).

**Controller specifics.** `arm(consentAccepted, serviceEnabled, targetPackage, messageLevelExtraction)`
captures the extraction choice at **arm time**, in `armedExtraction`, for the same reason
`armedTarget` exists: un-checking the box afterwards must not silently widen or narrow an inspection
the researcher already consented to. `cancel()` disarms and clears `armedExtraction`; `forceDisarm()`
and `revokeAndClear()` do too.

### C.8 Model handoff — closes G9

`model/ValidatedRecordAdapter.kt` turns accepted records into `List<ChatMessage>` for the existing
`ConversationPreprocessor`: ordered by `visibleOrderIndex` (not record order), only `ACCEPTED` and
non-blank text crosses, unknown speaker and unknown timestamp cross as `null`, `INCOMPLETE` and
`UNSUPPORTED` input is **refused** (`validation == null`, no model input produced), and the output
carries no judgement. `PlaceholderGroomingModelAdapter` still returns `NotIntegrated`, so nothing in
this build labels a conversation as grooming.

**Verified by test, not by assertion:** `ValidatedRecordAdapterTest` (9) includes
`theAdapterNeverProducesAGroomingJudgement` and checks the placeholder adapter's `isReady == false`.

### C.9 Deviations from the A.10 plan, recorded rather than smoothed over

| Plan | Built | Why it changed |
|---|---|---|
| P5: `InspectionLevel` enum in `Enums.kt` | `InspectionLadder` data class in `inspection/extraction/` | An ordinal enum invites a `level >= n` UI check, which re-collapses the five claims §7 insists stay separate. Five Booleans with per-field reasons cannot be compared that way. |
| P8: `inspection/harness/ValidatedRecordAdapter.kt` | `model/ValidatedRecordAdapter.kt` | The adapter's contract is with the model boundary (`ChatMessage`, `ConversationPreprocessor`), not with inspection. Keeping it in `model/` means the only file that knows both sides sits on the side that already owns the privacy-critical preprocessing tests. |
| P1: "`nodeId()` … implement in `RealAccessibilityNodeView`" | Interface member exists; **no device implementation** | `getSourceNodeId()` is not public (B.3). Implementing it would need a hidden API. The traverser's identity-map fallback is the legal substitute, and the limitation is recorded in Phase F. |
| P3: "a `NonMessageRules` set" | Rules inlined as named branches inside `CandidateClassifier` | A separate class for eight phrase/shape checks would have added an indirection with no test surface. Each rule still returns its own reason string, which is what the acceptance criterion actually asked for. |
| P1: file list included `inspection/StructuralInspector.kt` | **`StructuralInspector.kt` not touched at all** (mtime still 2026-10-19 23:34) | The plan listed it because widening the node surface looked as though it might require the first pass to carry depth or geometry. It did not: the new interface members have defaults, are inert unless called, and the structural path calls none of them. Leaving it byte-identical is what keeps R1–R8 the same measurement, and `theStructuralStageRunsIdenticallyWhetherOrNotExtractionIsArmed` is the test that says so. |
| P6, P7, P9, P10 | as planned | — |

### C.10 Files added and modified

**Added (10 main, 9 test):** `inspection/NodeBounds.kt`, `inspection/MessageLevelTraverser.kt`,
`inspection/candidate/NodeSample.kt`, `inspection/candidate/CandidateClassifier.kt`,
`inspection/extraction/MessageLevelExtractor.kt`, `inspection/extraction/MessageReconstructor.kt`,
`inspection/extraction/Reconstruction.kt`, `inspection/extraction/InspectionLadder.kt`,
`model/ValidatedRecordAdapter.kt`, `ui/screens/ReviewScreen.kt`;
`test/…/inspection/MessageLevelTraverserTest.kt`, `test/…/inspection/InspectionControllerExtractionTest.kt`,
`test/…/inspection/candidate/CandidateClassifierTest.kt`,
`test/…/inspection/extraction/{MessageReconstructorTest,SyntheticConversationFixtureTest,ExtractionMetricsTest,ExtractionPrivacyTest,InspectionLadderTest}.kt`,
`test/…/model/ValidatedRecordAdapterTest.kt`.

**Modified (10):** `inspection/AccessibilityNodeView.kt`, `inspection/InspectionController.kt`,
`inspection/InspectionResult.kt`, `service/RealAccessibilityNodeView.kt`, `state/ResearchStore.kt`,
`state/ResearchUiState.kt`, `ui/RootScreen.kt`, `ui/screens/ExperimentScreen.kt`,
`ui/screens/ResultsScreen.kt`, `test/…/inspection/FakeNode.kt` (gained bounds/flags/parent/node-id
support and the throwing shapes).

**Deliberately untouched:** `inspection/StructuralInspector.kt`, `inspection/Enums.kt`,
`report/CompatibilityReportBuilder.kt`, `service/ChatInspectionService.kt`, `service/LogUtils.kt`,
`model/ConversationPreprocessor.kt`, `model/GroomingModelAdapter.kt`, `model/AuthorizedTextParser.kt`,
`app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/accessibility_service_config.xml`,
`app/build.gradle.kts`, `docs/11_REAL_APP_COMPATIBILITY_REPORT.md`. No dependency was added; the
manifest still declares **zero `<uses-permission>`**, no `INTERNET`, `allowBackup="false"`; the
accessibility config still requests only
`typeWindowStateChanged|typeViewFocused` with `flagRetrieveInteractiveWindows` over the same
six-package allowlist.

## Phase D — Unit test results

### Command executed

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat --offline --rerun-tasks :app:testDebugUnitTest > phd_final.log 2>&1; echo "EXIT=$?"
```

### Actual output read

```
EXIT=0
BUILD SUCCESSFUL in 11s
24 actionable tasks: 24 executed
```

`grep -c "^e:\|^w:" phd_final.log` → **0**. No compile errors and no warnings, including no
"Condition is always true" — those were fixed during C by introducing a `reconstructed` local instead
of re-testing `rootAvailable`. The log is archived as
[`evidence/phaseD_test_run_2026-10-20_175006.log`](evidence/phaseD_test_run_2026-10-20_175006.log).

`--rerun-tasks` is mandatory here, not stylistic: without it Gradle reports `UP-TO-DATE`/`FROM-CACHE`
and the green line describes a previous build, not this one. Totals below were read from the JUnit
XML in `app/build/test-results/testDebugUnitTest/`, not inferred from the task line.

### Totals — 156 tests / 0 failures / 0 errors / 0 skipped

Regression baseline going in was **57 / 0 / 0 / 0**. All 57 original tests are present and still
passing; nothing was deleted, renamed away, or converted to a no-op.

| Test class | tests | failures | errors | skipped | Nature |
|---|---|---|---|---|---|
| `inspection/StructuralInspectorTest` | 13 | 0 | 0 | 0 | pre-existing, unchanged |
| `inspection/InspectionControllerTest` | 15 | 0 | 0 | 0 | pre-existing, unchanged |
| `report/CompatibilityReportTest` | 4 | 0 | 0 | 0 | pre-existing, unchanged |
| `model/ConversationPreprocessorTest` | 13 | 0 | 0 | 0 | pre-existing, unchanged |
| `model/AuthorizedTextParserTest` | 9 | 0 | 0 | 0 | pre-existing, unchanged |
| `model/PlaceholderGroomingModelAdapterTest` | 3 | 0 | 0 | 0 | pre-existing, unchanged |
| `inspection/MessageLevelTraverserTest` | 11 | 0 | 0 | 0 | new |
| `inspection/candidate/CandidateClassifierTest` | 16 | 0 | 0 | 0 | new |
| `inspection/extraction/MessageReconstructorTest` | 15 | 0 | 0 | 0 | new |
| `inspection/extraction/SyntheticConversationFixtureTest` | 14 | 0 | 0 | 0 | new (the §8 fourteen) |
| `inspection/extraction/ExtractionMetricsTest` | 11 | 0 | 0 | 0 | new |
| `inspection/extraction/ExtractionPrivacyTest` | 6 | 0 | 0 | 0 | new |
| `inspection/extraction/InspectionLadderTest` | 9 | 0 | 0 | 0 | new |
| `inspection/InspectionControllerExtractionTest` | 8 | 0 | 0 | 0 | new |
| `model/ValidatedRecordAdapterTest` | 9 | 0 | 0 | 0 | new |
| **Total** | **156** | **0** | **0** | **0** | |

### The fourteen §8 synthetic fixtures, as named tests

All fourteen run end-to-end through `MessageLevelExtractor` over hand-built `FakeNode` trees. No real
conversation appears in any fixture; every string is synthetic and marked `synthetic`.

| # | §8 case | Test |
|---|---|---|
| F1 | two-speaker conversation | `f1_twoSpeakerConversationBecomesAnOrderedRecordSetWithNoSendersAttached` |
| F2 | missing speaker labels | `f2_missingSpeakerLabelsProduceAReconstructionThatSaysItHasNoSenders` |
| F3 | no timestamps | `f3_absentTimestampsStayAbsentRatherThanBecomingReconstructionOrder` |
| F4 | nested containers duplicating text | `f4_nestedContainersEchoingOneBubbleYieldOneRecordNotTwo` (two echoing wrappers, `containerEchoesSuppressed == 2`, one record each) |
| F5 | many interface labels, no messages | `f5_aScreenOfChromeIsUnsupportedRatherThanAnEmptyConversation` |
| F6 | multi-line messages | `f6_aMultiLineBubbleIsOneRecordAndKeepsItsLineBreaks` |
| F7 | repeated identical text | `f7_repeatedIdenticalTextStaysTwoRecords` |
| F8 | reversed / ambiguous ordering | `f8_backwardsEnumerationIsReorderedByGeometryNotTrustedAsTraversalOrder` |
| F9 | empty and null text | `f9_emptyNullAndBlankTextAreRejectedWithoutEndingTheRun` |
| F10 | tree changing during inspection | `f10_aTreeThatMutatesMidTraversalIsReportedAsUnreliable` |
| F11 | partially visible conversation | `f11_aConversationRunningOffScreenIsLabelledPartial` |
| F12 | boundaries cannot be established | `f12_ambiguousBoundariesAreKeptAndBlockAReconstructedVerdict` (3 ambiguous, `INCOMPLETE`) |
| F13 | unsupported app / empty tree | `f13_anEmptyTreeAndANullRootAreBothUnsupportedNotSilentlyEmpty` |
| F14 | cancellation during inspection | `f14_cancellingTheArmMeansTheExtractionStageNeverRuns` (through `InspectionController`: `armedExtraction` true→false, `handleEvent` returns null, `completedCount == 0`, re-arm then extracts) |

### What the privacy suite proves

`ExtractionPrivacyTest` (6) plants `PRIVATE-MESSAGE-MARKER-4b7b` in a fixture and asserts, in
separate tests, that it reaches the in-memory records (so the run is real and not vacuously clean),
then that it appears in **no** compatibility report, **no** `toString()` of `Traversal`, `NodeSample`,
`CandidateJudgement`, `MessageRecord` or `Reconstruction`, **no** reason or evidence string, and not
in a `candidateId`. `textPresent=true` is still reported, and `withoutRecords()` keeps every count and
status. `CandidateClassifierTest` repeats the marker check at the classifier boundary.

### Not executed

No instrumented, Robolectric or on-device test was run. There is none in the project and no
dependency for one was added (G13 closed by keeping the classifier and reconstructor Android-free,
which is what lets all 156 tests — including the 99 new ones — run on the JVM in 11 seconds with no
emulator attached).

## Phase E — Build results

### Command executed

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat --offline --rerun-tasks :app:assembleDebug > phe_final.log 2>&1; echo "EXIT=$?"
```

### Actual output read

```
EXIT=0
BUILD SUCCESSFUL in 9s
37 actionable tasks: 37 executed
-rw-r--r-- 1 Chandana 197609 12114117 Oct 20 17:50 app/build/outputs/apk/debug/app-debug.apk
```

**APK: 12,114,117 bytes.** Against the Phase A baseline recorded in this document — 12,032,138 bytes
(2026-10-20 14:41) — that is **+81,979 bytes** for ten new main-source classes, two new UI surfaces'
content and the widened interface. No dependency changed; the size delta is this task's code.
Logs archived as [`evidence/phaseE_build_run_2026-10-20_175027.log`](evidence/phaseE_build_run_2026-10-20_175027.log).
The 12,032,138-byte comparison figure is the one recorded at audit time in §A.3 above — it came from
an earlier session's build and was **not** re-measured here, so the delta is exact only to the extent
that audit-time measurement was. The Phase A test run that established the 57-test baseline is
archived as [`evidence/phaseA_baseline_run.txt`](evidence/phaseA_baseline_run.txt).

Debug build, unsigned-but-installable default debug keystore, `minSdk 24`, `compileSdk`/`targetSdk 36`,
JVM 17, Kotlin 2.2.21, AGP 8.13.2, Gradle 8.14.6.

### A trap worth recording

Two earlier Phase C attempts appeared to fail because a **stale log was re-read as current**:
`phasec-compile.log` and `build-probe.log` still contained a cancelled run
(`Gradle build was cancelled`, `ProcessException error 2 CreatePipe failed`, `BUILD FAILED`) after the
tasks had actually succeeded. Both the test totals and the APK size above were therefore taken from
`--rerun-tasks` output written immediately before reading, and cross-checked against the JUnit XML
timestamps and the APK `mtime`. A build or test claim that cannot be tied to a freshly written log is
not a claim.

## Phase F — Real-device validation

### What was actually done on the device

Emulator `emulator-5554` (`Pixel_API35_Play`, API 35), adb at
`$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe`:

```
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk   → Performing Streamed Install / Success
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n com.example.groomingresearchdemo/.MainActivity
adb -s emulator-5554 shell dumpsys activity activities   → * Task{… com.example.groomingresearchdemo … visible=true visibleRequested=true}
                                                              topResumedActivity=ActivityRecord{… .MainActivity t37}
adb -s emulator-5554 shell pidof com.example.groomingresearchdemo  → 13377
adb -s emulator-5554 logcat -d                            → 740 lines captured
grep -c "FATAL EXCEPTION\|AndroidRuntime"                 → 0
adb -s emulator-5554 shell settings get secure enabled_accessibility_services
  → com.example.groomingresearchdemo/com.example.groomingresearchdemo.service.ChatInspectionService
```

The only lines the app itself wrote after that cold start:

```
I GroomingResearchDemo: ResearchStore initialised. consent=ACCEPTED target=com.google.android.apps.messaging serviceEnabled=true
I GroomingResearchDemo: ChatInspectionService connected (target=com.google.android.apps.messaging).
I GroomingResearchDemo: Accessibility service CONNECTED. Arm forced to NOT_ARMED.
```

**What this establishes.** The widened interface and the new extraction stage **load and run on
Android 15 without an exception**; the service still connects and still narrows to the selected
target; the arm is `NOT_ARMED` on connect, i.e. installing a build that *can* extract text does not
cause it to read anything. The logged lines are status and package names — the app's own output
contains no node text, consistent with §4.

**What this does not establish.** Nothing about **accuracy**. Two extraction runs have since completed
against real Instagram (recorded below), and one has been cancelled by a human press — so the
pipeline is proven to *run* on a live third-party tree. Precision, recall, order accuracy, speaker and
timestamp recovery on Instagram, Instagram Lite, WhatsApp, Telegram, Signal or Google Messages all
remain **NOT MEASURED**, because no inspected screen was transcribed. Per §12 a successful run alone
does not demonstrate successful message extraction, and per §8 no general compatibility may be claimed
from the R1–R8 handful of observations plus these three unlabelled rows.

### First real-app extraction runs, and RC-04 (added 2026-10-20 18:12, after the above was written)

The researcher armed the app four times against `com.instagram.android` and pressed Start. From
`adb -s emulator-5554 logcat -s GroomingResearchDemo:V`, verbatim:

```
18:07:13.389 Compatibility target set to com.instagram.android.
18:09:08.768 Retained message-level content forgotten. Counts remain.
18:09:08.769 cancelInspection -> true
18:09:11.884 Local results, session reports and any retained extraction cleared.
18:09:25.538 startInspection -> ARMED (target=com.instagram.android extractionOptIn=false)
18:09:58.347 Inspection outcome=FAILED  target=com.instagram.android event=true packageMatched=true root=false nodes=- …
18:10:33.818 startInspection -> ARMED (target=com.instagram.android extractionOptIn=true)
18:10:38.962 Inspection outcome=COMPLETED … nodes=56 textBearing=32 withText=20 descOnly=12 chars=268 depth=6 classes=8 truncated=false
18:10:38.962 Extraction status=INCOMPLETE sampled=56 candidates=11 accepted=11 ambiguous=4 rejected=41 order=GEOMETRY speakers=NOT_ATTEMPTED timestamps=false partial=true
18:11:30.366 startInspection -> ARMED (target=com.instagram.android extractionOptIn=true)
18:11:37.173 Inspection outcome=COMPLETED … nodes=58 textBearing=33 withText=20 descOnly=13 chars=253 depth=6 classes=8 truncated=false
18:11:37.174 Extraction status=RECONSTRUCTED sampled=58 candidates=17 accepted=17 ambiguous=0 rejected=41 order=GEOMETRY speakers=NOT_ATTEMPTED timestamps=false partial=false
```

Five observations, each tied to a line above:

1. **The extraction stage runs on a live third-party accessibility tree** without exception, resolves
   geometry-based reading order, and rejects 41 of 56–58 nodes as non-message. First time in this
   project.
2. **RC-04 fired for the first time.** docs/11 recorded `root=false` on a transition frame as Pending
   because it had never occurred in eight runs; 18:09:58 is that case, and the outcome was `FAILED`,
   correctly distinguished from "a screen that exposed no text".
3. **Stop/Cancel works on device as a human act** (18:09:08), and it cleared retained content before
   the counts were dropped. This closes the "needs a human press" gap in docs/13 §3 row 27.
4. **Redaction held against real conversation data.** Across a consent revoke, cancels, four arms and
   three completed extractions on a real Instagram screen, every one of these lines is counts, flags or
   a package name. No message string reached Logcat. That is the §4 guarantee observed on the data it
   was written to protect, not merely asserted by `ExtractionPrivacyTest`.
5. **The verdict swung on near-identical structure and the cause is unknown.** 56→58 nodes, `withText`
   unchanged at 20, but `accepted` 11→17 and `INCOMPLETE`/`partial=true` → `RECONSTRUCTED`/
   `partial=false`, ~60 seconds apart. Either the screen changed (another thread, a scroll) or the
   geometry row-grouping flipped. Unlabelled runs cannot separate those, which is why §6 finding 2 and
   the transcription milestone matter more than further runs. `accepted=17` is **not** "17 messages
   extracted" and must not be quoted as such.

`dupesSuppressed=0` and `echoesSuppressed=0` on device are **not** evidence of an echo-free tree:
`nodeId()` is null on device (finding 1 below), so those counters are uninterpretable there.

### Two findings only a device can show

1. **`nodeId()` is always null on device** (B.3). Duplicate-visit suppression is implemented, is
   exercised by `MessageLevelTraverserTest` and fixtures F4/F10, and is **not observable** on a real
   screen through that path; dedup there relies on wrapper identity. Do not report a device
   `duplicatesSuppressed` figure as proof that framework-level dedup ran.
2. **`parentNodeId` is null on device, which silently widens the same-row test.**
   `RowGrouper.sharedRowWithAnotherTextBearingSibling` asks for *another text-bearing node with the
   same parent* **and** vertical overlap. Since `nodeId()` is always null on a real tree, every node's
   `parentNodeId` is null, so `null == null` makes the parent clause **vacuously true**. The test
   therefore degrades on device from "same parent + same vertical band" to "same vertical band",
   across the whole sampled screen rather than within one row's parent. Overlap is judged against the
   shorter box (≥50%), which is what keeps separate rows from colliding, so the behaviour is still
   defensible — but a device `multiple-text-nodes-in-one-row` objection is **weaker evidence** than
   the same objection raised in a unit test, and must not be quoted as though the sibling constraint
   held. Both paths are covered by tests (`twoTextNodesInOneRowMakeTheRowAmbiguousInsteadOfMergingIt`,
   `twoTextNodesSharingARowAreLeftAmbiguousAndNeverMerged`); only the first one reflects device.

### Manual checklist for the researcher (not executed by this harness)

The consent gate is a human act, deliberately not automated. Steps, in order:

1. Settings → Accessibility → Installed apps → *Grooming Research Demo* → enable the service. (This
   build cannot do this for you; docs/11 §7 records why it will not try.)
2. Open the app. Confirm the home screen reports the service as enabled and the target as the intended
   app — currently `com.google.android.apps.messaging`.
3. Accept consent if prompted, then open the target app and navigate **yourself** to a conversation you
   are authorised to study, so the whole thread is visible on screen.
4. Return to the app → Experiment. Tick **"Message-level extraction"** only if that is the intent. Note
   the button label changes to "Start Inspection + Message-Level Stage" — that is the visible
   indication §4 requires.
5. Press it **once**. One press arms one inspection. Do not press again; a second press returns
   `ALREADY_ARMED` and cannot change the extraction choice, by design.
6. Observe the Results screen: the count set, the reading-order basis, the five ladder rows, and the
   partial-sample warning if the thread ran off-screen.
7. Only if the content must be read: use the temporary review action to open the review screen, read,
   then leave it — leaving destroys the retained text. There is no export, share or save path to
   resist.
8. To abandon mid-flight, use Stop/Cancel before the event arrives; cancelling disarms and clears
   `armedExtraction`.
9. Record the row in docs/13's table: screen type, ladder level reached, accepted/ambiguous/rejected
   counts, and the reason strings. Text itself is not recorded anywhere.
10. For any metric beyond counts, the screen must first be **transcribed by hand and consented** per
    docs/11 §9. Until an annotated sample exists, real-app precision and recall are `NOT MEASURED` and
    levels 4 and 5 of the ladder cannot be met.

### Blockers recorded instead of simulated

| Blocker | Status | Effect |
|---|---|---|
| Accessibility service toggle is human-only | Permanent by design | No automated real-app validation is possible; nothing here works around it. |
| No consented, annotated ground-truth sample | Open (docs/11 §9) | Every §8 metric on a real app is `NOT MEASURED`; ladder levels 4–5 unreachable on device. |
| Screen labels for RC-01/RC-02 and R5/R6 | Open | R1–R8 cannot be re-read as chat-thread evidence; RC-04 has never fired. |
| No instrumented-test infrastructure | Open, accepted | Device behaviour of the extraction stage is verified only by the manual checklist above. |

## Phase G — Validation report

Full report: [13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md](13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md).

### A.10 plan close-out against the G1–G14 gaps

| Plan | Closes | Status | Evidence |
|---|---|---|---|
| P1 node surface with defaults | G1, G5 | **Done**, minus `nodeId()` on device | C.1; `MessageLevelTraverserTest.aRefusedPropertyCostsThatPropertyNotTheNode`; Phase F finding 1 |
| P2 seen-set + `duplicateVisits` | G4 | **Done** | `MessageLevelTraverser` `duplicatesSuppressed`; `addSharedChild` and two-wrappers-id-44 tests |
| P3 transparent rule-based classifier | G2, G3 | **Done** | `CandidateClassifierTest` (16), each exclusion a named reason |
| P4 `MessageReconstructor` | G6, G7 | **Done** | `MessageReconstructorTest` (15); truncation/lost-substructure force `INCOMPLETE`; §6 rules 1–10 each mapped to ≥1 test |
| P5 five independently gated levels | G8 | **Done**, shape changed | `InspectionLadder` + `InspectionLadderTest` (9); deviation table C.9 |
| P6 retention policy + redaction test | G3, G10 | **Done** | `ExtractionPrivacyTest` (6); `CompatibilityReportBuilder` untouched and its footer still present |
| P7 results screen + deliberate review action | G8, G10 | **Done** | `ResultsScreen` counts/ladder, new `ReviewScreen` with `forgetExtractedContent()` on both exits |
| P8 validated-record adapter | G9 | **Done**, package changed | `model/ValidatedRecordAdapter.kt` + 9 tests; refuses `INCOMPLETE`/`UNSUPPORTED`; placeholder model still `NotIntegrated` |
| P9 harness + 14 fixtures + metric denominators | G12 (synthetic half) | **Done for synthetic; NOT MEASURED for real apps** | `SyntheticConversationFixtureTest` (14), `ExtractionMetricsTest` (11); docs/13 §5 |
| P10 device checklist | G11, G12 | **Done as a checklist, not executed** | Phase F above; blocker table |

G11 (unlabelled screens) and the real-app half of G12 stay **open** — they need a human at the device
and a consented transcription, so they are recorded as blockers rather than closed in code. G13 was
closed by design (no dependencies added; classifier and reconstructor are Android-free). G14 remains
as found: **no privacy violation existed**, so no corrective change was made, and none was introduced
under the banner of extraction.

### Definition-of-done check (§12), item by item

| Requirement | Verdict |
|---|---|
| Builds, or blocker documented | ✅ `BUILD SUCCESSFUL in 9s`, APK 12,114,117 bytes (Phase E) |
| Regression tests run and recorded | ✅ 57 → 156, 0 failures / 0 errors / 0 skipped, per class (Phase D) |
| New reconstruction logic has automated tests | ✅ 99 new tests across 9 classes, incl. the 14 named fixtures |
| User-triggered workflow intact | ✅ `arm()` gates on consent + service + target; `InspectionControllerTest` 15 unchanged tests still pass |
| Cancellable safely | ✅ `cancel()` disarms, clears `armedExtraction`; F14 proves the stage never runs after cancel |
| No background scraping / automatic history navigation | ✅ no timer, coroutine, poll or retry added; the service was later given one token-guarded 1.5 s settle read (2026-10-21 addendum), still one read per arm |
| Raw text not logged or uploaded | ✅ zero `<uses-permission>`, no `INTERNET`; hand-written `toString()` on all five content types; marker-string tests; device logcat after cold start contains only status lines |
| Uncertainty visible and preserved | ✅ `AMBIGUOUS` records kept, `INCOMPLETE` verdict, `TRAVERSAL_UNVERIFIED` reachable, unmet-ladder reasons on screen |
| Ground-truth metrics only where annotated samples exist | ✅ synthetic only, with denominators; every real-app metric `NOT MEASURED` (docs/13 §5) |
| Both docs accurate | ✅ docs/12 (this file) and docs/13 written from observed output; docs/11 left intact |
| A successful build alone does not demonstrate extraction | ✅ stated as such in Phase F and docs/13 §7 |

### Next recommended research milestone

Produce **one** consented, manually annotated Google Messages thread on `emulator-5554` using the
docs/11 §9 transcription method, run the checklist once with extraction ticked, and fill the real-app
row of docs/13 §5. That single artefact is what unlocks ladder level 4, converts the first
`NOT MEASURED` metrics into numbers with denominators, and turns the synthetic 12/13 picture into a
claim about a real app.

---

## Addendum — 2026-10-20 evening, after Phases A–G were written

Three things happened after the phases above were closed out, and they are recorded here rather than
retro-fitted into them.

### C.11 Review-screen layout fix

**Original behaviour.** `renderRecord` in
[ReviewScreen.kt](../app/src/main/java/com/example/groomingresearchdemo/ui/screens/ReviewScreen.kt)
drew each record as **two** `Text` layers — a `labelSmall` header line and a `bodySmall` text line —
inside a `SelectionContainer` nested in the scrolling `Column`. On a real screen with 16–17 records,
adjacent records printed over one another: words from record N and record N+1 landed on the same
baseline, which the researcher reported as "so many texts are overlapping".

**Change made.** One `Text` per record (header and body joined by a newline), and the record list now
wraps in its own `Column` with `verticalArrangement = Arrangement.spacedBy(10.dp)` instead of relying
on each child to push the next down. No behaviour, privacy or data change — the same fields, the same
order, the same discard-on-exit.

**Verification.** `./gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug` → `EXIT=0`,
156 / 0 / 0 / 0 re-read from the JUnit XML at 18:33, zero `e:`/`w:` lines. Then a full
`--rerun-tasks :app:assembleDebug` → `BUILD SUCCESSFUL in 10s`, 37 tasks executed, APK
**12,114,117 bytes** — byte-identical in size to the Phase E build, which is coincidence, not evidence
that nothing changed; the `ReviewScreenKt.dex` intermediates are newer than the edited source and the
18:33 test run compiled it. `install -r` → `Success`, relaunched as pid 17221. Both logs archived as
[`evidence/phaseF_ui_fix_build_incremental_2026-10-20_1833.log`](evidence/phaseF_ui_fix_build_incremental_2026-10-20_1833.log)
(incremental, 7 tasks executed) and
[`evidence/phaseF_ui_fix_build_rerun_2026-10-20_1833.log`](evidence/phaseF_ui_fix_build_rerun_2026-10-20_1833.log)
(full `--rerun-tasks`, 37 executed).

**Not verified.** The fix is reasoned from the code, not observed. It cannot be observed by this
harness: a screenshot of that screen would write Instagram message text into a file, which §4
forbids. **The researcher must confirm it visually.**

### A third real-app run, and an un-itemised false-positive report

`18:28:08.085 Extraction status=RECONSTRUCTED sampled=56 candidates=16 accepted=16 ambiguous=0
rejected=40 order=GEOMETRY speakers=NOT_ATTEMPTED timestamps=false partial=false` — quoted without its
paired structural line, which had already scrolled out of the log tail before it was read. That makes
**three** completed Instagram runs, not two; docs/13 §1, §3 row 28, §5.2 and §8 were corrected
accordingly.

Reading that screen, the researcher reported that non-message on-screen text was being counted as
messages. This matches the predicted failure mode (synthetic S6 loses a point to a sender label kept
as a message; Instagram chrome is a short leaf `TextView` with no geometry objection). It is recorded
as a **qualitative, un-itemised observation**: no specific record was annotated as wrong, so there is
no false-positive rate and **no classifier rule was added in response**. Tightening rules against one
unlabelled screen would overfit to Instagram's chrome and risks deleting real messages — the failure
§6 rule 10 exists to prevent.

### Device state at the time of writing

`adb devices` returns **no devices** and no emulator process is running, so the API 35 AVD is down.
Consequences: the log buffer holding the Instagram run lines is gone (they are preserved above and in
docs/13 §5.2, which is why those quotes matter); the layout fix is installed but unconfirmed; and the
next milestone's target needs revisiting — the runs actually happened on `com.instagram.android`,
while the plan above assumed Google Messages.

### Final report deliverable, 2026-10-21

At the researcher's request, a 10-page plain-language-plus-technical summary was written from this log
and docs/13: [`PROJECT_FINAL_REPORT.html`](PROJECT_FINAL_REPORT.html) and the PDF rendered from it,
[`PROJECT_FINAL_REPORT.pdf`](PROJECT_FINAL_REPORT.pdf) (412,748 bytes at first delivery; 413,017 bytes
after the 2026-10-21 settle-delay re-render to 413,013 bytes, **10 pages** in both cases — page count
read from the rendered file, not assumed). Renderer:
`chrome.exe --headless --disable-gpu --no-pdf-header-footer --print-to-pdf=…`; the first render came out
at 17 pages, so the print CSS was tightened and the prose compressed until every designed page fit one
printed page. No new claim appears in it that is not already in this log or docs/13, and the NOT
MEASURED / genuine-zero distinction is preserved there.

### Settle-delay change to the single read, 2026-10-21

At the researcher's request, the one tree read was given a fixed settle delay. This is a timing change
to the existing one-shot path, not a new collection mode: **still one human press, still one snapshot,
still one result**.

**Original behaviour.** `ChatInspectionService.onAccessibilityEvent` called
`ResearchStore.handleAccessibilityEvent(...)` synchronously, so `rootInActiveWindow` was read in the
same frame as the qualifying event. The only traversal bound was `MAX_TRAVERSAL_NODES = 5000`; there
was no delay, timer or second read.

**Why it changed.** The observed RC-04 failure (docs/13 §5.2) returned a null root on a run. Reading on
the transition frame that produced the event is a plausible cause, and a short settle window is the
minimal fix for it. The researcher chose 1.5 s from the bounded options offered; continuous capture was
not offered because §4 of the task forbids it.

**The change.** Every qualifying event now schedules **exactly one** deferred read with
`Handler(Looper.getMainLooper()).postDelayed(..., SETTLE_DELAY_MILLIS)` where
`SETTLE_DELAY_MILLIS = 1_500L`. The first queued read to run consumes the arm and traverses; any later
one finds the controller `NOT_ARMED` and returns null without opening the tree. `onDestroy` calls
`removeCallbacksAndMessages(null)`. A burst of events can therefore queue several callbacks, but at
most one can ever traverse — this is one read per arm, not a loop, and not a polling interval.

**Cancel / re-arm safety.** A delay opens a window in which the researcher can cancel or re-arm before
the read runs. `InspectionController` now keeps a monotonic `armToken`, bumped on every arm, cancel,
revoke and force-disarm. `armTokenFor(packageName)` returns the token only for the currently armed
target; the service captures it when the event arrives and hands it back after the delay. The
token-aware `handleEvent` overload rejects a stale token **before** calling `rootProvider`, so a queued
read can neither outlive its arm nor consume a newer one. The original two-argument `handleEvent` and
the two-argument `handleAccessibilityEvent` still exist and delegate with a null token, which is why
the 15 earlier controller tests needed no changes.

**Files changed.** `InspectionController.kt` (token, `armTokenFor`, token-aware overload),
`ResearchStore.kt` (pass-through plus `armTokenFor`), `ChatInspectionService.kt` (single `postDelayed`,
callback removal), `InspectionControllerTest.kt` (four new tests).

**New tests.** `armTokenIsOnlyIssuedForTheArmedTarget`, `queuedReadRejectedAfterCancelAndNeverOpensTree`
(asserts `treeOpened == false`), `staleTokenCannotConsumeAFreshArm`, and
`completedArmTokenCannotConsumeARearmedInspection`.

**Verification.** `./gradlew.bat --offline --rerun-tasks :app:testDebugUnitTest :app:assembleDebug` →
`BUILD SUCCESSFUL in 20s`, 43 actionable tasks executed (a preceding run of the same command took 50s;
both green). Log archived as
[`evidence/phaseG_settle_delay_build_2026-10-21.log`](evidence/phaseG_settle_delay_build_2026-10-21.log).
JUnit XML totals: **160 tests, 0 failures, 0 errors, 0 skipped** (controller class 19). APK
`app-debug.apk` 12,114,117 bytes; `adb -s emulator-5554 install -r` → `Success`, app relaunched as
pid 7652; after the final `@Synchronized` hardening it was rebuilt, reinstalled (`Success`) and
relaunched as pid 7958, with `ChatInspectionService connected (target=com.instagram.android)` logged
at 22:58:35.

**Not verified.** The effect of the delay on real root availability needs a human to arm an inspection
and scroll the target app; that remains the researcher's action, not something this harness performs.
No traversal wall-clock time was measured, so the claim is "the read waits 1.5 s", not "the read is
faster" or "the read always finds a root". Documentation was corrected accordingly: docs/04 ("no
continuous inspection"), docs/13 §7 (privacy table), and the report's "Not a monitor" bullet no longer
claim that `Handler`/`postDelayed` are absent; the re-rendered
[`PROJECT_FINAL_REPORT.pdf`](PROJECT_FINAL_REPORT.pdf) is 413,013 bytes and **10 pages**, re-checked
from the rendered file.
