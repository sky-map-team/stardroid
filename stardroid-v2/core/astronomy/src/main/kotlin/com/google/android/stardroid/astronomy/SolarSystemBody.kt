/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

/**
 * The solar-system bodies the ephemeris can compute.
 *
 * Unlike v1's enum, this carries no `R.drawable`/`R.string` resource IDs or update-frequency
 * hints — those couplings move app-side (imagery/name mapping). This is physics only.
 *
 * The order matches v1's reverse-distance-from-Earth draw order. [Earth] is included because the
 * Keplerian model needs its orbital elements to convert heliocentric positions to geocentric; it
 * is not itself a viewable target.
 */
enum class SolarSystemBody {
    PLUTO,
    NEPTUNE,
    URANUS,
    SATURN,
    JUPITER,
    MARS,
    SUN,
    MERCURY,
    VENUS,
    MOON,
    EARTH,
}
