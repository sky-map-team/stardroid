package com.google.android.stardroid.control

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.google.android.stardroid.ApplicationConstants
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.base.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.google.android.stardroid.R
import com.google.android.stardroid.util.MiscUtil
import java.util.concurrent.ScheduledExecutorService

@Singleton
class LocationController @Inject constructor(
    private val locationProvider: LocationProvider,
    private val astronomerModel: AstronomerModel,
    private val preferences: SharedPreferences,
    private val backgroundExecutor: ScheduledExecutorService,
    @ApplicationContext private val context: Context
) : AbstractController() {

    private val handler = Handler(Looper.getMainLooper())
    private var state: LocationState = LocationState.Unset
    private val stateListeners = mutableListOf<LocationStateCallback>()

    fun currentState(): LocationState = state

    fun interface LocationStateCallback {
        fun onStateChanged(state: LocationState)
    }

    fun addStateListener(callback: LocationStateCallback) {
        stateListeners.add(callback)
        callback.onStateChanged(state)
    }

    fun removeStateListener(callback: LocationStateCallback) {
        stateListeners.remove(callback)
    }

    private fun transitionTo(newState: LocationState) {
        state = newState
        stateListeners.toList().forEach { it.onStateChanged(newState) }
    }

    override fun start() {
        if (!enabled) return
        val noAutoLocate = preferences.getBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, false)
        when {
            noAutoLocate -> loadManualLocation()
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PermissionChecker.PERMISSION_GRANTED -> startAuto()
            else -> transitionTo(LocationState.Unset)
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
        // Don't reset to Acquiring if already tracking — just restart updates
        val alreadyAuto = state is LocationState.Confirmed &&
            (state as LocationState.Confirmed).source == LocationSource.AUTO
        if (!alreadyAuto) {
            transitionTo(LocationState.Acquiring)
            scheduleAcquiringTimeout()
        }
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
        preferences.edit {
            putString(ApplicationConstants.LATITUDE_PREF_KEY, location.latitude.toString())
            putString(ApplicationConstants.LONGITUDE_PREF_KEY, location.longitude.toString())
            putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true)
        }
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
        preferences.edit { 
            putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, false) 
        }
        startAuto()
    }

    fun switchToManual() {
        locationProvider.stopUpdates()
        cancelAcquiringTimeout()
        preferences.edit { 
            putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true)
        }
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
        // The LocationProvider already filters by minDistanceMetres at the OS level,
        // so any callback here is a meaningful position change.
        cancelAcquiringTimeout()
        astronomerModel.setLocation(location)
        preferences.edit {
            putString(ApplicationConstants.LATITUDE_PREF_KEY, location.latitude.toString())
            putString(ApplicationConstants.LONGITUDE_PREF_KEY, location.longitude.toString())
        }
        transitionTo(LocationState.Confirmed(
            location = location,
            source = LocationSource.AUTO,
            accuracy = accuracy,
            timestamp = System.currentTimeMillis()
        ))
        showLocationToast(location)
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
        if (preferences.contains(ApplicationConstants.LATITUDE_PREF_KEY) &&
            preferences.contains(ApplicationConstants.LONGITUDE_PREF_KEY)
        ) {
            val latStr = preferences.getString(ApplicationConstants.LATITUDE_PREF_KEY, "")
            val lonStr = preferences.getString(ApplicationConstants.LONGITUDE_PREF_KEY, "")
            if (!latStr.isNullOrEmpty() && !lonStr.isNullOrEmpty()) {
                try {
                    val lat = latStr.toFloat()
                    val lon = lonStr.toFloat()
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
                } catch (_: NumberFormatException) {
                    Log.w(TAG, "Loaded invalid location from preferences $latStr, $lonStr")
                }
            }
        }
        transitionTo(LocationState.Unset)
    }

    private fun showLocationToast(location: LatLong) {
        backgroundExecutor.execute {
            val name = tryReverseGeocode(location)
            val locName = name ?: context.getString(
                R.string.location_long_lat,
                location.longitude, location.latitude)
            val msg = context.getString(R.string.location_set_auto).format(locName)
            handler.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    @Suppress("DEPRECATION")
    private fun tryReverseGeocode(location: LatLong): String? = try {
        Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude.toDouble(), location.longitude.toDouble(), 1)
            ?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
    } catch (_: Exception) {
        null
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    companion object {
        private val TAG = MiscUtil.getTag(LocationController::class.java)
    }
}
