package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.convertToEquatorialCoordinates
import com.google.android.stardroid.math.heliocentricCoordinatesFromOrbitalElements
import java.util.*

/**
 * An object that orbits the sun.
 */
open class SunOrbitingObject(solarSystemBody : SolarSystemBody) : SolarSystemObject(solarSystemBody) {
    override fun getRaDec(date: Date): RaDec {
        val earthCoords =
            heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(date))
        val myCoords = getMyHeliocentricCoordinates(date)
        myCoords -= earthCoords
        val equ = convertToEquatorialCoordinates(myCoords)
        return RaDec.fromGeocentricCoords(equ)
    }

    protected open fun getMyHeliocentricCoordinates(date: Date) =
        heliocentricCoordinatesFromOrbitalElements(solarSystemBody.getOrbitalElements(date))

    /////////////////////

    // Methods copied from Planet.java.
    // TODO(jontayler): move the right places in the stack.
    // TODO(jontayler): consider separating out appearance-related stuff

    /////////////////////


    /** Returns the resource id for the planet's image.  */
    override fun getImageResourceId(time: Date): Int {
        return solarSystemBody.imageResourceId
    }
}