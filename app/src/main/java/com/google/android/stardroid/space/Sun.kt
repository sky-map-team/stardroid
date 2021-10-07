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
        val sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, date)
        var coords: HeliocentricCoordinates? = null
        if (Planet.Sun == Planet.Sun) {
            // Invert the view, since we want the Sun in earth coordinates, not the Earth in sun
            // coordinates.
            coords = HeliocentricCoordinates(
                sunCoords.radius, sunCoords.x * -1.0f,
                sunCoords.y * -1.0f, sunCoords.z * -1.0f
            )
        } else {
            coords = HeliocentricCoordinates.getInstance(Planet.Sun, date)
            coords.Subtract(sunCoords)
        }
        val equ = coords!!.CalculateEquatorialCoordinates()
        return RaDec.calculateRaDecDist(equ)
    }
}