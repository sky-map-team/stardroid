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
        var coords: HeliocentricCoordinates? = null
        if (planet == Planet.Sun) {
            // Invert the view, since we want the Sun in earth coordinates, not the Earth in sun
            // coordinates.
            coords = HeliocentricCoordinates(
                sunCoords.radius, sunCoords.x * -1.0f,
                sunCoords.y * -1.0f, sunCoords.z * -1.0f
            )
        } else {
            coords = HeliocentricCoordinates.getInstance(planet, date)
            coords.Subtract(sunCoords)
        }
        val equ = coords!!.CalculateEquatorialCoordinates()
        return RaDec.calculateRaDecDist(equ)
    }
}