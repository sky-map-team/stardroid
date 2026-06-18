package com.google.android.stardroid.control

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.util.MiscUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PlatformLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationProvider {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var activeListeners = mutableListOf<LocationListener>()
    private var updateCallback: ((LatLong, Float?) -> Unit)? = null

    @SuppressLint("MissingPermission")
    override fun startUpdates(minDistanceMetres: Float, onUpdate: (LatLong, Float?) -> Unit) {
        stopUpdates()
        updateCallback = onUpdate

        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!locationManager.isProviderEnabled(provider)) continue
            val listener = createListener(onUpdate)
            try {
                locationManager.requestLocationUpdates(provider, 0L, minDistanceMetres, listener)
                activeListeners.add(listener)
            } catch (_: IllegalArgumentException) {
                // Provider not supported on this device
                Log.w(TAG, "Provider $provider not supported on this device")
            }
        }
    }

    private fun createListener(onUpdate: (LatLong, Float?) -> Unit): LocationListener {
        return object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val accuracy = if (location.hasAccuracy()) location.accuracy else null
                onUpdate(LatLong(location.latitude, location.longitude), accuracy)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
    }

    override fun stopUpdates() {
        activeListeners.forEach { locationManager.removeUpdates(it) }
        activeListeners.clear()
        updateCallback = null
    }

    override fun isAvailable(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    companion object {
        private val TAG = MiscUtil.getTag(PlatformLocationProvider::class.java)
    }
}
