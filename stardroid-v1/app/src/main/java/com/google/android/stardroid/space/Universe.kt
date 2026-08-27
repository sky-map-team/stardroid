/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.math.LatLong
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

    /**
     * Gets the location of a planet at a particular date, as seen by an observer at [location].
     * Only the Moon's position depends meaningfully on the observer's location on Earth (diurnal
     * parallax); every other body falls back to the geocentric [getRaDec], as their own parallax
     * is far below this model's accuracy (e.g. the Sun's is at most ~0.002 degrees).
     */
    fun getTopocentricRaDec(
        solarSystemBody: SolarSystemBody, datetime: Date, location: LatLong
    ): RaDec {
        return if (solarSystemBody == SolarSystemBody.Moon) {
            moon.getTopocentricRaDec(datetime, location)
        } else {
            getRaDec(solarSystemBody, datetime)
        }
    }
}