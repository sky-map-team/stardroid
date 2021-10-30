package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.convertToEquatorialCoordinates
import com.google.android.stardroid.math.heliocentricCoordinatesFromOrbitalElements
import java.util.*

/**
 * An object that orbits the sun.
 */
open class SunOrbitingObject(private val planet : Planet) : SolarSystemObject() {
    override fun getRaDec(date: Date): RaDec {
        val earthCoords = getEarthHeliocentricCoordinates(date)
        var myCoords = getMyHeliocentricCoordinates(date)
        myCoords.minusAssign(earthCoords)
        val equ = convertToEquatorialCoordinates(myCoords)
        return RaDec.calculateRaDecDist(equ)
    }

    protected open fun getMyHeliocentricCoordinates(date: Date) =
        heliocentricCoordinatesFromOrbitalElements(planet.getOrbitalElements(date))

    protected fun getEarthHeliocentricCoordinates(date: Date) =
        heliocentricCoordinatesFromOrbitalElements(Planet.Sun.getOrbitalElements(date))
}