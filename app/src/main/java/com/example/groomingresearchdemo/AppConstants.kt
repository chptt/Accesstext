package com.example.groomingresearchdemo

/** Central, non-sensitive constants shared across the app. */
object AppConstants {
    /**
     * Instagram remains the original research target and the default, but the experiment now
     * supports any messaging app the researcher installs manually. See [TARGET_OPTIONS].
     */
    const val INSTAGRAM_PACKAGE = "com.instagram.android"

    /**
     * Instagram Lite ships as its own package (`com.instagram.lite`), separate from
     * [INSTAGRAM_PACKAGE]. A device with only Lite installed will never match an inspection
     * armed against `com.instagram.android`, so both are allowlisted.
     */
    const val INSTAGRAM_LITE_PACKAGE = "com.instagram.lite"

    /** Pre-selected on first launch, before the researcher chooses a test app. */
    const val DEFAULT_TARGET_PACKAGE = INSTAGRAM_PACKAGE

    /**
     * Messaging apps the researcher may choose as the compatibility target. This list is also
     * written into `res/xml/accessibility_service_config.xml` as the platform-level package
     * allowlist and into the manifest `<queries>` block, so the app can only ever receive
     * accessibility events for these packages — never for an arbitrary app.
     */
    val TARGET_OPTIONS: List<TargetOption> = listOf(
        TargetOption("Google Messages", "com.google.android.apps.messaging"),
        TargetOption("Instagram", INSTAGRAM_PACKAGE),
        TargetOption("Instagram Lite", INSTAGRAM_LITE_PACKAGE),
        TargetOption("WhatsApp", "com.whatsapp"),
        TargetOption("Telegram", "org.telegram.messenger"),
        TargetOption("Signal", "org.thoughtcrime.securesms"),
    )

    /** A selectable compatibility target. [packageName] is not sensitive data. */
    data class TargetOption(val label: String, val packageName: String)

    /** Single Logcat tag. Only structural, non-sensitive information is ever logged to it. */
    const val LOG_TAG = "GroomingResearchDemo"

    /**
     * Safety cap on how many accessibility nodes we will visit in a single traversal.
     * Real messaging windows are far below this, but the cap prevents a pathological or
     * unexpectedly deep/cyclic tree from spinning forever.
     */
    const val MAX_TRAVERSAL_NODES = 5000

    /** Bound on the node-class histogram so a hostile tree cannot grow the report unboundedly. */
    const val MAX_CLASS_HISTOGRAM_KEYS = 40

    /** Bound on structural notes attached to one inspection. */
    const val MAX_NOTES = 6

    /** How many redacted compatibility reports are kept in memory for the session. */
    const val MAX_REPORTS_KEPT = 20
}
