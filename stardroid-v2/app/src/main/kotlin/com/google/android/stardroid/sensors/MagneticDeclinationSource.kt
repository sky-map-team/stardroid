/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import android.hardware.GeomagneticField
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Instant

/**
 * The angle between magnetic north and true north at a place and time, fed into
 * `SkyModel.localFrame` — v1's `MagneticDeclinationCalculator` pair, as stateless lookups.
 */
interface MagneticDeclinationSource {
    fun declinationDeg(
        location: LatLong,
        time: Instant,
    ): Double
}

/** Real declination from Android's geomagnetic model (v1 used altitude 0 too). */
class GeomagneticDeclinationSource : MagneticDeclinationSource {
    override fun declinationDeg(
        location: LatLong,
        time: Instant,
    ): Double =
        GeomagneticField(
            location.latitudeDeg.toFloat(),
            location.longitudeDeg.toFloat(),
            0f,
            time.toEpochMilliseconds(),
        ).declination.toDouble()
}

/** For the user preference that disables magnetic correction. */
object ZeroMagneticDeclinationSource : MagneticDeclinationSource {
    override fun declinationDeg(
        location: LatLong,
        time: Instant,
    ): Double = 0.0
}
