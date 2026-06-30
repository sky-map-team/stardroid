/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.activities.util

import android.os.Bundle
import android.view.View
import com.google.android.stardroid.math.LatLong

/**
 * Interface for map functionality, implemented differently for GMS and fdroid flavors.
 */
interface MapAdapter {
    fun initialize(mapView: View, savedInstanceState: Bundle?)
    fun onResume()
    fun onPause()
    fun onDestroy()
    fun onSaveInstanceState(outState: Bundle)
    fun updateLocation(location: LatLong)
}
