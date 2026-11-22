/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorPath
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.FlagSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSProcessInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Set at launch (simctl's SIMCTL_CHILD_ prefix, a UI test's launchEnvironment) to the keys of the
// flags to turn on, comma-separated. Compiled for the simulator only.
private const val EXPERIMENTS_VARIABLE = "SKYMAP_EXPERIMENTS"

private val forcedOn: Set<String> by lazy {
    val value = NSProcessInfo.processInfo.environment[EXPERIMENTS_VARIABLE] as? String
    value?.split(',')?.toSet().orEmpty()
}

internal actual fun createArCamera(): IosArCamera = FakeArCamera()

internal actual fun flagSourceForTarget(source: FlagSource): FlagSource =
    if (forcedOn.isEmpty()) source else ForcedFlags(source)

// AR needs sensors, so forcing it on also holds an imaginary phone up at the sky.
internal actual fun orientationSourceForTarget(source: OrientationSource): OrientationSource =
    if (Experiment.CAMERA_AR.key in forcedOn && !source.available) HeldStill else source

private class ForcedFlags(
    private val real: FlagSource,
) : FlagSource by real {
    override fun serverValue(key: String): Boolean? =
        if (key in forcedOn) true else real.serverValue(key)
}

/** An upright phone with its back to the north, tipped 30° up. */
private object HeldStill : OrientationSource {
    override val available: Boolean = true

    override fun sensorPath(disableGyro: Boolean): SensorPath = SensorPath.FUSED

    override fun orientations(): Flow<Matrix3> =
        flow {
            val up = sin(30.0 * PI / 180.0)
            val level = cos(30.0 * PI / 180.0)
            emit(Matrix3(1.0, 0.0, 0.0, 0.0, -up, -level, 0.0, level, -up))
            awaitCancellation()
        }
}
