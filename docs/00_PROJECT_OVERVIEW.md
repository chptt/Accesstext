# 00 — Project Overview

Read this first. It explains the whole project in plain language and gives you a short script
you can say to your review panel.

---

## 1. What problem the BTP addresses

Online grooming is when an adult builds an emotional relationship with a minor online in order
to exploit them. Detecting it automatically is a text-classification problem: given a
conversation, decide whether its pattern of messages looks like grooming. To build such a
detector you need three things working together:

1. A way to **obtain conversation text** on a phone (the hard, privacy-sensitive part).
2. A **preprocessing pipeline** that turns raw, messy chat turns into clean, consistent text a
   model can read.
3. A **trained model** that scores the preprocessed text.

This project builds and demonstrates parts (1) and (2) as an Android application, and prepares a
clean seam for part (3).

## 2. What "online grooming detection" means in this research

In this demo, grooming detection means: take a multi-turn conversation, normalise it, and hand a
model a representation that preserves *who said what, in what order*. The detector would then
look at the **context across messages** (escalation, secrecy requests, age-disclosure reactions)
rather than single words. This project does **not** ship a trained detector — it ships the
honest scaffolding around one.

## 3. Why text extraction and preprocessing matter

Raw chat text is messy: repeated spaces, blank turns, emoji, mixed languages (e.g. Malayalam and
English), and legitimate repeated messages. A model is sensitive to these details. Preprocessing
must clean the text **without destroying signal** — for example, you must not delete a repeated
"ok", because repetition can itself be meaningful, and you must not transliterate or lowercase if
the model was trained on the original characters.

## 4. What this particular Android experiment demonstrates

- A **consent workflow** that must be completed before anything happens.
- A **manually armed, one-shot** compatibility experiment: the user chooses which messaging app is
  under test, and the app inspects the accessibility tree of that app's active window **once**,
  reporting **structural observations only** — nodes visited, how many expose a label, how many
  expose only a content description, total labelled characters, text-availability percentage, the
  widget-class histogram, maximum depth, truncation, and any per-node errors.
- A **redacted compatibility report** you can copy and paste into your write-up. It contains numbers
  and class names. A unit test proves it cannot contain message text.
- A **separate "research-authorized text" import screen** for content-processing tests, clearly
  labelled as pasted input that was **not** observed from the app under test.
- A **preprocessing laboratory** that runs on **synthetic** conversations and produces a copyable,
  model-ready string.
- A **model adapter seam** that truthfully reports **"Model not integrated"** until you connect your
  real classifier.

## 5. What the experiment does NOT demonstrate

- It does **not** extract or reconstruct conversations from any app.
- It does **not** prove that any given app can be monitored — it measures *whether text is exposed
  at all*, and an inaccessible screen is a valid, expected result.
- It does **not** prove any accuracy of grooming detection.
- It does **not** run a real ML model or produce predictions.
- It does **not** continuously monitor the screen.

## 6. The four distinct stages (know these names)

| Stage | What it is | Where it lives in this app |
|---|---|---|
| Accessibility **inspection** | Counting nodes, labels, classes and depth of the active window's tree | `service/ChatInspectionService.kt` + `inspection/StructuralInspector.kt` |
| Message **extraction** | Turning nodes into individual messages/speakers/times | **Not implemented** — deliberately, because it is unreliable and privacy-invasive |
| **Preprocessing** | Cleaning + ordering + serialising text | `model/ConversationPreprocessor.kt` |
| Model **inference** | Scoring the text | **Placeholder only** — `model/GroomingModelAdapter.kt` |

The single most important conceptual point for your panel: **inspection ≠ extraction ≠
preprocessing ≠ inference.** This project implements inspection (structurally) and preprocessing
(on synthetic data), and leaves extraction and inference as honest gaps.

## 7. Why an Android emulator

An emulator gives a reproducible, safe device to install the debug APK on and to demonstrate the
consent flow, the service-enable flow, and the results screen without touching a real phone or a
real person's account.

## 8. The role of the app under test

The experiment is no longer Instagram-only. `AppConstants.TARGET_OPTIONS` offers a small allowlist —
Google Messages, Instagram, Instagram Lite, WhatsApp, Telegram, Signal — and the user picks which one
is under test. Instagram and Instagram Lite are separate packages (`com.instagram.android` vs
`com.instagram.lite`), so choosing the wrong variant means an inspection can never match the app on
screen; the target screen marks each one installed / not installed for exactly this reason.
The point is to measure whether, and how much of, a real third-party screen exposes its structure to
Android's accessibility API. Three independent layers keep the boundary tight:

1. `res/xml/accessibility_service_config.xml` declares `packageNames` for the whole allowlist, so the
   **platform** never delivers events from any other app.
2. When you choose an app, `ChatInspectionService.applyTargetFilter()` narrows that set to the single
   chosen package with `setServiceInfo()` — the runtime filter only ever gets **narrower**.
3. `InspectionController` compares each event against `armedTarget`, the package captured at the
   moment you pressed Start.

On top of that, the root-node provider is lazy, so a non-qualifying event never opens a tree.
It never reads message text.

## 9. Why three kinds of text must not be confused

| Kind | Where it comes from | What it proves |
|---|---|---|
| Live-app **structure** | The accessibility tree of the app under test | Only that a screen exposes labels — counts, depth, class names. No content ever leaves the traversal. |
| **Synthetic** fixtures | `model/SampleConversations.kt` | The preprocessing pipeline is correct, repeatably, with zero privacy risk. |
| **Research-authorized** pasted text | The import screen, typed/pasted by the researcher | Content processing on text the researcher is permitted to use. Never observed from the app. |

Message-level preprocessing needs real conversation text to demonstrate, and using anyone's real DMs
without consent would be unethical and out of scope. Synthetic conversations let us prove the
pipeline (whitespace, emoji, Malayalam, ordering, duplicates, blanks) with full control and zero
privacy risk; the import screen exists so an authorised sample is kept visibly separate from both.

## 10. Expected outcome

A reviewer can watch: consent accepted → service enabled → app under test chosen and verified as
installed → one inspection armed → switch to that app → structural counts, text availability and
widget classes appear → the redacted compatibility report is shown and copied → the preprocessing
lab cleans a synthetic conversation → the import screen processes authorised text with a warning
banner → the model line says "Model not integrated". That is a complete, honest demonstration of the
research scaffolding, including a "the app exposes nothing" result if that is what happens.

## 11. Limitations to acknowledge up front

See [08_LIMITATIONS_AND_FUTURE_WORK.md](08_LIMITATIONS_AND_FUTURE_WORK.md) for the full list. In
short: node counts are not message counts; text may be unavailable; extraction is not solved; and
there is no trained model. The real-app compatibility run itself is recorded separately: one
observation (R1) has now been measured on Instagram and shows structure without any message-text
surface; the remaining rows stay **PENDING** until executed on device — see
[11_REAL_APP_COMPATIBILITY_REPORT.md](11_REAL_APP_COMPATIBILITY_REPORT.md).

---

## Your 60–90 second panel introduction (memorise the shape, not the words)

> "My project is a consent-based Android research demo for online grooming detection. It has two
> independent halves. The first half is a privacy-safe **accessibility compatibility experiment**:
> after the user accepts a disclosure and manually enables an AccessibilityService, they choose a
> messaging app from a small allowlist and arm a **single, one-time** inspection of that app's active
> window. The app reports only structural observations — how many accessibility nodes there are, how
> many expose a label, the widget classes it saw, and the maximum tree depth — and never stores,
> logs, or transmits message content; a unit test proves the report cannot contain it. An
> inaccessible screen is a valid result, not a failure. The second half is a **text-preprocessing
> laboratory** that runs on fully synthetic conversations — plus a separate import screen for
> research-authorized text, clearly marked as *not* observed from the app — cleaning whitespace,
> emoji, Malayalam, ordering, blank and repeated messages into one model-ready string. A model
> adapter sits at the end and honestly reports 'Model not integrated' until I connect my trained
> classifier. So the app demonstrates the measurement and the preprocessing a real detector needs,
> while being explicit about what it does not do: it does not extract conversations and it does not
> run a model."

Next: read [02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md](02_ANDROID_ACCESSIBILITY_FUNDAMENTALS.md).
