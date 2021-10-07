package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.units.RaDec
import java.util.*

/**
 * A likely temporary class to represent the Moon.
 */
class Moon : EarthOrbitingObject() {
    override fun getPosition(date: Date): RaDec {
        return Planet.calculateLunarGeocentricLocation(date)
    }
}