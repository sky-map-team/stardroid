package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.SolarSystemBody
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
    private val solarSystemObjectMap: MutableMap<SolarSystemBody, SolarSystemObject> = HashMap()
    private val sun = Sun()
    private val moon = Moon()

    init {
        for (planet in SolarSystemBody.values()) {
            if (planet != SolarSystemBody.Moon && planet != SolarSystemBody.Sun) {
                solarSystemObjectMap.put(planet, SunOrbitingObject(planet))
            }
        }
        solarSystemObjectMap.put(SolarSystemBody.Moon, moon)
        solarSystemObjectMap.put(SolarSystemBody.Sun, sun)
    }

    /**
     * Gets the |SolarSystemObject| corresponding to the given |SolarSystemBody|.
     * TODO(johntaylor): probably a temporary shim.
     */
    fun solarSystemObjectFor(solarSystemBody : SolarSystemBody) : SolarSystemObject = solarSystemObjectMap[solarSystemBody]!!

    /**
     * Gets the location of a planet at a particular date.
     * Possibly a temporary swap for RaDec.getInstance.
     */
    fun getRaDec(solarSystemBody: SolarSystemBody, datetime: Date): RaDec {
        return solarSystemObjectMap.get(solarSystemBody)!!.getRaDec(datetime)
    }
}