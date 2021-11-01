package com.google.android.stardroid.space

import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.math.RaDec
import java.util.*

/**
 * An object that orbits Earth.
 */
abstract class EarthOrbitingObject(planet : Planet) : SolarSystemObject(planet)