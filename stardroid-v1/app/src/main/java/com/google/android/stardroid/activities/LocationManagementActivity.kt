package com.google.android.stardroid.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.LocationPermissionPermanentlyDeniedDialogFragment
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleDialogFragment
import com.google.android.stardroid.activities.dialogs.ManualLocationEntryDialogFragment
import com.google.android.stardroid.control.LocationController
import com.google.android.stardroid.control.LocationSource
import com.google.android.stardroid.control.LocationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocationManagementActivity : FragmentActivity() {

    @Inject lateinit var locationController: LocationController

    private lateinit var sourceLabel: TextView
    private lateinit var coordinatesLabel: TextView
    private lateinit var mapContainer: FrameLayout
    private lateinit var modeToggleButton: Button
    private lateinit var changeButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locationController.switchToAuto()
        } else {
            val canAsk = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            )
            locationController.onPermissionDenied(canAsk)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_management)
        setTitle(R.string.location_management_title)

        sourceLabel = findViewById(R.id.location_source_label)
        coordinatesLabel = findViewById(R.id.location_coordinates_label)
        mapContainer = findViewById(R.id.map_container)
        modeToggleButton = findViewById(R.id.location_mode_toggle_button)
        changeButton = findViewById(R.id.location_change_button)

        modeToggleButton.setOnClickListener { onModeToggle() }
        changeButton.setOnClickListener { showManualEntryDialog() }

        locationController.setOnStateChanged { state -> runOnUiThread { updateUi(state) } }
    }

    override fun onResume() {
        super.onResume()
        updateUi(locationController.currentState())
        initMap(savedInstanceState = null)
    }

    override fun onPause() {
        super.onPause()
        teardownMap()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyMap()
    }

    private fun onModeToggle() {
        val state = locationController.currentState()
        if (state is LocationState.Confirmed && state.source == LocationSource.MANUAL
            || state is LocationState.Unset
            || state is LocationState.PermissionDenied
            || state is LocationState.PermissionPermanentlyDenied
        ) {
            // Switch to auto
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                locationController.switchToAuto()
            } else if (state is LocationState.PermissionPermanentlyDenied) {
                showPermDeniedDialog()
            } else {
                showRationaleDialog()
            }
        } else {
            // Switch to manual
            locationController.switchToManual()
            showManualEntryDialog()
        }
    }

    private fun showManualEntryDialog() {
        val state = locationController.currentState()
        var lat = Float.NaN; var lon = Float.NaN
        if (state is LocationState.Confirmed) {
            lat = state.location.latitude; lon = state.location.longitude
        }
        showDialog(ManualLocationEntryDialogFragment.newInstance(lat, lon))
    }

    private fun showRationaleDialog() {
        val dlg = LocationPermissionRationaleDialogFragment.newInstance()
        dlg.setOnGrant(Runnable {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        })
        dlg.setOnEnterManually(Runnable { showManualEntryDialog() })
        showDialog(dlg)
    }

    private fun showPermDeniedDialog() {
        val dlg = LocationPermissionPermanentlyDeniedDialogFragment.newInstance()
        dlg.setOnEnterManually(Runnable { showManualEntryDialog() })
        showDialog(dlg)
    }

    private fun showDialog(fragment: androidx.fragment.app.DialogFragment) {
        fragment.show(supportFragmentManager, fragment::class.java.simpleName)
    }

    private fun updateUi(state: LocationState) {
        when (state) {
            is LocationState.Confirmed -> {
                val sourceStr = if (state.source == LocationSource.AUTO)
                    getString(R.string.location_source_auto)
                else
                    getString(R.string.location_source_manual)
                sourceLabel.text = sourceStr
                coordinatesLabel.text = "%.4f°, %.4f°".format(
                    state.location.latitude, state.location.longitude
                )
                coordinatesLabel.visibility = View.VISIBLE
                modeToggleButton.text = if (state.source == LocationSource.AUTO)
                    getString(R.string.location_switch_to_manual)
                else
                    getString(R.string.location_switch_to_auto)
                changeButton.visibility = if (state.source == LocationSource.MANUAL) View.VISIBLE else View.GONE
                updateMapPin(state)
            }
            is LocationState.Acquiring -> {
                sourceLabel.text = getString(R.string.location_source_acquiring)
                coordinatesLabel.visibility = View.GONE
                modeToggleButton.text = getString(R.string.location_switch_to_manual)
                changeButton.visibility = View.GONE
                showMapMessage(getString(R.string.location_map_acquiring))
            }
            is LocationState.HardwareUnavailable -> {
                sourceLabel.text = getString(R.string.location_source_hardware_unavailable)
                coordinatesLabel.visibility = View.GONE
                modeToggleButton.visibility = View.GONE
                changeButton.visibility = View.GONE
                showMapMessage(getString(R.string.location_source_hardware_unavailable))
            }
            else -> {
                sourceLabel.text = getString(R.string.location_source_unset)
                coordinatesLabel.visibility = View.GONE
                modeToggleButton.text = getString(R.string.location_switch_to_auto)
                changeButton.visibility = View.GONE
                showMapMessage(getString(R.string.location_map_no_location))
            }
        }
    }

    // Map lifecycle hooks — overridden in GMS instrumentation layer if needed, or handled via
    // runtime detection (see US6 tasks T040/T041)
    protected open fun initMap(savedInstanceState: Bundle?) {}
    protected open fun teardownMap() {}
    protected open fun destroyMap() {}
    protected open fun updateMapPin(state: LocationState.Confirmed) {
        showMapMessage("%.4f°, %.4f°".format(state.location.latitude, state.location.longitude))
    }
    protected open fun showMapMessage(message: String) {
        mapContainer.removeAllViews()
        val tv = TextView(this)
        tv.text = message
        tv.setPadding(32, 32, 32, 32)
        mapContainer.addView(tv)
    }
}
