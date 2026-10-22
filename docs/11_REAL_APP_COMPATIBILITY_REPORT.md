# 11 — Real-App Accessibility Compatibility Report

This is the working record for the second half of the project: testing whether a **real**
messaging app exposes usable interface structure and text through Android's supported
`AccessibilityService` APIs.

It is a **feasibility** record, not an accuracy record. Read [08_LIMITATIONS_AND_FUTURE_WORK.md](08_LIMITATIONS_AND_FUTURE_WORK.md)
before drawing any conclusion from a number in here.

---

## 1. What this experiment answers, and what it does not

| Question | Answered by this experiment? |
|---|---|
| Does the app deliver accessibility events for its windows? | **Yes** |
| Does an armed one-shot inspection obtain a non-null active root? | **Yes** |
| How large is the exposed tree, how deep, which widget classes? | **Yes** |
| What fraction of nodes expose any label at all? | **Yes** |
| Which nodes correspond to chat messages? | **No** — not determinable from the tree |
| Can sender names and timestamps be read reliably? | **No** — not assumed anywhere in the code |
| Was a complete conversation extracted? | **No — and the app never returns text at all** |
| Does the grooming-detection model work? | **No — model evaluation is a separate activity (§8)** |

The app under test is chosen on the **Choose app under test** screen and is restricted to a
six-package allowlist declared in
[accessibility_service_config.xml](../app/src/main/res/xml/accessibility_service_config.xml):
Google Messages, Instagram, Instagram Lite, WhatsApp, Telegram, Signal. At connect time
[ChatInspectionService](../app/src/main/java/com/example/groomingresearchdemo/service/ChatInspectionService.kt)
narrows that further to the single selected package via `setServiceInfo()`, so the framework
delivers no events for the others.

---

## 2. Prerequisites (all manual, by design)

1. A device or emulator running the app.
2. The messaging app **installed and signed in by the researcher by hand**. This project never
   automates a login and never bypasses authentication.
3. Consent accepted inside the research app.
4. The accessibility service toggled on **by a human** in Android Settings. The app cannot and
   does not enable it; see §7 for why the test harness must not fake this either.

---

## 3. Exact procedure for one observation

1. Open **Grooming Detection Research Demo**.
2. **Review Consent** → read → **Accept**.
3. **Enable Accessibility Service** → Android Settings → *Grooming Research Inspection (structural
   only)* → toggle on → accept the system warning → return → **Re-check status**. The row must
   read `Enabled (verified)`.
4. **Choose app under test** → select the installed messaging app. The row *Installed on this
   device* must read `Yes`.
   - **Instagram and Instagram Lite are different packages.** `com.instagram.android` and
     `com.instagram.lite` can both be present on one device, and an inspection armed against one
     never matches a window from the other. Pick the variant you are actually going to open, then
     confirm the dashboard's *Compatibility target* row reads that package.
   - The dashboard shows the *armed* package separately from the *configured* one, so a mismatch
     here is visible before, not after, an inspection.
5. **Start One-Time Inspection**. The indicator reads *Waiting for a qualifying accessibility
   event*. Nothing has been inspected yet.
6. Bring the messaging app to the foreground and open the screen you want to characterise
   (conversation list, or one open thread). **Stop there.** Do not scroll, do not tap anything in
   that app for the purpose of the experiment; the inspection consumes the first qualifying
   window event and then disarms itself.
7. Return to the research app. **View Results** shows the per-stage outcome; **View Compatibility
   Report** shows the redacted record.
8. Long-press → select all → copy the report, and paste it into §6 below with the screen you were
   looking at written in the "Screen under test" column.
9. To take another observation, repeat from step 5. One button press buys exactly one inspection.

**Abort at any time:** *Stop / Cancel Inspection* disarms immediately without traversing, and the
system Settings toggle turns collection off entirely.

---

## 4. What a report contains

Rendered by
[CompatibilityReportBuilder](../app/src/main/java/com/example/groomingresearchdemo/report/CompatibilityReportBuilder.kt):

| Field | Meaning |
|---|---|
| Test app package | the armed target |
| Installed / Consent / Service | configuration state at report time |
| Accessibility event received | did a qualifying event arrive at all |
| Foreground package matched | was it from the armed target |
| Active root available | did `rootInActiveWindow` return a node |
| Outcome | `COMPLETED` / `FAILED` / `CANCELLED` |
| Nodes visited | traversal size, `≤ 5000` cap |
| Nodes exposing text | `node.text` non-blank |
| Nodes with label only | `contentDescription` non-blank and no text — icon labels, **not** message bodies |
| Text-bearing nodes (% of visited) | either of the above; a **ceiling** on label availability |
| Nodes with empty / unavailable text | visited minus text-bearing |
| Aggregate text characters exposed | sum of text **lengths**; a volume measure, never the content |
| Maximum hierarchy depth | root is depth 1 |
| Distinct widget classes | how varied the exposed UI is |
| Nodes whose children were unreadable | where the app or platform refused enumeration |
| Widget classes seen | top 10 by node count |
| Structural notes | truncation, unreadable subtrees, zero-label trees |

Every field is a count, a depth, a class name, or a note. `CompatibilityReportTest` builds a tree
whose text is a distinctive marker string and asserts that the marker appears **nowhere** in the
rendered report — the redaction guarantee is a test, not a comment.

---

## 5. Expected outcomes, so a "bad" number is not misread

| Screen type | Expected report | Correct reading |
|---|---|---|
| Ordinary list screen (conversation list) | root available, tens–hundreds of nodes, high text-bearing share, `RecyclerView`/`TextView` classes | Structure is exposed; **still not proof** that each text node is a message |
| Open chat thread | root available, many text nodes, deep nesting | Labels reachable; speaker identity and timestamps remain unestablished |
| Custom-drawn or secured screen (e.g. a canvas-rendered view, a passphrase dialog) | root available but **0 text-bearing nodes**, or unreadable children noted | A **valid negative result**. Treat it as the finding; do not work around it |
| Transition frame / animation mid-flight | `FAILED`, root unavailable | Re-arm once and retry deliberately; never auto-retry |
| Event from a non-target app while armed | nothing at all — arm stays armed, tree never opened | Correct isolation; verify by arming and opening a different app |
| Service toggled off mid-session | arm forced to `NOT_ARMED` on disconnect | No orphan inspections |

---

## 6. Result log

**Status: 8 observations executed (R1–R8).** These figures were read from the device's own structural
log after the researcher enabled the service by hand and pressed Start.

| # | Time | App | Screen under test | Nodes | Text-bearing (%) | `withText` | `descOnly` | Chars | Depth | Classes | Outcome |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R1 | 14:54 | Instagram 452.0.0.48.64 | *not labelled — not a chat* | 19 | 11 (57%) | **0** | 11 | **0** | 9 | 6 | COMPLETED |
| R2 | 15:15 | Instagram Lite 533.0.0.9.105 | open DM (researcher-stated) | 80 | 16 (20%) | **16** | 0 | **205** | 5 | 5 | COMPLETED |
| R3 | 15:17 | Instagram 452.0.0.48.64 | open DM (researcher-stated) | 55 | 30 (54%) | **18** | 12 | **245** | 6 | 8 | COMPLETED |
| R4 | 15:26 | Google Messages | *unidentified — 8 nodes only* | 8 | 1 (12%) | **1** | 0 | **8** | 6 | 4 | COMPLETED |
| R5 | 15:28 | Google Messages | conversation list? (unconfirmed) | 46 | 12 (26%) | **4** | 8 | **70** | 10 | 7 | COMPLETED |
| R6 | 15:34 | Google Messages | open thread? (unconfirmed) | 20 | 11 (55%) | **8** | 3 | **135** | 8 | 5 | COMPLETED |
| R7 | 15:35 | Google Messages | conversation list? (unconfirmed) | 46 | 12 (26%) | **5** | 7 | **74** | 10 | 7 | COMPLETED |
| R8 | 15:35 | Google Messages | same window as R7 | 46 | 12 (26%) | **5** | 7 | **74** | 10 | 7 | COMPLETED |

Every run had `root=true`, `packageMatched=true`, `unreadable=0`, `truncated=false`.

**Correction notice, because this table was wrong once already.** R4–R6 had *already been executed*
when R7/R8 were first recorded, and were missed: the log was read with a truncated tail and three
completed observations went unreported. A further consequence — the "identical repeat proves
determinism" claim written into the first draft of this section was built on only half the evidence,
and is corrected below. Rows were renumbered chronologically, so what was published as R4 is now R7.

**The headline result: text IS reachable, and R1 was a false negative.** R1 showed `withText=0`,
`chars=0`. R2 and R3 — on conversation screens — show **16 text nodes / 205 characters** and
**18 / 245**. So the conclusion is not "Instagram hides its messages"; it is *"availability depends
entirely on which screen is foreground"*. Had R1 been reported alone, this project would have
published the opposite of the truth. **One observation is not a result about an app, it is a result
about one window.**

**Determinism: a claim that survived 28 seconds and not seven minutes.** R7 and R8, back to back, are
identical in every field. But R5 and R7 are the same 46-node screen seven minutes apart and they
*differ*: `withText` 4 → 5, `descOnly` 8 → 7, `chars` 70 → 74 — while node count, depth and class
count held exactly. So the defensible statement is that the **shape** of the tree is stable while its
**text content drifts**, which is what a live inbox gaining a timestamp or an unread marker would
produce. A back-to-back repeat proves the *traversal* is deterministic; it does not prove the *screen*
is unchanged, and the earlier draft of this section conflated the two.

**Reading the two columns that are easy to confuse:**
- `withText` = nodes exposing a real `text` value. This is the text surface. R2 = 16, R3 = 18, R6 = 8.
- `descOnly` = nodes labelled *only* by `contentDescription` — an annotation about a control, not
  content. R1 had 11 of these and zero text; R2 had none.
- `textBearing` counts the two together, so it must never be quoted on its own. R1's 57% and R3's 54%
  look similar and mean opposite things: R1's were all descriptions, R3's were mostly text.

**The Messages comparison is provisional, not a finding.** It is tempting to read R5/R7 (46 nodes,
~5 text, ~72 chars) against Instagram's R3 (55 nodes, 18 text, 245 chars) as "Messages exposes less
text". That comparison is not safe yet: **none of R4–R8 is screen-labelled**, and R4 — 8 nodes, one
text node, eight characters — is almost certainly not a messaging screen at all, so it must be
excluded rather than averaged in. R6 (20 nodes, 8 text, 135 chars) is the shape you would expect from
an open thread and is the only Messages row that looks text-dense. Labelling these rows is the next
step before any cross-app sentence is written.

**What is still NOT claimed.** The app never displays or stores what those characters are, so 245
characters across 18 text nodes (≈13 per node) cannot be asserted to be message bodies — on a chat
screen that set also contains sender names, timestamps, "Active now", and button labels. The finding
is *"a text surface exists and is readable"*, which is a feasibility result, not an extraction result.
Coverage against actual messages still needs the ground-truth method in §9.

**Note on the missing screen labels:** the log records package, counts and outcome, but the app never
records *which* screen was open — by design, since that would mean storing conversation context. R2
and R3 are labelled from what the researcher reported, not from device evidence; R1 and R4–R8 are
unlabelled. Treat those rows as measurements of *a* window, not of a named screen.

Planned coverage:

| ID | Objective | Status |
|---|---|---|
| RC-01 | Google Messages conversation-list screen | **Run as R5/R7/R8 — label unconfirmed.** Three Messages windows at 46 nodes / depth 10 / 7 classes were measured, so the screen was consistent; the researcher has not yet confirmed it is the conversation list, so the row cannot be titled one |
| RC-02 | Google Messages open thread | **Likely run as R6 — label unconfirmed.** 20 nodes but 8 text nodes and 135 characters is the text-dense, chrome-light shape of an open thread. Needs the researcher to confirm before it counts |
| RC-03 | Isolation check: arm, then foreground a non-target app | **Pass — observed by accident, then confirmed deliberately.** An inspection armed at 15:10:32 for `com.instagram.android` produced nothing while `com.instagram.lite` was foreground; it stayed armed and only completed after the target was switched and re-armed. A near-identical package name is not close enough to satisfy the check |
| RC-04 | Null-root behaviour on a transition frame | Pending — all eight runs returned `root=true`, so the null-root path has never fired on a real device. It is unit-tested only |
| RC-05 | Second messaging app, same screen type, for cross-app comparison | **Pass for Instagram vs Instagram Lite (R2 vs R3): 80 nodes / 205 chars / depth 5 / 5 classes vs 55 / 245 / depth 6 / 8.** Two builds of the *same product* differ this much in shape, which is the argument for never generalising from one row. The Messages leg stays provisional until R4–R8 are labelled |

The mechanism itself is exercised by 57 JVM unit tests
([06_TESTING_AND_RESULTS.md](06_TESTING_AND_RESULTS.md)), including the target-capture,
wrong-package, null-root and redaction cases. What unit tests cannot establish is how a specific
real app behaves; that is exactly what this table is waiting for.

---

## 7. Why the harness did not enable the service

An earlier attempt to run this experiment automatically used
`adb shell settings put secure enabled_accessibility_services …`. It was rejected, correctly:
that is programmatic grant of an accessibility permission, which is the one safeguard this design
refuses to bend. The app itself never calls it, and a test that side-steps the consent gate would
be measuring a different, non-consented system. RC-01 onward therefore requires a human toggle,
which is also what the BTP panel needs to see demonstrated live.

---

## 8. Compatibility testing is not model evaluation

These are two different experiments and the panel will ask about the difference:

| | Real-app compatibility (this document) | Model evaluation (future work) |
|---|---|---|
| Input | an accessibility tree on a device | text the researcher is authorized to use |
| Output | counts, depth, class names | a model input string, then a prediction |
| Ground truth | none required | labelled conversations required |
| Claim supported | "the structure is reachable" | "the detector detects" |
| Status | implemented; observation pending | model not integrated |

Preprocessing of actual text happens only on the **Research-authorized text import** screen, from
content the researcher pastes, held in memory and never logged or stored. That screen is where
conversation content belongs; the accessibility path is structurally incapable of reaching it
(see [04_ACCESSIBILITY_INSPECTION_FLOW.md](04_ACCESSIBILITY_INSPECTION_FLOW.md)).

---

## 9. Measuring extraction coverage (the honest way)

"Text-bearing nodes = 41" is not coverage. Coverage needs ground truth:

1. Screenshot-free manual transcription of one thread by the researcher, who consents to their own
   data being the reference.
2. Count turns in that transcript: `T`.
3. Run one armed inspection on the same screen and record text-bearing node count `B`.
4. Report `B/T` as an **upper bound on turn coverage**, with the caveat that `B` counts every
   labelled node — timestamps rendered as text, sender names, unread badges, typing indicators,
   and system notices are all inside it.
5. Repeat across screens and app versions; report the spread, never a single number.

Until step 1 exists, no coverage percentage may be quoted in the report or the presentation.

---

## 10. Privacy statement for this document

The report contains no message text, no sender name, no contact name, no account identifier, and
no conversation content. It is held in memory only — never written to a file, preference, database,
log, analytics endpoint, or network by this app (there is no `INTERNET` permission). Copying it
into a lab notebook is the researcher's action, outside the app.

---

Next: [08_LIMITATIONS_AND_FUTURE_WORK.md](08_LIMITATIONS_AND_FUTURE_WORK.md).
