package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates.getInstance
import com.google.android.stardroid.units.RaDec
import java.util.*

/**
 * An object that orbits the sun.
 */
open class SunOrbitingObject(private val planet : Planet) : SolarSystemObject() {
    override fun getRaDec(date: Date): RaDec {
        val earthCoords = getEarthHeliocentricCoordinates(date)
        var myCoords = getMyHeliocentricCoordinates(date)
        myCoords.Subtract(earthCoords)
        val equ = myCoords.calculateEquatorialCoordinates()
        return RaDec.calculateRaDecDist(equ)
    }

    protected open fun getMyHeliocentricCoordinates(date: Date) =
        getInstance(planet.getOrbitalElements(date))

    protected fun getEarthHeliocentricCoordinates(date: Date) =
        getInstance(Planet.Sun.getOrbitalElements(date))
}