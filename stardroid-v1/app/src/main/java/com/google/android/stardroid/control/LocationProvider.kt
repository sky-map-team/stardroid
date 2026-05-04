package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong

interface LocationProvider {
    fun startUpdates(minDistanceMetres: Float, onUpdate: (location: LatLong, accuracy: Float?) -> Unit)
    fun stopUpdates()
    fun isAvailable(): Boolean
}
