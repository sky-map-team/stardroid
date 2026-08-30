/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteFetchPolicy
import com.google.android.stardroid.startup.Experiment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * What [SatelliteRefreshWorker] should tell WorkManager after a fetch.
 *
 * A separate type from `ListenableWorker.Result` purely so the decision is testable on the JVM;
 * [FAIL] exists to be asserted unreachable rather than to be used.
 */
enum class SatelliteWorkAction {
    SUCCEED,
    RETRY,
    FAIL,
}

/**
 * Translates a [FetchOutcome] into what the worker should do — the single most consequential
 * decision in the satellite feature, factored out so it can be pinned by a unit test.
 *
 *  - **Success / NotModified → succeed.** Nothing to do.
 *  - **HttpError → succeed, never retry.** CelesTrak refused us, and retrying is the storm the
 *    usage policy forbids: the breaker already holds the door shut, and the 12-hour job fires
 *    again on schedule and no-ops against it.
 *  - **TransportFailure → retry.** Never reached them; a legitimate transient.
 *  - **UnusableBody → succeed.** A captive portal, not CelesTrak; retrying hammers the portal.
 *  - **StorageFailure → retry.** Our disk, not their server.
 *
 * Nothing maps to [SatelliteWorkAction.FAIL]: a permanently failed periodic job stops running,
 * which would silently kill the feature with no way back short of a reinstall — and "stop asking"
 * is already expressed by the circuit breaker, in a form that recovers on its own.
 */
fun satelliteWorkAction(outcome: FetchOutcome): SatelliteWorkAction =
    when (outcome) {
        is FetchOutcome.Success, FetchOutcome.NotModified -> SatelliteWorkAction.SUCCEED
        // Never RETRY here. This is the single most important line in the feature.
        is FetchOutcome.HttpError -> SatelliteWorkAction.SUCCEED
        is FetchOutcome.UnusableBody -> SatelliteWorkAction.SUCCEED
        is FetchOutcome.TransportFailure -> SatelliteWorkAction.RETRY
        is FetchOutcome.StorageFailure -> SatelliteWorkAction.RETRY
    }

/**
 * The periodic CelesTrak fetch (D92), scheduled by [SatelliteScheduler].
 *
 * Its one consequential decision — what to tell WorkManager after a fetch — lives in
 * [satelliteWorkAction], which is pure and unit-tested. Letting WorkManager's retry machinery
 * drive requests at CelesTrak is the failure mode the entire circuit breaker exists to prevent,
 * so that mapping should not be inferred from a scattering of `return` statements.
 *
 * What remains here is the surrounding work: re-checking both gates at run time, reporting server
 * refusals as CelesTrak's policy requires, and keeping anything from escaping as an exception.
 */
class SatelliteRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val entryPoint get() = satelliteEntryPoint(applicationContext)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            // Both gates re-checked at run time, not just at schedule time: a Remote Config
            // flip or a withdrawn consent must stop traffic even if a job is already enqueued.
            if (!entryPoint.experimentConfig().isEnabled(Experiment.SATELLITES)) {
                return@withContext Result.success()
            }
            if (!entryPoint.settings().satelliteDataEnabled.first()) {
                return@withContext Result.success()
            }
            val repository = entryPoint.satelliteElementsRepository()
            val analytics = entryPoint.analytics()

            // The repository is total by construction (D95), but a worker that throws is a
            // worker that stops running, so this is belt and braces at the last boundary.
            val result =
                runCatching { repository.refresh() }
                    .getOrElse {
                        Log.w(TAG, "Satellite refresh failed unexpectedly", it)
                        return@withContext Result.retry()
                    }

            when (result) {
                is RefreshResult.Skipped -> {
                    // The breaker is open or the minimum interval has not elapsed. Not an
                    // error: the schedule simply outran the policy, which is by design.
                    Log.i(
                        TAG,
                        "Satellite refresh skipped; next allowed in ${result.retryAfter}",
                    )
                    Result.success()
                }

                is RefreshResult.Completed -> handle(result, analytics)
            }
        }

    private fun handle(
        result: RefreshResult.Completed,
        analytics: Analytics,
    ): Result {
        report(result, analytics)

        if (result.outcome is FetchOutcome.Success ||
            result.outcome is FetchOutcome.NotModified
        ) {
            reportCircuitClosedIfItWas(result, analytics)
        }

        return when (satelliteWorkAction(result.outcome)) {
            SatelliteWorkAction.SUCCEED -> Result.success()
            SatelliteWorkAction.RETRY -> Result.retry()
            // Unreachable by construction, and asserted so; mapped rather than thrown because
            // a worker that throws is a worker that stops running.
            SatelliteWorkAction.FAIL -> Result.success()
        }
    }

    /**
     * Reports a server refusal, as CelesTrak's policy requires — to the log for a developer,
     * and to analytics for the aggregate signal that stands in for logs we cannot see.
     */
    private fun report(
        result: RefreshResult.Completed,
        analytics: Analytics,
    ) {
        val outcome = result.outcome
        if (!SatelliteFetchPolicy.warrantsReporting(outcome)) return
        val statusCode = (outcome as FetchOutcome.HttpError).statusCode

        if (SatelliteFetchPolicy.isTerminal(outcome)) {
            // 301 means our URL is outdated and only a new release fixes it, so this is the
            // one case that genuinely warrants shouting.
            Log.e(
                TAG,
                "CelesTrak returned 301: the element-set URL is outdated and no retry will " +
                    "fix it. Sky Map needs a release with the new URL.",
            )
        } else {
            Log.w(
                TAG,
                "CelesTrak returned $statusCode; circuit open until " +
                    "${result.state.circuitOpenUntil}",
            )
        }

        analytics.trackEvent(
            AnalyticsEvents.SATELLITE_FETCH_FAILED_EVENT,
            mapOf(
                AnalyticsEvents.SATELLITE_FETCH_HTTP_CODE to statusCode.toString(),
                AnalyticsEvents.SATELLITE_FETCH_CIRCUIT_OPENED to
                    result.state.circuitIsOpen.toString(),
                AnalyticsEvents.SATELLITE_FETCH_CIRCUIT_OPEN_UNTIL to
                    result.state.circuitOpenUntil.toString(),
            ),
        )
    }

    /**
     * Reports recovery, if this success closed a circuit that had been open.
     *
     * Read from [RefreshResult.Completed.previousState], because the success has already
     * cleared the circuit by the time this runs. Paired with the failure event, this is what
     * would show — in aggregate, across an install base whose logs we cannot see — that a
     * breaker opened in production and how long it held.
     *
     * "How long it was open" is measured from the attempt that opened it, which is the last
     * attempt recorded before this one.
     */
    private fun reportCircuitClosedIfItWas(
        result: RefreshResult.Completed,
        analytics: Analytics,
    ) {
        val previous = result.previousState
        if (!previous.circuitIsOpen) return
        val openedAt = previous.lastAttempt ?: return
        analytics.trackEvent(
            AnalyticsEvents.SATELLITE_CIRCUIT_CLOSED_EVENT,
            mapOf(
                AnalyticsEvents.SATELLITE_CIRCUIT_OPEN_HOURS to
                    (Clock.System.now() - openedAt).inWholeHours.toString(),
            ),
        )
    }

    private companion object {
        const val TAG = "SatelliteRefresh"
    }
}
