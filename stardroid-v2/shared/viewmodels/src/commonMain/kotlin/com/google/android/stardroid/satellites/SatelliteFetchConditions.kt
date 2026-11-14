/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.settings.passAlertsEnabled
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.startup.StartupRouter
import com.google.android.stardroid.startup.StartupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Everything that must hold before Sky Map downloads satellite data, on either platform. See "Who
 * downloads" in satellite-tracking.md (#1272).
 */
data class SatelliteFetchConditions(
    val featureEnabled: Boolean,
    val dataConsented: Boolean,
    val layerEnabled: Boolean,
    val termsAccepted: Boolean,
    val lastForegroundAt: Instant?,
    /** Something shows satellite data while the app is closed: a widget, alert or digest. */
    val backgroundUse: Boolean,
) {
    /** The flag and the user's own choices: every gate except recent use. */
    val switchedOn: Boolean
        get() = featureEnabled && dataConsented && layerEnabled && termsAccepted

    fun allowFetch(now: Instant): Boolean = switchedOn && (backgroundUse || usedRecently(now))

    private fun usedRecently(now: Instant): Boolean {
        val last = lastForegroundAt ?: return false
        // A stamp far in the future is a clock that was wrong when it was written. Trusting it
        // would keep an unused install fetching until real time caught up.
        return now - last <= ACTIVE_USE_WINDOW && last - now <= CLOCK_SKEW_ALLOWANCE
    }

    companion object {
        /** How long after the user last opened the app downloads carry on. */
        val ACTIVE_USE_WINDOW: Duration = 14.days

        private val CLOCK_SKEW_ALLOWANCE: Duration = 1.days
    }
}

/**
 * The conditions as they stand. Re-emitted when a stored input changes; [backgroundUse] is only as
 * live as the flow the caller passes.
 */
fun satelliteFetchConditions(
    experiments: ExperimentConfig,
    settings: Settings,
    startupRouter: StartupRouter,
    startupState: StartupState,
    backgroundUse: Flow<Boolean>,
): Flow<SatelliteFetchConditions> =
    combine(
        settings.satelliteDataEnabled,
        LayerRegistry.layerEnabled(settings, SatelliteLayer.LAYER_ID),
        startupRouter.needsEula,
        startupState.lastForegroundAt,
        backgroundUse,
    ) { consented, layerOn, needsEula, lastForeground, background ->
        SatelliteFetchConditions(
            featureEnabled = experiments.isEnabled(Experiment.SATELLITES),
            dataConsented = consented,
            layerEnabled = layerOn,
            termsAccepted = !needsEula,
            lastForegroundAt = lastForeground,
            backgroundUse = background,
        )
    }

/**
 * Whether something shows satellite data while the app is closed: the Tonight widget (the only
 * widget with passes), or pass alerts or the tonight digest. A notification that can't be posted
 * needs no data. This only waives recent use: [SatelliteFetchConditions.switchedOn], the layer
 * included, still has to hold.
 */
fun satelliteBackgroundUse(
    passAlertsOn: Boolean,
    digestOn: Boolean,
    notificationsFlagOn: Boolean,
    notificationsAllowed: Boolean,
    tonightWidgetPlaced: Boolean,
): Boolean =
    tonightWidgetPlaced ||
        ((passAlertsOn || digestOn) && notificationsFlagOn && notificationsAllowed)

/**
 * [satelliteBackgroundUse] as the platform stands. The lambdas are read only when an alert or
 * digest setting changes, so a caller that needs current values collects afresh (`.first()`).
 */
fun satelliteBackgroundUseFlow(
    settings: Settings,
    experiments: ExperimentConfig,
    notificationsAllowed: suspend () -> Boolean,
    tonightWidgetPlaced: () -> Boolean,
): Flow<Boolean> =
    combine(settings.passAlertsEnabled, settings.tonightDigestEnabled) { alerts, digest ->
        satelliteBackgroundUse(
            passAlertsOn = alerts,
            digestOn = digest,
            notificationsFlagOn = experiments.isEnabled(Experiment.NOTIFICATIONS),
            notificationsAllowed = notificationsAllowed(),
            tonightWidgetPlaced = tonightWidgetPlaced(),
        )
    }
