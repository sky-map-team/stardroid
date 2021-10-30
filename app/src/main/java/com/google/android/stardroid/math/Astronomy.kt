package com.google.android.stardroid.math

import java.util.*

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