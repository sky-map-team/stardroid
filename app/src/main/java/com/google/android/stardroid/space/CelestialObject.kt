package com.google.android.stardroid.space

import com.google.android.stardroid.math.RaDec
import java.util.*

/**
 * Base class for any celestial objects.
 */
abstract class CelestialObject {
    abstract fun getRaDec(date : Date) : RaDec
}