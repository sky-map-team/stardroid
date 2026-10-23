/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.math

/**
 * A geographic location. Latitude is clamped to `[-90, 90]` and longitude wrapped to
 * `[-180, 180)` on construction, so a `LatLong` is always normalized.
 *
 * Construct with `LatLong(lat, lon)`; the primary constructor is private so the values are always
 * normalized first. [ConsistentCopyVisibility] keeps the generated `copy()` private too, so it
 * can't bypass normalization.
 */
@ConsistentCopyVisibility
data class LatLong private constructor(val latitudeDeg: Double, val longitudeDeg: Double) {
    /** Angular distance to [other] along a great circle, in degrees. */
    fun distanceTo(other: LatLong): Double = angularSeparationDeg(asRaDec(), other.asRaDec())

    // Reuses the celestial-sphere math: longitude maps to ra, latitude to dec.
    private fun asRaDec() = RaDec(longitudeDeg, latitudeDeg)

    companion object {
        operator fun invoke(
            latitudeDeg: Double,
            longitudeDeg: Double,
        ) = LatLong(
            latitudeDeg.coerceIn(-90.0, 90.0),
            floorMod(longitudeDeg + 180.0, 360.0) - 180.0,
        )
    }
}
