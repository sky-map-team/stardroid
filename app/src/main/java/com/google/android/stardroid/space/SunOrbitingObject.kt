package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.units.RaDec
import java.util.*

/**
 * An object that orbits the sun.
 */
class SunOrbitingObject(private val planet : Planet) : SolarSystemObject() {
    override fun getPosition(date: Date): RaDec {
        val sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, date)
        return RaDec.getInstanceDontUse(planet, date, sunCoords)
    }
}