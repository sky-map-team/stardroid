package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.convertToEquatorialCoordinates
import com.google.android.stardroid.math.heliocentricCoordinatesFromOrbitalElements
import java.util.*

/**
 * An object that orbits the sun.
 */
open class SunOrbitingObject(planet : Planet) : SolarSystemObject(planet) {
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

    /////////////////////

    // Methods copied from Planet.java.
    // TODO(jontayler): move the right places in the stack.
    // TODO(jontayler): consider separating out appearance-related stuff

    /////////////////////


    /** Returns the resource id for the planet's image.  */
    override fun getImageResourceId(time: Date): Int {
        return planet.getImageResourceId()
    }
}