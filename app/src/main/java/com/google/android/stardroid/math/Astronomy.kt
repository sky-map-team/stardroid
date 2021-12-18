package com.google.android.stardroid.math

import java.util.*
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.ephemeris.SolarSystemBody

import com.google.android.stardroid.space.Universe


/**
  Functions related to Astronomy that don't belong elsewhere.
 */

/**
 * Computes celestial coordinates of zenith from utc, lat long.
 */
fun calculateRADecOfZenith(utc: Date, location: LatLong): RaDec {
    // compute overhead RA in degrees
    val myRa = meanSiderealTime(utc, location.longitude)
    val myDec = location.latitude
    return RaDec(myRa, myDec)
}


/**
 * Return the date of the next full moon after today.
 */
// TODO(serafini): This could also be error prone right around the time
// of the full and new moons...
fun getNextFullMoon(now: Date): Date {
    val universe = Universe()
    val moon = universe.solarSystemObjectFor(SolarSystemBody.Moon)
    // First, get the moon's current phase.
    val phase: Float = moon.calculatePhaseAngle(now)

    // Next, figure out if the moon is waxing or waning.
    val later = Date(now.time + 1 * 3600 * 1000)
    val phase2: Float = moon.calculatePhaseAngle(later)
    val isWaxing = phase2 > phase

    // If moon is waxing, next full moon is (180.0 - phase)/360.0 * 29.53.
    // If moon is waning, next full moon is (360.0 - phase)/360.0 * 29.53.
    val LUNAR_CYCLE = 29.53f // In days.
    val baseAngle = if (isWaxing) 180.0f else 360.0f
    val numDays = (baseAngle - phase) / 360.0f * LUNAR_CYCLE
    return Date(now.time + (numDays * 24.0 * 3600.0 * 1000.0).toLong())
}

/**
 * Return the date of the next full moon after today.
 * Slow incremental version, only correct to within an hour.
 */
fun getNextFullMoonSlow(now: Date): Date {
    val universe = Universe()
    val moon = universe.solarSystemObjectFor(SolarSystemBody.Moon)
    val fullMoon = Date(now.time)
    var phase: Float = moon.calculatePhaseAngle(now)
    var waxing = false
    while (true) {
        fullMoon.time = fullMoon.time + TimeConstants.MILLISECONDS_PER_HOUR
        val nextPhase: Float = moon.calculatePhaseAngle(fullMoon)
        if (waxing && nextPhase < phase) {
            fullMoon.time = fullMoon.time - TimeConstants.MILLISECONDS_PER_HOUR
            return fullMoon
        }
        waxing = nextPhase > phase
        phase = nextPhase
        // Log.d(TAG, "Phase: $phase\tDate:$fullMoon")
    }
}