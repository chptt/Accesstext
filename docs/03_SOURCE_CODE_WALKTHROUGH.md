# 03 — Source Code Walkthrough

Every important file, what it does, why it exists, and what a reviewer might ask. All names and
signatures below match the actual code in `app/src/main/java/com/example/groomingresearchdemo/`.

Package layout:
- `inspection/` — Android-free structural logic + the one-shot state machine (unit-testable).
- `model/` — conversation preprocessing (synthetic + authorized import) + the model seam.
- `report/` — renders an inspection as a redacted compatibility record.
- `service/` — the AccessibilityService and its thin adapters + logging.
- `state/` — the app-scoped store that connects UI ⇄ service.
- `ui/` — Compose screens and theme.

---

## A. `AppConstants.kt`

- **Purpose:** single home for non-sensitive constants.
- **Key members:** `INSTAGRAM_PACKAGE = "com.instagram.android"`,
  `INSTAGRAM_LITE_PACKAGE = "com.instagram.lite"`,
  `DEFAULT_TARGET_PACKAGE`, `TARGET_OPTIONS` (the six allowlisted messaging apps),
  `LOG_TAG = "GroomingResearchDemo"`, `MAX_TRAVERSAL_NODES = 5000`, `MAX_CLASS_HISTOGRAM_KEYS = 40`,
  `MAX_NOTES = 6`, `MAX_REPORTS_KEPT = 20`.
- **Why it exists:** keeping the target list and the caps in one place makes the privacy boundary
  auditable and prevents magic strings scattered across files. The allowlist here, the one in
  `accessibility_service_config.xml`, and the `<queries>` block in the manifest are deliberately the
  same six packages — three independent readers of the same bound.
- **Note on Instagram Lite:** Instagram and Instagram Lite are two different packages installed side
  by side or alone. An inspection armed against `com.instagram.android` never matches a Lite window,
  so both are allowlisted and the researcher selects the one actually on the device.
- **Reviewer Q:** "Why cap traversal at 5000?" → To guarantee the inspection terminates even on a
  pathological tree; real messaging windows are far below this, and `truncated` is reported if hit.

## B. `GroomingResearchApp.kt` (Application)

- **Purpose:** calls `ResearchStore.init(this)` in `onCreate()`.
- **Why:** both the Activity and the system-bound AccessibilityService share the same store; it
  must be initialised before either uses it. It loads **only** the consent decision and the chosen
  target package name — never an armed-inspection state — so a restart cannot auto-begin an
  inspection.
- **Error condition:** if you forget `android:name=".GroomingResearchApp"` in the manifest, init
  never runs. **This exact bug was hit during smoke testing and fixed** (see the journal).
- **Reviewer Q:** "What persists across app restarts?" → Two non-sensitive strings in SharedPreferences:
  the consent flag and the target package name. No statistics, no reports, nothing content-like.

## C. `MainActivity.kt`

- **Purpose:** single `ComponentActivity`; `enableEdgeToEdge()` then `setContent { AppTheme { RootScreen() } }`.
- **Why one activity:** all nine spec "screens" are Compose destinations in one manual nav graph —
  simpler to reason about and to explain than a navigation library.
- **Reviewer Q:** "Why not Jetpack Navigation?" → To keep the dependency surface minimal and make
  the state flow explicit for the write-up.

## D. `inspection/Enums.kt`

- **Purpose:** the orthogonal concepts the spec demands be kept separate.
- **Members:** `ConsentStatus { NOT_GIVEN, ACCEPTED, DECLINED }`, `ArmStatus { NOT_ARMED, ARMED, EXECUTING }`,
  `ExperimentOutcome { NONE, COMPLETED, FAILED, CANCELLED }`,
  `ArmResult { ARMED, CONSENT_REQUIRED, SERVICE_REQUIRED, TARGET_REQUIRED, ALREADY_ARMED }`.
- **Why:** so "consented + service on + target chosen + armed + running + has result" is never
  collapsed into one Boolean.
- **Reviewer Q:** "Show me where a single flag is NOT used." → `ResearchUiState` has separate fields
  for each of them.

## E. `inspection/AccessibilityNodeView.kt`

- **Purpose:** a 5-method interface (`text`, `contentDescription`, `className`, `childCount`,
  `getChild`) that abstracts an accessibility node.
- **Why:** lets `StructuralInspector` be unit-tested with fake trees (`FakeNode.kt`) without an
  emulator or the real `AccessibilityNodeInfo`. `className` was added for the compatibility
  experiment: knowing that a screen is built from `RecyclerView` + `TextView` versus one opaque
  custom view is the difference between "accessible" and "not reachable", and a class name is
  structural metadata, not user content.
- **Reviewer Q:** "How do you test traversal without a device?" → Through this seam.

## F. `inspection/InspectionResult.kt`

- **Purpose:** data classes `StructuralStats` and `InspectionResult`.
- **Key fields of `StructuralStats`:** `nodesVisited`, `nodesWithText`,
  `nodesWithContentDescriptionOnly`, `textBearingNodes`, `emptyOrUnavailableTextNodes`,
  `totalTextCharacters`, `maxDepth`, `distinctNodeClasses`, `nodeClassCounts`,
  `unreadableChildNodes`, `truncated`, `notes`, plus the derived `textAvailabilityPercent`.
- **Key fields of `InspectionResult`:** `eventReceived`, `packageMatched`, `rootAvailable`,
  `stats`, `outcome`, `failureReason`, `targetPackage`.
- **Why separate stage flags:** so a COMPLETED result genuinely means "root was available and a
  traversal produced stats", not just "an event arrived".
- **Why the split between text and contentDescription:** an author-written label on an icon is a
  different kind of evidence from rendered text; lumping them would overstate message exposure.

## G. `inspection/StructuralInspector.kt`

- **Purpose:** breadth-first traversal returning `StructuralStats?`.
- **Signature:** `fun inspect(root: AccessibilityNodeView?): StructuralStats?` (null root → null).
- **Behaviour:** queue of `(node, depth)` with root at depth 1; counts visited nodes; classifies
  each node as text / label-only / nothing; adds only a text node's **length** to
  `totalTextCharacters`; builds the widget-class histogram (bounded by `MAX_CLASS_HISTOGRAM_KEYS`,
  with a separate set for the distinct count); computes `maxDepth`; sets
  `emptyOrUnavailableTextNodes = visited - textBearing`; honours `maxNodes` with a `truncated`
  flag; and wraps every read — `text`, `contentDescription`, `className`, `childCount`/`getChild` —
  so one hostile subtree cannot crash the inspection. Each failure is counted in
  `unreadableChildNodes` and described in `notes` with depth and class name only.
- **Why BFS not recursion:** deep hierarchies could overflow the call stack.
- **What it never does:** return, store, or log any node's text. Text is consumed inside the loop
  and dropped.
- **Reviewer Q:** "Does it read message text?" → It reads text only to test blankness and measure
  length; the characters are never retained or output.

## H. `inspection/InspectionController.kt` (the heart)

- **Purpose:** the pure-Kotlin one-shot state machine. No Android imports.
- **Key methods:**
  - `arm(consentAccepted, serviceEnabled, targetPackage): ArmResult` — enforces all three gates
    (consent, service, a non-blank target); refuses if already armed; **captures** `armedTarget`.
  - `cancel(): Boolean` — only when ARMED; records a CANCELLED result without traversing.
  - `handleEvent(packageName, rootProvider): InspectionResult?` — ignores non-armed and wrong-package
    events (returns null, **stays armed**); otherwise **consumes** the arm (→ EXECUTING) *before*
    calling `rootProvider()`, inspects, records COMPLETED/FAILED, increments `completedCount`,
    clears `armedTarget`, and returns to NOT_ARMED.
  - `clearResults()`, `revokeAndClear()`, `forceDisarm()`.
- **Why "consume before inspect":** guarantees exactly one inspection per arm even if events arrive
  back-to-back during the traversal.
- **Why the target is a parameter of `arm()` rather than a constructor field:** the researcher can
  change the app under test between inspections without rebuilding anything, and an inspection that
  is already waiting keeps the package it was armed for. `targetIsCapturedAtArmTimeAndIgnoresLaterSelectionChange`
  asserts exactly that.
- **Why wrong-package keeps the arm:** the user should still get their inspection against the app
  they chose; and the platform filter already prevents non-target events, so this is defensive.
- **Reviewer Qs:** "How do you prevent continuous inspection?" → arm is consumed and never
  auto-re-armed; `handleEvent` returns null unless explicitly ARMED. "What happens on cancel?" →
  `cancel()` flips to NOT_ARMED and records CANCELLED.

## I. `model/ChatMessage.kt`

- **Purpose:** `data class ChatMessage(speaker: String?, text: String?, timestampMillis: Long?)`.
- **Why all nullable:** the pipeline must represent a missing speaker/timestamp honestly rather than
  invent one.

## J. `model/ConversationPreprocessor.kt`

- **Purpose:** the required functions.
  - `normalizeWhitespace(text: String?): String` — trim + collapse `\s+` runs to single spaces.
  - `cleanMessages(messages): List<ChatMessage>` — normalise text, drop blank/null turns, keep
    duplicates, keep speaker/timestamp.
  - `preserveMessageOrder(messages)` — stable-sort by timestamp **only if all present**, else keep
    supplied order.
  - `toModelInput(messages): String` — one line per message, `"Speaker: body"` or bare `body`.
  - `validateModelInput(messages): PreprocessValidation` — checks non-blank output and
    `lineCount == cleanedCount`.
  - `preprocess(messages)` — convenience: clean → order → serialise.
- **Why conservative:** no lowercasing/stripping — inference preprocessing must mirror training.
- **Reviewer Q:** "Why keep duplicate messages?" → Repetition can be signal; dedup would erase it.

## K. `model/SampleConversations.kt`

- **Purpose:** seven labelled **synthetic** scenarios (normal, whitespace, emoji, Malayalam+English,
  empty, duplicates, out-of-order).
- **Why:** to demonstrate and unit-test preprocessing with zero privacy risk.

## L. `model/GroomingModelAdapter.kt`

- **Purpose:** the ML seam. `interface GroomingModelAdapter { isReady; predict(input): ModelPrediction }`,
  `sealed interface ModelPrediction { NotIntegrated; Scored }`, and
  `object PlaceholderGroomingModelAdapter` which returns `NotIntegrated("Model not integrated")`.
- **Why:** the demo must not fabricate predictions; the placeholder is truthful and the KDoc
  explains exactly where to wire the real model + tokenizer.
- **Reviewer Q:** "Is there a model?" → No. The placeholder never returns a score.

## M. `service/LogUtils.kt`

- **Purpose:** the single logging funnel with tag `GroomingResearchDemo`.
- **Why:** one place to audit that nothing sensitive is ever logged.

## N. `service/RealAccessibilityNodeView.kt`

- **Purpose:** adapts `AccessibilityNodeInfo` to `AccessibilityNodeView`.
- **Key decision:** it does **not** call `recycle()`. Node recycling was deprecated at API 33 and is
  a no-op from API 34; this app targets API 36, so the correct modern practice is to simply not
  retain node references — which the design guarantees (nodes live only inside one traversal).
- **Reviewer Q:** "Do you leak nodes?" → No references are stored beyond the traversal; the whole
  graph is GC'd when `handleEvent` returns.

## O. `service/ChatInspectionService.kt`

- **Purpose:** the `AccessibilityService`. Overrides `onServiceConnected`, `onAccessibilityEvent`,
  `onInterrupt`, `onDestroy`.
- **onServiceConnected:** calls `applyTargetFilter(selected)` then `ResearchStore.notifyServiceConnected()`.
- **`applyTargetFilter()`:** sets `serviceInfo.packageNames = arrayOf(target)` and calls
  `setServiceInfo(serviceInfo)`. This **narrows** the six-package allowlist from the config XML to
  the one app under test, so the framework delivers no events for the others. It never adds event
  types, windows, or capabilities. Registered for changes through
  `ResearchStore.onTargetPackageChanged`, and unregistered in `onDestroy`.
- **onAccessibilityEvent:** forwards `event.packageName` and a **lazy** `rootProvider`
  (`rootInActiveWindow?.let(::RealAccessibilityNodeView)`) to `ResearchStore.handleAccessibilityEvent`.
  Because the provider is lazy, the tree is never opened for an unarmed/non-target event.
- **Why thin:** all decisions live in the controller, so behaviour is unit-tested.

## P. `state/ResearchUiState.kt`

- **Purpose:** immutable snapshot the UI renders: `consentStatus`, `serviceEnabled`, `armStatus`,
  `armedTarget`, `lastResult`, `completedCount`, `targetPackage`, `reports`.

## Q. `state/ResearchStore.kt`

- **Purpose:** the shared singleton. Holds the `InspectionController`, a `MutableStateFlow<ResearchUiState>`,
  the consent + target SharedPreferences keys, and the in-memory session report list.
- **Persistence surface:** exactly two keys — `consent_status` and `target_package`. Both are
  non-sensitive strings. Statistics and reports are memory-only.
- **Service-status detection:** `computeServiceEnabled = serviceBoundBySystem || AccessibilityManager
  enabled-list check || Settings.Secure enabled-services string check`. It only ever reports
  "Enabled (verified)" after a real check — never faked.
- **Consent:** `accept/decline/revoke`; revoke and decline call `controller.revokeAndClear()` **and**
  clear the session reports.
- **Target:** `setTargetPackage()` persists the choice and notifies the connected service;
  `isPackageInstalled()` answers the setup screen's "is it actually here?" using the manifest
  `<queries>` scope.
- **`startInspection()`** refreshes status then arms with the current target;
  **`handleAccessibilityEvent`** delegates, appends COMPLETED/FAILED results to `sessionReports`
  (capped at `MAX_REPORTS_KEPT`, newest first), publishes, and logs structural numbers only.
- **Reviewer Q:** "Where does the service talk to the UI?" → Both go through this store's StateFlow.

## R. `ui/theme/Theme.kt`

- **Purpose:** deterministic Material 3 light/dark colour schemes (no dynamic colour, so it looks
  identical on the API-26 emulator). Contrast meets AA for body text.

## S. `ui/RootScreen.kt`

- **Purpose:** `enum Screen` (HOME, CONSENT, SERVICE, TARGET, EXPERIMENT, RESULTS, REPORT, LAB,
  IMPORT) + a `when`-based manual nav; `Scaffold` + `TopAppBar` with a back arrow; collects
  `ResearchStore.uiState` via `collectAsState(initial = ResearchUiState())`. Also defines shared
  `InfoCard` and `StatusRow`.
- **Bug fixed here:** the first build failed because `collectAsState` was imported from the wrong
  package; corrected to `androidx.compose.runtime.collectAsState` with an `initial` value.

## T. `ui/screens/*.kt`

- **HomeScreen** — dashboard: title, subtitle, description, seven separate status rows (consent,
  service, compatibility target, armed-for, last experiment, count, reports held), and the action
  buttons. Start is disabled unless consent + service are verified. Cancel is enabled only while
  actually armed.
- **ConsentScreen** — full disclosure bullets including the feasibility framing and the
  third-party-consent requirement, Accept/Decline, and a Revoke control once accepted.
- **ServiceSetupScreen** — manual steps, an "Open accessibility settings" button
  (`Settings.ACTION_ACCESSIBILITY_SETTINGS`, with a graceful fallback), a "Re-check status" button,
  and the verified status.
- **TargetScreen** — the five allowlisted messaging apps as chips with installed/not-installed
  status, a custom-package field (recorded, still bounded by the allowlist), the armed target, and a
  reset control. Changing the target while armed is disabled.
- **ExperimentScreen** — explanation, the app under test + whether it is installed, Start,
  Stop / Cancel, "Change the app under test", "Open the compatibility report", and a live state
  indicator (Not armed / Waiting for a qualifying event / Completed / Failed / Cancelled).
- **ResultsScreen** — the `InspectionResult` stage flags, the full structural stats, the widget-class
  table, structural notes, and an interpretation note that counts are not messages.
- **ReportScreen** — one selectable redacted record per completed or failed inspection in the session,
  rendered by `CompatibilityReportBuilder`, inside a `SelectionContainer` so the researcher can copy
  it into a lab notebook. Cleared by Clear Results; never written to disk.
- **PreprocessingLabScreen** — synthetic sample chips, raw view, Run preprocessing, cleaned messages,
  a copyable model-input field, validation status, and the model line ("Model not integrated").
- **AuthorizedImportScreen** — the only place that processes message text: the researcher pastes
  content they are authorized to use, it is parsed by `AuthorizedTextParser`, run through the same
  preprocessor, and shown with a copyable model input. In memory only, never logged or persisted,
  cleared by "Clear and forget", and headed by a warning that nothing here came from the live app.

---

## U. Test files (`app/src/test/java/…`)

- `inspection/FakeNode.kt` — builds fake trees (`chain(n)`, `addChild`, optional class name, and a
  node that throws when its children are read).
- `inspection/StructuralInspectorTest.kt` — 13 tests (null root, empty tree, no-text tree, several
  text nodes, whitespace-only, max depth, node cap, class histogram, unknown class, text vs
  contentDescription split, availability percentage, unreadable children, zero-label note).
- `inspection/InspectionControllerTest.kt` — 15 tests (consent/service/target gates, double-arm,
  cancel, wrong package with a never-opened-tree assertion, target captured at arm time,
  consume-once, no re-fire, null root, empty tree, revoke, force-disarm).
- `model/ConversationPreprocessorTest.kt` — 13 tests (whitespace, blanks, duplicates, Malayalam/emoji,
  punctuation, ordering, missing speaker/timestamp, determinism, line-count, all-samples).
- `model/AuthorizedTextParserTest.kt` — 9 tests (speaker split, first separator only, no separator,
  colon-without-space, never invents timestamps, order preserved, blank lines, Unicode round trip,
  empty import).
- `model/PlaceholderGroomingModelAdapterTest.kt` — 3 tests (NotIntegrated, not ready, no scores).
- `report/CompatibilityReportTest.kt` — 4 tests, including the privacy guarantee that distinctive
  node text never appears in a rendered report.

**Total: 57 tests, all passing** (see [06_TESTING_AND_RESULTS.md](06_TESTING_AND_RESULTS.md)).

---

## V. Files added for the real-app compatibility experiment

| File | Purpose | Why it exists |
|---|---|---|
| `report/CompatibilityReportBuilder.kt` | Renders an `InspectionResult` as a copyable, purely structural record | The experiment needs an artefact the researcher can paste into a lab notebook without ever risking content leakage |
| `model/AuthorizedTextParser.kt` | Turns pasted, research-authorized lines into `ChatMessage`s | Content processing must have an explicit, human-supplied entry point that is not the accessibility path |
| `ui/screens/TargetScreen.kt` | Choose and verify the app under test | The experiment is no longer Instagram-only |
| `ui/screens/ReportScreen.kt` | Session list of redacted reports | Makes the compatibility findings visible and copyable |
| `ui/screens/AuthorizedImportScreen.kt` | Authorized text import + preprocessing | Keeps real content away from both the synthetic fixtures and the live-app observation |
| `AndroidManifest.xml` `<queries>` | Package visibility for six messaging apps | Lets the app say "not installed" honestly without `QUERY_ALL_PACKAGES` |
| `res/xml/accessibility_service_config.xml` | `packageNames` allowlist of six packages | Platform-level bound on which apps can ever deliver events to us |
| `res/values/strings.xml` | Service description shown in Android Settings | The disclosure the user reads while granting the permission must match what the code now does |

**Reviewer Q:** "Why narrow `packageNames` at runtime instead of just filtering in code?" → Because
the platform filter is the stronger guarantee: with it, events for the other apps are never
delivered to us at all. The code check stays as a second layer, and the config allowlist is the
outer bound that a runtime failure cannot widen.

Next: [04_ACCESSIBILITY_INSPECTION_FLOW.md](04_ACCESSIBILITY_INSPECTION_FLOW.md).
