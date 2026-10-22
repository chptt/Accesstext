package com.example.groomingresearchdemo.state

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.groomingresearchdemo.AppConstants
import com.example.groomingresearchdemo.inspection.AccessibilityNodeView
import com.example.groomingresearchdemo.inspection.ArmResult
import com.example.groomingresearchdemo.inspection.ArmStatus
import com.example.groomingresearchdemo.inspection.ConsentStatus
import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.InspectionController
import com.example.groomingresearchdemo.inspection.InspectionResult
import com.example.groomingresearchdemo.inspection.extraction.InspectionLadder
import com.example.groomingresearchdemo.inspection.extraction.Reconstruction
import com.example.groomingresearchdemo.service.ChatInspectionService
import com.example.groomingresearchdemo.service.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Application-scoped single source of truth for the research demo.
 *
 * The AccessibilityService and the Compose UI both live in the same process and reach the same
 * instance of this object. All state transitions go through [InspectionController], which is
 * the pure-Kotlin one-shot state machine that unit tests exercise directly.
 *
 * PERSISTENCE SURFACE — exactly two keys, both non-sensitive:
 *   * `consent_status` — the user's consent decision.
 *   * `target_package` — the package name chosen for the compatibility experiment.
 * Inspection statistics and the compatibility reports live only in memory, so a process restart
 * clears them.
 *
 * SINCE THE MESSAGE-LEVEL STAGE WAS ADDED, THE HONEST VERSION OF THE LAST SENTENCE IS:
 * the structural path still never produces message content at all, and the extraction path produces
 * it into exactly one in-memory field, [retainedExtraction], which is never written to a preference,
 * a file, a database, a log line, a report, or a network — there is no network permission to write a
 * network. It is overwritten by the next inspection and cleared by cancellation, consent revocation,
 * `clearResults`, service disconnect, and the explicit forget action. Counts reach every other
 * surface; content reaches none of them.
 */
object ResearchStore {

    private const val PREFS_NAME = "grooming_research_demo"
    private const val KEY_CONSENT = "consent_status"
    private const val KEY_TARGET_PACKAGE = "target_package"

    private val controller = InspectionController()

    private val _uiState = MutableStateFlow(ResearchUiState())
    val uiState: StateFlow<ResearchUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    /** Structural results from this session only, newest first. Never written to disk. */
    private val sessionReports = mutableListOf<InspectionResult>()

    /**
     * THE ONE RETAINED EXTRACTION, AND WHY IT IS ONE.
     *
     * When the researcher opts into message-level extraction, the reconstructed records have to live
     * somewhere or the results are useless. They live here: a single reference, in memory, guarded by
     * the same lock as the report list, and never copied into any other field.
     *
     * It is *one* rather than a list because retention should never exceed the scope of the consent
     * that produced it: one armed inspection, one authorised screen, one set of records. The next
     * inspection overwrites it and the previous records become garbage immediately — they are not
     * archived, not appended, and not kept "for comparison".
     *
     * Cleared by: a new inspection, cancellation, consent revocation, `clearResults`, the explicit
     * forget action, and service disconnect. A process death clears it by definition; nothing here is
     * serialised. Every published `InspectionResult` and every session report holds a record-free
     * copy, so the only place message text exists in this process is this single field.
     */
    private var retainedExtraction: Reconstruction? = null

    /**
     * Set by [ChatInspectionService] so it can narrow its own `serviceInfo.packageNames` when the
     * researcher picks a different test app while the service is already connected.
     */
    @Volatile
    var onTargetPackageChanged: ((String) -> Unit)? = null

    /**
     * Set by the AccessibilityService in onServiceConnected / onDestroy. Combined with the
     * active AccessibilityManager query this gives a truthful, verified view of whether the
     * service is enabled — the UI never fakes this.
     */
    @Volatile
    private var serviceBoundBySystem: Boolean = false

    /** Called from [android.app.Application.onCreate]. Safe to call more than once. */
    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val store = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        val stored = store.getString(KEY_CONSENT, ConsentStatus.NOT_GIVEN.name)
        val restored = runCatching {
            ConsentStatus.valueOf(stored ?: ConsentStatus.NOT_GIVEN.name)
        }.getOrDefault(ConsentStatus.NOT_GIVEN)
        val storedTarget = store.getString(KEY_TARGET_PACKAGE, AppConstants.DEFAULT_TARGET_PACKAGE)
            ?.takeIf { it.isNotBlank() } ?: AppConstants.DEFAULT_TARGET_PACKAGE

        _uiState.update {
            it.copy(
                consentStatus = restored,
                targetPackage = storedTarget,
                serviceEnabled = computeServiceEnabled(appCtx),
            )
        }
        LogUtils.i(
            "ResearchStore initialised. consent=${restored.name} " +
                "target=$storedTarget serviceEnabled=${_uiState.value.serviceEnabled}",
        )
    }

    // ---- Consent ---------------------------------------------------------------------

    fun acceptConsent() {
        persistConsent(ConsentStatus.ACCEPTED)
        LogUtils.i("Consent ACCEPTED.")
    }

    fun declineConsent() {
        persistConsent(ConsentStatus.DECLINED)
        controller.revokeAndClear()
        synchronized(sessionReports) {
            sessionReports.clear()
            retainedExtraction = null
        }
        publishController()
        LogUtils.i("Consent DECLINED. Inspection stays disabled.")
    }

    /**
     * Consent revocation must cancel any pending inspection AND clear local results. This is
     * enforced in [InspectionController.revokeAndClear]; the session reports and the one retained
     * extraction are wiped here, because withdrawing consent cannot leave message text sitting in a
     * process that was consented to under the earlier state.
     */
    fun revokeConsent() {
        persistConsent(ConsentStatus.NOT_GIVEN)
        controller.revokeAndClear()
        synchronized(sessionReports) {
            sessionReports.clear()
            retainedExtraction = null
        }
        publishController()
        LogUtils.i("Consent REVOKED. Pending arm cancelled, local results and retained content cleared.")
    }

    private fun persistConsent(status: ConsentStatus) {
        prefs?.edit()?.putString(KEY_CONSENT, status.name)?.apply()
        _uiState.update { it.copy(consentStatus = status) }
    }

    // ---- Compatibility target --------------------------------------------------------

    /**
     * Choose which messaging app the next inspection targets. A package name is structural
     * configuration, not user content, so it is the only non-consent value we persist.
     */
    fun setTargetPackage(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return
        prefs?.edit()?.putString(KEY_TARGET_PACKAGE, trimmed)?.apply()
        _uiState.update { it.copy(targetPackage = trimmed) }
        onTargetPackageChanged?.invoke(trimmed)
        LogUtils.i("Compatibility target set to $trimmed.")
    }

    /**
     * Whether the chosen test app is actually installed. Uses PackageManager, which is scoped by
     * the manifest `<queries>` declaration to the small allowlist of messaging packages this
     * experiment may target.
     */
    fun isPackageInstalled(packageName: String): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            ctx.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    // ---- Service status --------------------------------------------------------------

    /**
     * Two sources combined: (a) whether the system has currently bound an instance of
     * [ChatInspectionService] and (b) a live query to [AccessibilityManager] for the enabled
     * list. If either is true, the service is enabled. If neither is, we say so honestly and
     * never pretend otherwise in the UI.
     */
    fun refreshServiceStatus() {
        val ctx = appContext ?: return
        _uiState.update { it.copy(serviceEnabled = computeServiceEnabled(ctx)) }
    }

    private fun computeServiceEnabled(ctx: Context): Boolean {
        if (serviceBoundBySystem) return true
        return isServiceEnabledInAccessibilityManager(ctx) || isServiceListedInSecureSettings(ctx)
    }

    /**
     * Fallback check that reads the system's enabled-services string. It lets "Re-check status"
     * report enablement correctly in the brief window after the user toggles the service in
     * settings but before the framework has finished binding our [ChatInspectionService].
     */
    private fun isServiceListedInSecureSettings(ctx: Context): Boolean {
        val expected = ComponentName(ctx, ChatInspectionService::class.java)
        val raw = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        return raw.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry)
            component != null &&
                component.packageName == expected.packageName &&
                component.className == expected.className
        }
    }

    private fun isServiceEnabledInAccessibilityManager(ctx: Context): Boolean {
        val manager = ContextCompat.getSystemService(ctx, AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(ctx, ChatInspectionService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                    serviceInfo.packageName == expected.packageName &&
                    serviceInfo.name == expected.className
            }
    }

    /** Called by [ChatInspectionService] in onServiceConnected. */
    fun notifyServiceConnected() {
        serviceBoundBySystem = true
        // Never re-arm on reconnect: the process may have been restarted mid-flight.
        controller.forceDisarm()
        _uiState.update { it.copy(serviceEnabled = true) }
        publishController()
        LogUtils.i("Accessibility service CONNECTED. Arm forced to NOT_ARMED.")
    }

    /** Called by [ChatInspectionService] in onDestroy. */
    fun notifyServiceDisconnected() {
        serviceBoundBySystem = false
        controller.forceDisarm()
        // The collection channel is gone, so anything it produced is orphaned. Dropping the retained
        // records here means "the service was turned off" and "message text is still in RAM" can never
        // be true of the same moment.
        synchronized(sessionReports) { retainedExtraction = null }
        val ctx = appContext
        val stillEnabled = ctx != null && isServiceEnabledInAccessibilityManager(ctx)
        _uiState.update { it.copy(serviceEnabled = stillEnabled) }
        publishController()
        LogUtils.i("Accessibility service DISCONNECTED (stillEnabledInSettings=$stillEnabled).")
    }

    // ---- Inspection lifecycle --------------------------------------------------------

    /**
     * Attempt to arm a one-shot inspection against the currently selected test app. The
     * controller enforces the consent, service and target gates before the arm flag flips.
     *
     * @param messageLevelExtraction the researcher's separate, deliberate opt-in to the
     *   message-level stage. It is captured at arm time along with the target, so ticking the box
     *   after arming cannot widen an inspection that was already consented to — and leaving it
     *   unticked means the tree is walked for counts only, exactly as it was for R1-R8.
     */
    fun startInspection(messageLevelExtraction: Boolean = false): ArmResult {
        val state = _uiState.value
        val result = controller.arm(
            consentAccepted = state.consentStatus == ConsentStatus.ACCEPTED,
            serviceEnabled = state.serviceEnabled,
            targetPackage = state.targetPackage,
            messageLevelExtraction = messageLevelExtraction,
        )
        publishController()
        LogUtils.i(
            "startInspection -> ${result.name} (target=${state.targetPackage} " +
                "extractionOptIn=${messageLevelExtraction && result == ArmResult.ARMED})",
        )
        return result
    }

    fun cancelInspection(): Boolean {
        val changed = controller.cancel()
        if (changed) forgetExtractedContent()
        publishController()
        LogUtils.i("cancelInspection -> $changed")
        return changed
    }

    fun clearResults() {
        controller.clearResults()
        synchronized(sessionReports) {
            sessionReports.clear()
            retainedExtraction = null
        }
        publishController()
        LogUtils.i("Local results, session reports and any retained extraction cleared.")
    }

    /**
     * Drops the one retained reconstruction.
     *
     * This is the "clear temporary extracted content" requirement made callable. It is wired to the
     * review screen's own button, to cancellation, to consent revocation, to `clearResults`, and to
     * service disconnect, and it is the reason a researcher can finish looking at a sample and leave
     * nothing behind. Counts survive in the session reports; content does not, anywhere.
     */
    fun forgetExtractedContent() {
        synchronized(sessionReports) { forgetExtractedContentLocked() }
        publishController()
        LogUtils.i("Retained message-level content forgotten. Counts remain.")
    }

    private fun forgetExtractedContentLocked() {
        retainedExtraction = null
    }

    /**
     * The retained records, for the one deliberate review action. Returns null whenever nothing is
     * held. Not called by the summary screens, the report builder, or the logger — by construction
     * those read the record-free copy on `InspectionResult` instead.
     */
    fun peekRetainedExtraction(): Reconstruction? = synchronized(sessionReports) { retainedExtraction }


    /**
     * Entry point used by [ChatInspectionService] when the OS delivers an event. The
     * `rootProvider` lambda is passed straight to the controller, which only calls it after the
     * arm + package gates have been checked. That keeps the target app's active-window tree
     * completely unopened for every event that does not correspond to a real armed inspection.
     */
    fun handleAccessibilityEvent(
        packageName: String?,
        rootProvider: () -> AccessibilityNodeView?,
    ): InspectionResult? = handleAccessibilityEvent(packageName, null, rootProvider)

    /**
     * Token for the arm that a qualifying event belongs to, or null when nothing is armed for this
     * package. The service holds this across its short settle delay and hands it back, so a read
     * that was queued for one arm can never be applied to a later one.
     */
    fun armTokenFor(packageName: String?): Long? = controller.armTokenFor(packageName)

    fun handleAccessibilityEvent(
        packageName: String?,
        expectedArmToken: Long?,
        rootProvider: () -> AccessibilityNodeView?,
    ): InspectionResult? {
        val produced = controller.handleEvent(packageName, expectedArmToken, rootProvider) ?: return null
        val fullExtraction = produced.extraction

        // Only ONE reconstruction is ever held, and it is replaced before anything is published, so
        // an inspection cannot accumulate content across runs. The session list and the UI state get
        // the record-free copy; the full copy is reachable only through peekRetainedExtraction().
        val published = if (fullExtraction == null) produced else produced.copy(
            extraction = fullExtraction.withoutRecords(),
        )

        synchronized(sessionReports) {
            retainedExtraction = fullExtraction
            if (published.outcome == ExperimentOutcome.COMPLETED ||
                published.outcome == ExperimentOutcome.FAILED
            ) {
                sessionReports.add(0, published)
                while (sessionReports.size > AppConstants.MAX_REPORTS_KEPT) {
                    sessionReports.removeAt(sessionReports.lastIndex)
                }
            }
        }

        publishController()
        logResult(published)
        return published
    }

    private fun logResult(result: InspectionResult) {
        // Never include node text here. Only structural, non-sensitive numbers.
        val stats = result.stats
        val extraction = result.extraction
        LogUtils.i(
            "Inspection outcome=${result.outcome.name} target=${result.targetPackage} " +
                "event=${result.eventReceived} packageMatched=${result.packageMatched} " +
                "root=${result.rootAvailable} " +
                "nodes=${stats?.nodesVisited ?: "-"} textBearing=${stats?.textBearingNodes ?: "-"} " +
                "withText=${stats?.nodesWithText ?: "-"} descOnly=${stats?.nodesWithContentDescriptionOnly ?: "-"} " +
                "emptyText=${stats?.emptyOrUnavailableTextNodes ?: "-"} " +
                "chars=${stats?.totalTextCharacters ?: "-"} depth=${stats?.maxDepth ?: "-"} " +
                "classes=${stats?.distinctNodeClasses ?: "-"} unreadable=${stats?.unreadableChildNodes ?: "-"} " +
                "truncated=${stats?.truncated ?: "-"}"
        )
        if (extraction != null) {
            // Message-level counts, and deliberately NOT a record, NOT a length of record text, NOT
            // a reason string that could quote content. Reasons are generated by fixed code paths and
            // are safe, but they stay on the results screen rather than in Logcat: the smaller the
            // surface that could ever carry a substring of a node's text, the better.
            LogUtils.i(
                "Extraction status=${extraction.status} sampled=${extraction.nodesSampled} " +
                    "candidates=${extraction.candidateCount} accepted=${extraction.acceptedCount} " +
                    "ambiguous=${extraction.ambiguousCount} rejected=${extraction.rejectedCount} " +
                    "dupesSuppressed=${extraction.duplicateVisitsSuppressed} " +
                    "echoesSuppressed=${extraction.containerEchoesSuppressed} " +
                    "order=${extraction.orderBasis} speakers=${extraction.speakerAttribution} " +
                    "timestamps=${extraction.timestampsRecovered} partial=${extraction.isPartialVisibleSample}",
            )
        }
    }

    private fun publishController() {
        val reports = synchronized(sessionReports) { sessionReports.toList() }
        val retained = synchronized(sessionReports) { retainedExtraction }
        _uiState.update {
            it.copy(
                armStatus = controller.armStatus,
                armedTarget = controller.armedTarget,
                armedExtraction = controller.armedExtraction,
                lastResult = controller.lastResult,
                completedCount = controller.completedCount,
                reports = reports,
                hasRetainedContent = retained != null && retained.records.isNotEmpty(),
                ladder = InspectionLadder.of(controller.lastResult),
            )
        }
    }

    // Testability seam: expose arm status without a full state read.
    val currentArmStatus: ArmStatus get() = controller.armStatus
}
