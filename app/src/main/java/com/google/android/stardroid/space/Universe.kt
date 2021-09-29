package com.google.android.stardroid.space

import com.google.android.stardroid.space.SolarSystemObject
import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.RaDec
import com.google.android.stardroid.units.HeliocentricCoordinates
import java.util.*

/**
 * Represents the celestial objects and physics of the universe.
 *
 * Initially this is going to be a facade to calculating positions etc of objects - akin to
 * the functions that are in the RaDec class at the moment. Might be a temporary shim.
 */
class Universe {
    /**
     * Returns all the solar system objects in the universe.
     */
    val solarSystemObjects: List<SolarSystemObject> = ArrayList()

    /**
     * Gets the location of a planet at a particular date.
     * Possibly a temporary swap for RaDec.getInstance.
     */
    fun getRaDec(planet: Planet, datetime: Date): RaDec {
        val sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, datetime)
        return RaDec.getInstanceDontUse(planet, datetime, sunCoords)
    }

    /**
     * Gets the RaDec of the Moon at a particular date.
     * TODO Factor this away
     */
    fun getMoonRaDec(datetime: Date): RaDec {
        val sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, datetime)
        return Planet.calculateLunarGeocentricLocation(datetime)
    }

    /**
     * Gets the RaDec of the sun at a particular date.
     * TODO Factor this away
     */
    fun getSunRaDec(datetime: Date): RaDec {
        val sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, datetime)
        return RaDec.getInstanceDontUse(Planet.Sun, datetime, sunCoords)
    }
}