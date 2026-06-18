package com.google.android.stardroid.control

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.stardroid.math.LatLong
import javax.inject.Inject

class FusedLocationProvider @Inject constructor(
    private val client: FusedLocationProviderClient
) : LocationProvider {

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startUpdates(minDistanceMetres: Float, onUpdate: (LatLong, Float?) -> Unit) {
        stopUpdates()
        // The second argument "0" means send updates "as fast as possible" but it is tempered
        // by the minimum updat requirement.
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 0L)
            .setMinUpdateDistanceMeters(minDistanceMetres)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onUpdate(
                    LatLong(loc.latitude, loc.longitude),
                    if (loc.hasAccuracy()) loc.accuracy else null
                )
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    override fun stopUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    override fun isAvailable(): Boolean = true
}
