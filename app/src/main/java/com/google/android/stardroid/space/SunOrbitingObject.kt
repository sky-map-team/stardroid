package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.units.RaDec
import java.util.*

/**
 * An object that orbits the sun.
 */
open class SunOrbitingObject(private val planet : Planet) : SolarSystemObject() {
    override fun getPosition(date: Date): RaDec {
        val earthCoords = getEarthHeliocentricCoordinates(date)
        var myCoords = getMyHeliocentricCoordinates(date)
        myCoords.Subtract(earthCoords)
        val equ = myCoords.calculateEquatorialCoordinates()
        return RaDec.calculateRaDecDist(equ)
    }

    protected open fun getMyHeliocentricCoordinates(date: Date) =
        HeliocentricCoordinates.getInstance(planet, date)

    protected fun getEarthHeliocentricCoordinates(date: Date) =
        HeliocentricCoordinates.getInstance(Planet.Sun, date)
}