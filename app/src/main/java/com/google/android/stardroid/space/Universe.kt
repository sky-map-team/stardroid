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
        solarSystemObjectMap.put(Planet.Moon, moon)
        solarSystemObjectMap.put(Planet.Sun, sun)
    }

    /**
     * Gets the |SolarSystemObject| corresponding to the given |Planet|.
     * TODO(johntaylor): probably a temporary shim.
     */
    fun solarSystemObjectFor(planet : Planet) : SolarSystemObject = solarSystemObjectMap[planet]!!

    /**
     * Gets the location of a planet at a particular date.
     * Possibly a temporary swap for RaDec.getInstance.
     */
    fun getRaDec(planet: Planet, datetime: Date): RaDec {
        return solarSystemObjectMap.get(planet)!!.getRaDec(datetime)
    }
}