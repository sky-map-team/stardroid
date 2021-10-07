package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.units.RaDec
import com.google.android.stardroid.units.RaDec2
import java.util.*

/**
 * The Sun is special as it's at the center of the solar system.
 */
class Sun : SolarSystemObject() {
    override fun getPosition(date: Date): RaDec {
        val sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, date)
        return RaDec2.getInstanceDontUse(Planet.Sun, date, sunCoords)
    }
}