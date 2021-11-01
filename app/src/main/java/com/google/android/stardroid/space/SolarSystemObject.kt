package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.Planet
import java.util.*

/**
 * A celestial object that lives in our solar system.
 */
abstract class SolarSystemObject(private val planet : Planet) : MovingObject() {
    fun getUpdateFrequencyMs(): Long {
        return planet.updateFrequencyMs
    }

    /**
     * Returns the resource id for the string corresponding to the name of this
     * planet.
     */
    fun getNameResourceId(): Int {
        return planet.nameResourceId
    }

    /** Returns the resource id for the planet's image.  */
    abstract fun getImageResourceId(time: Date): Int
}