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
import com.google.android.stardroid.data.satellites.FetchOutcome
import com.google.android.stardroid.data.satellites.RefreshResult
import com.google.android.stardroid.data.satellites.SatelliteFetchPolicy
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

internal val SATELLITE_FLAG_TIMEOUT: Duration = 10.seconds

/**
 * The kill-switch check: waits up to [timeout] for a fresh flag, then reads it. Fails closed —
 * the flag exists to stop CelesTrak traffic, and a skipped run just retries on the next tick.
 */
internal suspend fun satellitesAllowed(
    config: ExperimentConfig,
    timeout: Duration = SATELLITE_FLAG_TIMEOUT,
): Boolean =
    withTimeoutOrNull(timeout) { config.awaitFresh() } == true &&
        config.isEnabled(Experiment.SATELLITES)

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
            // The gates are re-checked at run time, not just at schedule time: a Remote Config
            // flip, a withdrawn consent or the layer being switched off must stop traffic even if
            // a job is already enqueued.
            if (!entryPoint.settings().satelliteDataEnabled.first()) {
                return@withContext Result.success()
            }
            val layerOn =
                LayerRegistry.layerEnabled(entryPoint.settings(), SatelliteLayer.LAYER_ID).first()
            if (!layerOn) return@withContext Result.success()
            // Last, so only installs about to download pay for the fetch.
            if (!satellitesAllowed(entryPoint.experimentConfig())) {
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
        logRefusal(result)
        trackSatelliteFetchResult(result, analytics, Clock.System.now())

        return when (satelliteWorkAction(result.outcome)) {
            SatelliteWorkAction.SUCCEED -> Result.success()
            SatelliteWorkAction.RETRY -> Result.retry()
            // Unreachable by construction, and asserted so; mapped rather than thrown because
            // a worker that throws is a worker that stops running.
            SatelliteWorkAction.FAIL -> Result.success()
        }
    }

    /** A server refusal, to the log for a developer, as CelesTrak's policy requires. */
    private fun logRefusal(result: RefreshResult.Completed) {
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
    }

    private companion object {
        const val TAG = "SatelliteRefresh"
    }
}
