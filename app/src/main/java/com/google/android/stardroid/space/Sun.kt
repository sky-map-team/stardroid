package com.google.android.stardroid.space

import com.google.android.stardroid.provider.ephemeris.Planet
import com.google.android.stardroid.units.HeliocentricCoordinates
import com.google.android.stardroid.units.RaDec
import java.util.*

/**
 * The Sun is special as it's at the center of the solar system.
 *
 * It's a sort of trivial sun-orbiting object.
 */
class Sun : SunOrbitingObject(Planet.Sun) {
    protected override fun getMyHeliocentricCoordinates(date: Date) =
        HeliocentricCoordinates(0.0f, 0.0f, 0.0f, 0.0f)
}