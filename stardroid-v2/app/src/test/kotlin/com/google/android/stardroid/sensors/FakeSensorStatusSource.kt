/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** In-memory [SensorStatusSource] for JVM tests: hot per-sensor flows, settable presence. */
class FakeSensorStatusSource(
    private val present: Set<SensorKind> = SensorKind.entries.toSet(),
) : SensorStatusSource {
    private val flows = mutableMapOf<SensorKind, MutableSharedFlow<SensorReading>>()

    private fun flowFor(kind: SensorKind) = flows.getOrPut(kind) { MutableSharedFlow(replay = 1) }

    override fun hasSensor(kind: SensorKind): Boolean = kind in present

    override fun readings(kind: SensorKind): Flow<SensorReading> = flowFor(kind)

    suspend fun emit(
        kind: SensorKind,
        reading: SensorReading,
    ) {
        flowFor(kind).emit(reading)
    }
}
