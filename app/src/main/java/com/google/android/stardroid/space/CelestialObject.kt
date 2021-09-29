package com.google.android.stardroid.space

import com.google.android.stardroid.units.RaDec

/**
 * Base class for any celestial objects.
 */
open class CelestialObject {
    lateinit var position : RaDec
}