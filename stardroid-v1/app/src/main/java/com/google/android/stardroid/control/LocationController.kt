package com.google.android.stardroid.control

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.base.VisibleForTesting
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class LocationController @Inject constructor(
    private val locationProvider: LocationProvider,
    private val astronomerModel: AstronomerModel,
    private val preferences: SharedPreferences,
    private val activity: Activity
) : AbstractController() {

    private val handler = Handler(Looper.getMainLooper())
    private var state: LocationState = LocationState.Unset
    private var stateChangedCallback: LocationStateCallback? = null

    fun currentState(): LocationState = state

    fun interface LocationStateCallback {
        fun onStateChanged(state: LocationState)
    }

    fun setOnStateChanged(callback: LocationStateCallback?) {
        stateChangedCallback = callback
    }

    private fun transitionTo(newState: LocationState) {
        state = newState
        stateChangedCallback?.onStateChanged(newState)
    }

    override fun start() {
        if (!enabled) return
        val noAutoLocate = preferences.getBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, false)
        if (noAutoLocate) {
            loadManualLocation()
        } else {
            startAuto()
        }
    }

    override fun stop() {
        locationProvider.stopUpdates()
        cancelAcquiringTimeout()
    }

    fun startAuto() {
        if (!locationProvider.isAvailable()) {
            transitionTo(LocationState.HardwareUnavailable)
            return
        }
        transitionTo(LocationState.Acquiring)
        scheduleAcquiringTimeout()
        locationProvider.startUpdates(ApplicationConstants.LOCATION_UPDATE_MIN_DISTANCE_METRES) { location, accuracy ->
            onLocationUpdate(location, accuracy)
        }
    }

    fun onPermissionDenied(canAsk: Boolean) {
        locationProvider.stopUpdates()
        cancelAcquiringTimeout()
        transitionTo(
            if (canAsk) LocationState.PermissionDenied
            else LocationState.PermissionPermanentlyDenied
        )
    }

    fun onPermissionRevoked() {
        locationProvider.stopUpdates()
        cancelAcquiringTimeout()
        transitionTo(LocationState.PermissionDenied)
    }

    fun setManualLocation(location: LatLong) {
        cancelAcquiringTimeout()
        locationProvider.stopUpdates()
        preferences.edit()
            .putString("latitude", location.latitude.toString())
            .putString("longitude", location.longitude.toString())
            .putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true)
            .apply()
        astronomerModel.setLocation(location)
        transitionTo(
            LocationState.Confirmed(
                location = location,
                source = LocationSource.MANUAL,
                accuracy = null,
                timestamp = System.currentTimeMillis()
            )
        )
        showLocationToast(location)
    }

    fun switchToAuto() {
        preferences.edit().putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, false).apply()
        startAuto()
    }

    fun switchToManual() {
        locationProvider.stopUpdates()
        cancelAcquiringTimeout()
        preferences.edit().putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true).apply()
    }

    fun keepWaiting() {
        cancelAcquiringTimeout()
        transitionTo(LocationState.Acquiring)
        scheduleAcquiringTimeout()
    }

    @VisibleForTesting
    internal fun testOnlyInvokeLocationUpdate(location: LatLong, accuracy: Float?) =
        onLocationUpdate(location, accuracy)

    private fun onLocationUpdate(location: LatLong, accuracy: Float?) {
        val current = state
        val distanceDegrees = if (current is LocationState.Confirmed) {
            current.location.distanceFrom(location)
        } else {
            Float.MAX_VALUE
        }
        // 2000 m ≈ 0.018 degrees; use a small epsilon for "first fix" case (MAX_VALUE)
        val minDistanceDegrees = ApplicationConstants.LOCATION_UPDATE_MIN_DISTANCE_METRES / 111_320f
        if (distanceDegrees >= minDistanceDegrees) {
            cancelAcquiringTimeout()
            astronomerModel.setLocation(location)
            val confirmed = LocationState.Confirmed(
                location = location,
                source = LocationSource.AUTO,
                accuracy = accuracy,
                timestamp = System.currentTimeMillis()
            )
            transitionTo(confirmed)
            showLocationToast(location)
        }
    }

    private fun scheduleAcquiringTimeout() {
        cancelAcquiringTimeout()
        handler.postDelayed(timeoutRunnable, ApplicationConstants.LOCATION_ACQUIRING_TIMEOUT_MS)
    }

    private fun cancelAcquiringTimeout() {
        handler.removeCallbacks(timeoutRunnable)
    }

    private val timeoutRunnable = Runnable {
        if (state is LocationState.Acquiring) {
            transitionTo(LocationState.AcquiringTimeout)
        }
    }

    private fun loadManualLocation() {
        val latStr = preferences.getString("latitude", "")
        val lonStr = preferences.getString("longitude", "")
        if (!latStr.isNullOrEmpty() && !lonStr.isNullOrEmpty()) {
            try {
                val lat = latStr.toFloat()
                val lon = lonStr.toFloat()
                if (lat != 0f || lon != 0f) {
                    val location = LatLong(lat, lon)
                    astronomerModel.setLocation(location)
                    transitionTo(
                        LocationState.Confirmed(
                            location = location,
                            source = LocationSource.MANUAL,
                            accuracy = null,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    return
                }
            } catch (_: NumberFormatException) {
            }
        }
        transitionTo(LocationState.Unset)
    }

    private fun showLocationToast(location: LatLong) {
        val msg = "%.2f°, %.2f°".format(location.latitude, location.longitude)
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
