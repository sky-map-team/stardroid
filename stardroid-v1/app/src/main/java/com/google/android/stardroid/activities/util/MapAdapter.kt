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
