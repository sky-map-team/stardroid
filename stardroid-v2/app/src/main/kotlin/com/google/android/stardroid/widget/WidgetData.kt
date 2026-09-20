/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import com.google.android.stardroid.astronomy.MoonWidgetModel
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.astronomy.moonWidgetModel
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

/*
 * What each widget's `provideGlance` computes before it draws, split out so the kill-switch
 * and data plumbing can be unit-tested without Glance, Hilt or a device. A null result is the
 * quiet disabled state: the widgets render an empty brand tile (D75).
 */

/**
 * The moon widget's model, or null when [Experiment.MOON_WIDGET] is off. The location is the
 * last confirmed one from settings — never a live provider request; null (app never ran)
 * degrades to the geometry-only model with no times row.
 */
internal suspend fun moonWidgetModelFor(
    experimentConfig: ExperimentConfig,
    now: Instant,
    savedLocation: Flow<LatLong?>,
): MoonWidgetModel? {
    if (!experimentConfig.isEnabled(Experiment.MOON_WIDGET)) return null
    return moonWidgetModel(now, savedLocation.first())
}

/**
 * The tonight widget's content, or null when [Experiment.TONIGHT_WIDGET] is off. [showers] and
 * [passes] are suspend lambdas, not values: the catalog lookup does real I/O, so it must not
 * run before the flag check.
 */
internal suspend fun tonightSkyFor(
    experimentConfig: ExperimentConfig,
    now: Instant,
    savedLocation: Flow<LatLong?>,
    showers: suspend () -> Flow<List<MeteorShower>>,
    passes: suspend (LatLong?) -> List<SatellitePass>,
): TonightSky? {
    if (!experimentConfig.isEnabled(Experiment.TONIGHT_WIDGET)) return null
    val location = savedLocation.first()
    return tonightSky(now, location, showers().first(), passes = passes(location))
}

/**
 * The countdown widget's target, or null when it is off. It shares [Experiment.TONIGHT_WIDGET]
 * with the tonight widget — the two ship together (D75 phase 2).
 */
internal suspend fun countdownFor(
    experimentConfig: ExperimentConfig,
    now: Instant,
    showers: suspend () -> Flow<List<MeteorShower>>,
): CountdownTarget? {
    if (!experimentConfig.isEnabled(Experiment.TONIGHT_WIDGET)) return null
    // Location-free: the countdown is about dates, not local geometry.
    return tonightSky(now, location = null, showers = showers().first()).countdown
}
