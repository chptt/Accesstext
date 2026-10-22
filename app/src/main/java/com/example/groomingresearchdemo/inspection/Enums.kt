package com.example.groomingresearchdemo.inspection

/**
 * The five concerns the spec insists must stay separate. Each is modelled explicitly so we
 * never collapse "consented + service on + armed + running + has-result" into one Boolean.
 */

/** Whether the user has given, refused, or not yet made the consent decision. */
enum class ConsentStatus { NOT_GIVEN, ACCEPTED, DECLINED }

/** The one-shot inspection lifecycle. */
enum class ArmStatus { NOT_ARMED, ARMED, EXECUTING }

/** Terminal outcome of the most recent inspection attempt. */
enum class ExperimentOutcome { NONE, COMPLETED, FAILED, CANCELLED }

/** Result of asking to arm an inspection. */
enum class ArmResult { ARMED, CONSENT_REQUIRED, SERVICE_REQUIRED, TARGET_REQUIRED, ALREADY_ARMED }
