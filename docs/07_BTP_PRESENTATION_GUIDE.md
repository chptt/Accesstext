# 07 — BTP Presentation Guide

Everything you need to present and defend this project: a 2-minute script, a live-demo checklist,
slide outline, and 44 panel questions with answers.

---

## A. 2-minute presentation script

> "My project is a **consent-based Android research demonstration** for online grooming detection.
> It separates the research problem into honest stages.
>
> **Motivation:** detecting grooming needs conversation text, clean preprocessing, and a trained
> model. The hard, sensitive part is getting text off a phone without violating privacy.
>
> **Architecture:** a Kotlin + Jetpack Compose app with four layers — a pure `inspection` layer
> containing a one-shot state machine and a structural tree inspector; a `service` layer with the
> AccessibilityService; a `model` layer for preprocessing and a model seam; and a `state` layer that
> connects the UI and the service through a single store.
>
> **Accessibility inspection:** after the user accepts a disclosure and manually enables the service,
> they choose one messaging app from a small allowlist and arm **one** structural inspection of it.
> When they open that app, the app receives one qualifying event, walks the active window's
> accessibility tree, and reports **only structure** — nodes visited, how many expose a label, the
> widget classes seen, text-availability percentage, and maximum depth. A package allowlist in the
> service config, a runtime `setServiceInfo()` narrowing, and an arm-time target check keep it from
> ever observing another app; the tree is never even opened for a non-qualifying event. It never
> stores, logs, or sends message text, and never auto-re-arms.
>
> **Compatibility report:** each inspection renders a copyable, purely structural record. Redaction is
> tested, not asserted — a unit test plants a marker string in node text and fails if it can be found
> in the report.
>
> **Preprocessing:** a laboratory runs entirely on **synthetic** conversations, normalising
> whitespace, dropping blanks while keeping legitimate repeats, preserving Malayalam and emoji, and
> maintaining chronological order, then serialising to a model-ready string. A separate import screen
> handles research-authorized text and is banner-labelled "not observed from the app under test", so
> the three kinds of text can never be confused.
>
> **Testing:** 57 unit tests pass, covering consent, the target gate, cancellation, wrong package,
> null/empty trees, depth, unreadable children, class histograms, report redaction, the authorized
> parser, and every preprocessing rule. The debug APK builds and was installed and smoke-tested on an
> API-26 emulator — consent and the chosen app persist across a restart.
>
> **Limitations and next steps:** this is scaffolding, not a working detector. Node counts are not
> message counts, an inaccessible screen is a valid result, the on-device compatibility runs are still
> recorded as pending, and there is no model — the adapter reports 'Model not integrated' until I
> connect my trained classifier."

## B. Live demonstration checklist

1. Launch the app → Home dashboard. Point at the seven status rows (all honest: unverified until checked).
2. **Review Consent** → read disclosure → **Accept** → back; status shows "Consent: Accepted".
3. **Enable Accessibility Service** → opens Settings → (explain the manual toggle; say the words
   *"I do not enable this programmatically"*) → return → **Re-check status**.
4. **Choose app under test** → pick a package from the allowlist → the row shows *installed / not
   installed*. Explain the three restriction layers.
5. **Start One-Time Inspection** → indicator: "Waiting for a qualifying accessibility event"; note the
   **armed-for** package is now frozen.
6. Show **Stop / Cancel** → cancel once, and explain it disarms immediately and records CANCELLED.
7. Arm again → switch to the chosen app, land on a screen → return → **View Results** → structural
   counts, text availability, widget classes, notes.
8. **View Compatibility Report** → show the copyable record and the privacy footer; emphasise: numbers
   and class names only.
9. **Open Preprocessing Laboratory** → pick a sample (e.g. Malayalam or out-of-order) → **Run
   preprocessing** → cleaned messages + copyable model input + validation OK.
10. Point to the model line: **"Model not integrated"**. Explain how that string feeds the real model.
11. **Import research-authorized text** → paste a line, run preprocessing, and read out the red banner:
    *"NOT OBSERVED FROM THE APP UNDER TEST"*.
12. Show `docs/11_REAL_APP_COMPATIBILITY_REPORT.md` — the procedure and the **PENDING** result rows —
    then `docs/06_TESTING_AND_RESULTS.md` and state the limitations.

**Fallback if the accessibility service cannot be toggled during the viva (recommended default):**
demonstrate steps 1–6 and 9–12 live (all work without the service), and for steps 7–8 show the
unit-test evidence for the inspection and report path (`InspectionControllerTest`,
`StructuralInspectorTest`, `CompatibilityReportTest`) plus the Results-screen interpretation. Say
clearly: "the on-device inspection is a manual step that has not been executed in this environment;
its logic is unit-tested and its result rows are marked pending rather than filled with invented
numbers." **Do not** present a synthetic number as a measured one.

## C. Slide outline (8 slides)

| # | Title | Main points | Visual | Say |
|---|---|---|---|---|
| 1 | Problem & goal | Grooming detection = text + preprocessing + model; the privacy-hard part is text | none | motivation |
| 2 | Architecture | 4 layers; consent + one-shot + structural-only | architecture diagram (08) | how it's split |
| 3 | Accessibility inspection | event→root→traverse→counts; never text | flow diagram (04) | the flow |
| 4 | Real-app **compatibility** experiment | choose app → arm once → structure; accessible *and* inaccessible outcomes | report screen + `docs/11` table | feasibility, not extraction |
| 5 | Privacy & consent design | manual enable, 3-layer package restriction, no network, redaction test, revoke clears | consent screen screenshot | the guardrails |
| 6 | Preprocessing lab | synthetic + authorized-import separation; whitespace/emoji/Malayalam/order/dupes | lab screenshot | the pipeline |
| 7 | Testing & results | 57 tests; APK built; emulator smoke test; **pending** on-device rows | results table (06) | evidence |
| 8 | Limitations & next steps | no extraction, no model; how to integrate; coverage is an upper bound | future-ML diagram (05) | honesty |

Slide 4 is the one that answers "so can you actually get the text?" — show the expected-outcome table
from `docs/11` §6, including the row where an app exposes **zero** text-bearing nodes, and say that a
zero is a finding, not a bug.

## D. Likely panel questions and model answers

Answer honestly; where something is unverified, say so.

### Research motivation & scope
1. **Q: What does your app actually detect?** A: Nothing yet — it's scaffolding for detection.
   *Beginner:* it prepares text and measures screen structure; the classifier is future work.
   *Follow-up:* "So what's the contribution?" → the privacy-safe measurement + preprocessing + a
   clean model seam. *Warning:* do not claim detection accuracy.
2. **Q: Why Android?** A: Grooming happens on mobile chat apps; a phone is where text originates.
3. **Q: Why a messaging app specifically?** A: Grooming happens in chat apps, and the question worth
   measuring first is whether a third-party app exposes its screen to accessibility APIs at all.
   *Follow-up:* "Do you have their data?" → No, structure only. *Follow-up:* "Why only five apps?" →
   The allowlist is a deliberate outer bound; a general-purpose target would make this a monitor.

### Android accessibility fundamentals
4. **Q: What is an AccessibilityService?** A: A system-bound component that receives events about
   other apps' UI, built for assistive tech.
5. **Q: Why use AccessibilityNodeInfo instead of reading the app's database?** A: The database is
   private, encrypted, and off-limits; the accessibility tree is the only sanctioned, on-screen view,
   and we use it for structure only. *Beginner:* we look at what's already shown to a screen reader.
6. **Q: What is rootInActiveWindow?** A: The root node of the focused window; our traversal entry.
7. **Q: Why can it be null?** A: During transitions or when no window is focused. *Follow-up:* "What
   do you do then?" → report FAILED with rootAvailable=false, no retries.
8. **Q: Difference between view hierarchy and accessibility hierarchy?** A: The accessibility tree is
   a simplified semantic view; nodes may be merged, hidden, or off-screen items omitted.

### AccessibilityNodeInfo & traversal
9. **Q: How do you traverse safely?** A: Breadth-first with an explicit queue, a 5000-node cap, and
   per-node try/catch. *Beginner:* avoids stack overflow and infinite loops.
10. **Q: Do you call recycle() on nodes?** A: No — deprecated at API 33 and a no-op from API 34; we
    target 36 and simply never retain node references. *Follow-up:* "Any leak?" → none; graph is GC'd.
11. **Q: Does a text-bearing node mean a message?** A: No — could be a button, header, timestamp.
12. **Q: Does node count equal message count?** A: No, there's no fixed ratio. *Warning:* never
    present counts as message counts.

### Consent & privacy
13. **Q: How is consent obtained?** A: A dedicated disclosure screen with explicit Accept; nothing
    is armed until accepted; Decline keeps it disabled.
14. **Q: Can the user back out?** A: Yes — Revoke cancels any pending inspection and clears results;
    they can also disable the service in Settings.
15. **Q: How do you know no data leaves the device?** A: The manifest declares **no** INTERNET
    permission, so the app cannot open a socket. *Beginner:* no permission = no transmission.
16. **Q: Where is chat text stored?** A: Nowhere — never in Logcat, prefs, files, or a DB. Only two
    non-sensitive settings keys persist: the consent flag and the chosen package. Inspection reports
    are memory-only and vanish on process death or revoke.
17. **Q: Why must the user enable the service manually?** A: Android forbids self-enabling because of
    its power; we never bypass it.

### Android architecture & Kotlin
18. **Q: Why a single activity?** A: Simpler state flow; all screens are Compose destinations.
19. **Q: How do the service and UI share state?** A: Through `ResearchStore`, an app-scoped singleton
    exposing a `StateFlow`.
20. **Q: Why keep the state machine Android-free?** A: So it's unit-testable without a device.
    *Follow-up:* "Show me" → `InspectionControllerTest`.
21. **Q: How is one-shot enforced?** A: The ARMED flag is consumed (→EXECUTING) before inspection and
    never auto-re-armed.

### Preprocessing
22. **Q: Why use synthetic data?** A: Real DMs are private and out of scope; synthetic proves the
    pipeline with zero risk. *Warning:* it doesn't prove extraction.
23. **Q: Why keep duplicate messages?** A: Repetition can be signal; dedup would erase it.
24. **Q: How do you preserve Malayalam and emoji?** A: Only whitespace is touched; no lowercasing or
    transliteration; UTF-16 strings keep the code points.
25. **Q: How do you ensure order?** A: Stable sort by timestamp when all present, else keep supplied
    order — never invent positions.

### ML integration
26. **Q: Is there a model?** A: No — the adapter returns "Model not integrated" and never a score.
27. **Q: How would you add your model?** A: Implement `GroomingModelAdapter`, load the same tokenizer
    used at training, feed it `preprocess()` output, return `Scored`.
28. **Q: How do you keep preprocessing matching training?** A: The module is deterministic and
    unit-tested; reuse the exact functions in the training script. *Follow-up:* "Biggest risk?" →
    preprocessing drift.

### Real-app compatibility testing
29. **Q: What is "compatibility testing" here?** A: A feasibility question — does the app expose
    usable structure and text through supported accessibility APIs? It is *not* a claim that
    conversations can be extracted. *Beginner:* "can a screen reader even see this app?"
30. **Q: Which app did you test, and what did you find?** A: Three on-device observations. On a
    **conversation screen**, Instagram Lite 533.0.0.9.105 exposed **16 text nodes / 205 characters**
    and Instagram 452.0.0.48.64 **18 / 245** — so text *is* reachable through supported accessibility
    APIs. But the first observation, on a non-chat screen, exposed **zero** text nodes. The finding is
    therefore **per-screen, not per-app**: availability depends on what is foreground. *Strongest panel
    answer:* "my first result was a false negative, and the second run caught it — that is why I report
    windows, not apps." Do not claim messages were extracted: the app never shows what those characters
    are, and a chat screen's text nodes also contain names, timestamps and button labels.
31. **Q: How do you keep it from inspecting other apps?** A: Three layers — the `packageNames` allowlist
    in the service config (platform-enforced), `setServiceInfo()` narrowing to the chosen package at
    runtime, and the `armedTarget` check in the controller. Plus a lazy root provider, so the tree is
    never opened for a non-qualifying event. *Follow-up:* "Show me" → `wrongPackageEventIsIgnoredAnd
    StaysArmed` asserts `treeOpened == false`.
32. **Q: What if the user changes the app selection mid-experiment?** A: It cannot redirect the armed
    inspection; the target was captured at arm time. Covered by
    `targetIsCapturedAtArmTimeAndIgnoresLaterSelectionChange`.
33. **Q: What if the app exposes no text?** A: That is a valid, reported result — `COMPLETED` with
    `textBearingNodes = 0` and a structural note. We treat unavailability as a finding, and never try
    to circumvent it.
34. **Q: What if a node's children can't be read?** A: The traversal continues, `unreadableChildNodes`
    increments, and a note records only the depth and widget class. Test:
    `unreadableChildrenAreCountedAndNoted`.
35. **Q: How do you prove the report has no private text?** A: The inspector only ever emits counts and
    class names — no code path carries node text into the report — and `CompatibilityReportTest.
    nodeTextNeverAppearsInTheReport` plants `PRIVATE-MESSAGE-MARKER-8f21c` in node text and asserts it
    never appears in the rendered report. *Key line:* redaction is tested, not asserted.
36. **Q: Why did you not just enable the service with adb?** A: Android requires the user to enable an
    accessibility service deliberately, because of its power. An attempt to write that setting was
    refused by the environment's safety policy, and I did not work around it — that refusal is recorded
    in `docs/11` §7 and `docs/06` §6. The pending status is the honest consequence.
37. **Q: Where does pasted conversation text come from, and is it the app's text?** A: The import screen
    is explicitly separate, banner-labelled "NOT OBSERVED FROM THE APP UNDER TEST", and holds text in
    memory only. It exists so content processing can be demonstrated without pretending it was captured.
38. **Q: Can you use other people's messages?** A: Only with appropriate consent and institutional
    requirements satisfied; the import screen says so, and the consent screen repeats it. That is an
    ethics boundary, not a technical one.
39. **Q: How would you measure real coverage?** A: Prepare a known transcript `T`, count text-bearing
    nodes `B` on that screen, and report `B/T` — as an **upper bound**, because labels include buttons
    and tabs. Not yet done; `docs/11` §9 sets out the method.

### Testing & evaluation
40. **Q: What did you actually test?** A: 57 unit tests + build + emulator install/launch/persistence.
    *Warning:* the on-device app inspection was not run here.
41. **Q: Name a bug your tests caught.** A: My own arithmetic in
    `textAvailabilityPercentageIsDerivedFromVisitedNodes` — I asserted 50 for 2-of-5, the integer result
    is 40. The test failed; I corrected the assertion and added a check on the denominator.
42. **Q: What evidence proves it works?** A: Passing tests + a built APK + smoke screenshots; and the
    honest reporting of what was *not* run.

### Limitations & future work
43. **Q: What are the current limitations?** A: No message extraction, no model, structural counts
    only, results vary by Android/app version, and the compatibility runs are pending.
44. **Q: What's next?** A: Execute the pending compatibility runs, train and connect the model, define
    an extraction strategy with proper consent/ethics, and measure coverage on labelled data.

> Never give an answer that claims the app extracts conversations, runs a model, or measures
> accuracy. It does not. Say what it does and what remains unverified.

## E. How to study this project (reading order)

1. `00_PROJECT_OVERVIEW.md` — the whole idea + your intro.
2. `02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md` — the concepts.
3. `03_SOURCE_CODE_WALKTHROUGH.md` — the code, file by file.
4. `04` then `05` — the two flows.
5. `11_REAL_APP_COMPATIBILITY_REPORT.md` — the experiment procedure and the pending result log.
6. `06` — the results you'll be asked about.
7. `08` + `10` — limitations and last-minute revision.

**Then study these source files, in this order** (paths under
`app/src/main/java/com/example/groomingresearchdemo/`):

1. `AppConstants.kt` — the allowlist of apps under test, the bounds (node cap, histogram keys, notes),
   and the log tag.
2. `inspection/Enums.kt` + `inspection/InspectionResult.kt` — the vocabulary, including `TARGET_REQUIRED`
   and every field of `StructuralStats`.
3. `inspection/InspectionController.kt` — the one-shot state machine; read `arm()`, `cancel()`,
   `handleEvent()` in that order, and note `armedTarget`.
4. `inspection/StructuralInspector.kt` — the BFS traversal and exactly what is counted.
5. `service/ChatInspectionService.kt` — where Android calls you, `applyTargetFilter()`, and the lazy
   root provider.
6. `report/CompatibilityReportBuilder.kt` — the redacted output; read it with its test.
7. `state/ResearchStore.kt` — consent, chosen package, service status, arming, in-memory reports.
8. `ui/screens/TargetScreen.kt` + `ExperimentScreen.kt` + `ResultsScreen.kt` + `ReportScreen.kt` —
   the panel-facing behaviour.
9. `model/ConversationPreprocessor.kt` + `model/AuthorizedTextParser.kt` +
   `model/GroomingModelAdapter.kt` — preprocessing, authorised input, and the boundary where your real
   model will attach.
10. `app/src/main/AndroidManifest.xml` + `app/src/main/res/xml/accessibility_service_config.xml` —
    the two declarations that make the service legal and bound which apps it can ever observe.

**Rehearsal:** run the demo checklist (B) twice on the emulator, then answer section D aloud once
without notes. Time the 2-minute script to land at ~120 seconds. Practise question 30 in particular —
"what number did you get?" — and the correct answer, "none yet, and here is why".

Next: [08_LIMITATIONS_AND_FUTURE_WORK.md](08_LIMITATIONS_AND_FUTURE_WORK.md).
