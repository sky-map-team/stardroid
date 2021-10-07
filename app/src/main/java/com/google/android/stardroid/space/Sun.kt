package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.units.RaDec
import java.util.*

/**
 * The Sun is special as it's at the center of the solar system.
 */
class Sun : SolarSystemObject() {
    override fun getPosition(date: Date): RaDec {
        val actuallyTheseAreEarthCoords = HeliocentricCoordinates.getInstance(Planet.Sun, date)
        var sunInEarthCoords = HeliocentricCoordinates(
                actuallyTheseAreEarthCoords.radius, actuallyTheseAreEarthCoords.x * -1.0f,
                actuallyTheseAreEarthCoords.y * -1.0f, actuallyTheseAreEarthCoords.z * -1.0f
            )
        val equ = sunInEarthCoords.calculateEquatorialCoordinates()
        return RaDec.calculateRaDecDist(equ)
    }
}