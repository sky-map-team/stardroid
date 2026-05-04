package com.google.android.stardroid.activities.dialogs

import android.app.Dialog
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.stardroid.R
import com.google.android.stardroid.control.LocationController
import com.google.android.stardroid.math.LatLong
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class ManualLocationEntryDialogFragment : DialogFragment() {

    @Inject lateinit var locationController: LocationController

    private lateinit var placeNameEdit: EditText
    private lateinit var latEdit: EditText
    private lateinit var lonEdit: EditText
    private lateinit var placeErrorText: TextView
    private lateinit var latErrorText: TextView
    private lateinit var lonErrorText: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_manual_location_entry, null)

        placeNameEdit = view.findViewById(R.id.manual_place_name_edit)
        latEdit = view.findViewById(R.id.manual_latitude_edit)
        lonEdit = view.findViewById(R.id.manual_longitude_edit)
        placeErrorText = view.findViewById(R.id.manual_place_error_text)
        latErrorText = view.findViewById(R.id.manual_latitude_error_text)
        lonErrorText = view.findViewById(R.id.manual_longitude_error_text)

        val prefillLat = arguments?.getFloat(ARG_LAT, Float.NaN) ?: Float.NaN
        val prefillLon = arguments?.getFloat(ARG_LON, Float.NaN) ?: Float.NaN
        val prefillName = arguments?.getString(ARG_NAME, "") ?: ""

        if (!prefillLat.isNaN() && !prefillLon.isNaN() && (prefillLat != 0f || prefillLon != 0f)) {
            latEdit.setText(prefillLat.toString())
            lonEdit.setText(prefillLon.toString())
        }
        if (prefillName.isNotEmpty()) placeNameEdit.setText(prefillName)

        view.findViewById<Button>(R.id.manual_resolve_button).setOnClickListener {
            resolvePlace(view)
        }
        view.findViewById<Button>(R.id.manual_set_location_button).setOnClickListener {
            trySetLocation()
        }

        return android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_manual_entry_title)
            .setView(view)
            .create()
    }

    private fun resolvePlace(view: View) {
        val name = placeNameEdit.text.toString().trim()
        if (name.isEmpty()) return
        placeErrorText.visibility = View.GONE

        if (!Geocoder.isPresent()) {
            showPlaceError(getString(R.string.location_geocoder_offline))
            return
        }

        Thread {
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(requireContext()).getFromLocationName(name, 1)
                requireActivity().runOnUiThread {
                    if (results.isNullOrEmpty()) {
                        showPlaceError(getString(R.string.location_place_not_found))
                    } else {
                        val result = results[0]
                        latEdit.setText("%.6f".format(result.latitude))
                        lonEdit.setText("%.6f".format(result.longitude))
                        placeErrorText.visibility = View.GONE
                    }
                }
            } catch (e: IOException) {
                requireActivity().runOnUiThread {
                    showPlaceError(getString(R.string.location_geocoder_offline))
                }
            }
        }.start()
    }

    private fun showPlaceError(message: String) {
        placeErrorText.text = message
        placeErrorText.visibility = View.VISIBLE
    }

    private fun trySetLocation() {
        var valid = true

        val latStr = latEdit.text.toString().trim()
        val lonStr = lonEdit.text.toString().trim()

        val lat = latStr.toFloatOrNull()
        val lon = lonStr.toFloatOrNull()

        if (lat == null || lat < -90f || lat > 90f) {
            latErrorText.text = getString(R.string.location_invalid_latitude)
            latErrorText.visibility = View.VISIBLE
            valid = false
        } else {
            latErrorText.visibility = View.GONE
        }

        if (lon == null || lon < -180f || lon > 180f) {
            lonErrorText.text = getString(R.string.location_invalid_longitude)
            lonErrorText.visibility = View.VISIBLE
            valid = false
        } else {
            lonErrorText.visibility = View.GONE
        }

        if (valid && lat != null && lon != null) {
            locationController.setManualLocation(LatLong(lat, lon))
            dismiss()
        }
    }

    companion object {
        private const val ARG_LAT = "prefill_lat"
        private const val ARG_LON = "prefill_lon"
        private const val ARG_NAME = "prefill_name"

        @JvmStatic
        @JvmOverloads
        fun newInstance(
            prefillLat: Float = Float.NaN,
            prefillLon: Float = Float.NaN,
            prefillName: String = ""
        ) = ManualLocationEntryDialogFragment().apply {
            arguments = Bundle().apply {
                putFloat(ARG_LAT, prefillLat)
                putFloat(ARG_LON, prefillLon)
                putString(ARG_NAME, prefillName)
            }
        }
    }
}
