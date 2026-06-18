package com.google.android.stardroid.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.LocationPermissionPermanentlyDeniedDialogFragment
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleDialogFragment
import com.google.android.stardroid.activities.dialogs.ManualLocationEntryDialogFragment
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.activities.util.EdgeToEdgeFixer
import com.google.android.stardroid.activities.util.MapAdapter
import com.google.android.stardroid.activities.util.NightModeHelper
import com.google.android.stardroid.control.LocationController
import com.google.android.stardroid.control.LocationSource
import com.google.android.stardroid.control.LocationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.core.view.isVisible

@AndroidEntryPoint
class LocationManagementActivity : FragmentActivity(),
    ActivityLightLevelChanger.NightModeable {

    @Inject lateinit var locationController: LocationController
    @Inject lateinit var mapAdapter: MapAdapter
    @Inject lateinit var activityLightLevelManager: ActivityLightLevelManager

    private var nightMode = false

    private lateinit var sourceLabel: TextView
    private lateinit var coordinatesLabel: TextView
    private lateinit var fallbackLabel: TextView
    private lateinit var mapContainer: FrameLayout
    private lateinit var modeToggleButton: Button
    private lateinit var changeButton: Button
    private lateinit var progressBar: ProgressBar

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
        EdgeToEdgeFixer.applyEdgeToEdgeFixForActionBarActivity(this)
        setTitle(R.string.location_management_title)

        sourceLabel = findViewById<TextView>(R.id.location_source_label)
        coordinatesLabel = findViewById<TextView>(R.id.location_coordinates_label)
        fallbackLabel = findViewById<TextView>(R.id.map_unavailable_label)
        mapContainer = findViewById<FrameLayout>(R.id.map_container)
        modeToggleButton = findViewById<Button>(R.id.location_mode_toggle_button)
        changeButton = findViewById<Button>(R.id.location_change_button)
        progressBar = findViewById<ProgressBar>(R.id.location_acquiring_progress)

        changeButton.text = getString(R.string.location_change)

        modeToggleButton.setOnClickListener { onModeToggle() }
        changeButton.setOnClickListener { showManualEntryDialog() }

        initMap(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        EdgeToEdgeFixer.applyTopPaddingForActionBar(this, findViewById(android.R.id.content))
    }

    private val stateListener = LocationController.LocationStateCallback { state ->
        runOnUiThread { updateUi(state) }
    }

    override fun onResume() {
        super.onResume()
        activityLightLevelManager.onResume()
        locationController.addStateListener(stateListener)
        mapAdapter.onResume()
    }

    override fun onPause() {
        super.onPause()
        activityLightLevelManager.onPause()
        locationController.removeStateListener(stateListener)
        mapAdapter.onPause()
    }

    override fun setNightMode(nightMode: Boolean) {
        this.nightMode = nightMode
        NightModeHelper.applyActionBarNightMode(actionBar, this, nightMode)
        val textColor = if (nightMode) getColor(R.color.night_text_color) else getColor(android.R.color.white)
        val root = findViewById<View>(android.R.id.content)
        if (root is ViewGroup) {
            NightModeHelper.tintTextViews(root, textColor)
        }
        val mapView = findViewById<ImageView>(R.id.map_view)
        if (nightMode) {
            mapView?.setColorFilter(getColor(R.color.night_text_color), PorterDuff.Mode.MULTIPLY)
        } else {
            mapView?.clearColorFilter()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapAdapter.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapAdapter.onSaveInstanceState(outState)
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
        val tag = fragment::class.java.simpleName
        if (supportFragmentManager.findFragmentByTag(tag) == null) {
            fragment.show(supportFragmentManager, tag)
        }
    }

    private fun updateUi(state: LocationState) {
        progressBar.visibility = if (state is LocationState.Acquiring) View.VISIBLE else View.GONE
        when (state) {
            is LocationState.Confirmed -> {
                val sourceStr = if (state.source == LocationSource.AUTO)
                    getString(R.string.location_source_auto)
                else
                    getString(R.string.location_source_manual)
                sourceLabel.text = sourceStr
                coordinatesLabel.text = getString(
                    R.string.location_long_lat, state.location.longitude, state.location.latitude
                )
                coordinatesLabel.visibility = View.VISIBLE
                modeToggleButton.text = if (state.source == LocationSource.AUTO)
                    getString(R.string.location_switch_to_manual)
                else
                    getString(R.string.location_switch_to_auto)
                changeButton.visibility = if (
                    state.source == LocationSource.MANUAL) View.VISIBLE else View.GONE
                updateMapPin(state)
            }
            is LocationState.Acquiring -> {
                sourceLabel.text = getString(R.string.location_source_acquiring)
                coordinatesLabel.visibility = View.GONE
                modeToggleButton.text = getString(R.string.location_switch_to_manual)
                changeButton.visibility = View.GONE
                showFallbackLabel(R.string.location_map_acquiring)
            }
            is LocationState.HardwareUnavailable -> {
                sourceLabel.text = getString(R.string.location_source_hardware_unavailable)
                coordinatesLabel.visibility = View.GONE
                modeToggleButton.visibility = View.GONE
                changeButton.visibility = View.GONE
                showFallbackLabel(R.string.location_source_hardware_unavailable)
            }
            else -> {
                sourceLabel.text = getString(R.string.location_source_unset)
                coordinatesLabel.visibility = View.GONE
                modeToggleButton.text = getString(R.string.location_switch_to_auto)
                changeButton.visibility = View.GONE
                showFallbackLabel(R.string.location_map_no_location)
            }
        }
    }

    // Map lifecycle hooks — overridden in GMS instrumentation layer if needed, or handled via
    // runtime detection (see US6 tasks T040/T041)
    private fun initMap(savedInstanceState: Bundle?) {
        val mapView = findViewById<View>(R.id.map_view)
        if (mapView != null) {
            mapAdapter.initialize(mapView, savedInstanceState)
        }
    }
    private fun updateMapPin(state: LocationState.Confirmed) {
        mapAdapter.updateLocation(state.location)
    }
    private fun showFallbackLabel(messageResId: Int) {
        fallbackLabel.text = getString(messageResId)
        fallbackLabel.visibility = View.VISIBLE
        val mapView = findViewById<View>(R.id.map_view)
        mapView?.visibility = View.GONE
    }
}
