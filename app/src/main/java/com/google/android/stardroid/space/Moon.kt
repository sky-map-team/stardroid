package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.HeliocentricCoordinates
import com.google.android.stardroid.math.RaDec
import java.util.*

/**
 * A likely temporary class to represent the Moon.
 */
class Moon : EarthOrbitingObject() {
    override fun getRaDec(date: Date): RaDec {
        return Planet.calculateLunarGeocentricLocation(date)
    }
}