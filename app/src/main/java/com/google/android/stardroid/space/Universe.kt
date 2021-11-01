package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.RaDec
import java.util.*

/**
 * Represents the celestial objects and physics of the universe.
 *
 * Initially this is going to be a facade to calculating positions etc of objects - akin to
 * the functions that are in the RaDec class at the moment. Might be a temporary shim.
 */
class Universe {
    /**
     * A map from the planet enum to the corresponding CelestialObject. Possibly just
     * a temporary shim.
     */
    private val solarSystemObjectMap: MutableMap<Planet, SolarSystemObject> = HashMap()
    private val sun = Sun()
    private val moon = Moon()

    init {
        for (planet in Planet.values()) {
            if (planet != Planet.Moon && planet != Planet.Sun) {
                solarSystemObjectMap.put(planet, SunOrbitingObject(planet))
            }
        }
    }

    /**
     * Gets the |SolarSystemObject| corresponding to the given |Planet|.
     * TODO(johntaylor): probably a temporary shim.
     */
    fun solarSystemObjectFor(planet : Planet) = solarSystemObjectMap[planet]

    /**
     * Gets the location of a planet at a particular date.
     * Possibly a temporary swap for RaDec.getInstance.
     */
    fun getRaDec(planet: Planet, datetime: Date): RaDec {
        if (planet == Planet.Sun) {
            return getSunRaDec(datetime)
        }
        if (planet == Planet.Moon) {
            return getMoonRaDec(datetime)
        }
        // Not null, because all the enum values are in the map except for Sun and Moon.
        return solarSystemObjectMap.get(planet)!!.getRaDec(datetime)
    }

    /**
     * Gets the RaDec of the Moon at a particular date.
     * TODO(jontayler) Factor this away
     */
    fun getMoonRaDec(datetime: Date): RaDec {
        return moon.getRaDec(datetime)
    }

    /**
     * Gets the RaDec of the sun at a particular date.
     * TODO(jontayloer) Factor this away
     */
    fun getSunRaDec(datetime: Date): RaDec {
        return sun.getRaDec(datetime)
    }
}