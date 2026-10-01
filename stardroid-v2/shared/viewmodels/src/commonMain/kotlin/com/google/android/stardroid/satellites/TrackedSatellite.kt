/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.math.RaDec

/** A satellite currently drawn: its synthesized card and where it is right now. */
data class TrackedSatellite(
    val tle: Tle,
    val info: ObjectInfo,
    val position: RaDec,
)
