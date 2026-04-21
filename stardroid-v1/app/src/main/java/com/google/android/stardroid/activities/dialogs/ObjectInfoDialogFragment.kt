// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.activities.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.ObjectInfoDialogFragment.Companion.newInstance
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.activities.util.NightModeHelper
import com.google.android.stardroid.education.ObjectInfo
import com.google.android.stardroid.util.AssetImageLoader
import com.google.android.stardroid.util.ImageLoadHandle
import com.google.android.stardroid.util.MiscUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dialog fragment that displays educational information about a celestial object.
 * Shows the object name, description, scientific data, and a fun fact.
 *
 * Use the [newInstance] factory method to create an instance with the required ObjectInfo.
 * This ensures the data survives configuration changes (e.g., screen rotation).
 */
@AndroidEntryPoint
class ObjectInfoDialogFragment : DialogFragment() {
    @Inject lateinit var preferences: SharedPreferences
    private var imageLoadHandle: ImageLoadHandle? = null

    /** Implemented by activities that host this dialog and want to handle the Find action. */
    interface OnFindClickedListener {
        fun onFindClicked(info: ObjectInfo)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parentActivity = requireActivity()
        val activity = parentActivity as? Activity

        // Retrieve ObjectInfo from arguments (survives configuration changes)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_OBJECT_INFO, ObjectInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_OBJECT_INFO)
        }
        val showFind = arguments?.getBoolean(ARG_SHOW_FIND, true) ?: true

        if (info == null) {
            Log.w(TAG, "ObjectInfo not found in arguments, dismissing dialog")
            return AlertDialog.Builder(parentActivity)
                .setMessage("No information available")
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .create()
        }

        val isNight = ActivityLightLevelManager.isNightMode(preferences)
        val nightTextColor = parentActivity.getColor(R.color.night_text_color)

        val inflater = parentActivity.layoutInflater
        val view = inflater.inflate(R.layout.object_info_card, null)

        // Apply night mode text tinting to all text in the card
        if (isNight) {
            (view as? ViewGroup)?.let { NightModeHelper.tintTextViews(it, nightTextColor) }
        }

        // Populate the view with object information
        view.findViewById<TextView>(R.id.object_info_name).text = info.name

        // Display celestial image if available
        val imageContainer = view.findViewById<View>(R.id.object_info_image_container)
        val imageView = view.findViewById<ImageView>(R.id.object_info_image)
        if (info.imagePath != null) {
            imageLoadHandle = AssetImageLoader.loadBitmapAsync(parentActivity.assets, info.imagePath) { bitmap ->
                if (bitmap != null && isAdded) {
                    imageView.setImageBitmap(bitmap)
                    if (isNight) {
                        imageView.setColorFilter(nightTextColor, PorterDuff.Mode.MULTIPLY)
                    }
                    imageContainer.visibility = View.VISIBLE
                    imageContainer.setOnClickListener {
                        ImageExpandDialogFragment.newInstance(info.imagePath, info.imageCredit)
                            .show(parentFragmentManager, "ExpandedImage")
                    }
                }
            }
        }

        // Display image credit if available
        val imageCreditView = view.findViewById<TextView>(R.id.object_info_image_credit)
        if (info.imageCredit != null && info.imagePath != null) {
            imageCreditView.text = info.imageCredit
            imageCreditView.visibility = View.VISIBLE
        }

        view.findViewById<TextView>(R.id.object_info_description).text = info.description
        view.findViewById<TextView>(R.id.object_info_funfact).text = info.funFact

        // Populate scientific data (show only fields that have data)
        var hasAnyScientificData = false

        hasAnyScientificData = setFieldIfPresent(
            view, R.id.object_info_distance_row, R.id.object_info_distance, info.distance
        ) || hasAnyScientificData

        hasAnyScientificData = setFieldIfPresent(
            view, R.id.object_info_size_row, R.id.object_info_size, info.size
        ) || hasAnyScientificData

        hasAnyScientificData = setFieldIfPresent(
            view, R.id.object_info_mass_row, R.id.object_info_mass, info.mass
        ) || hasAnyScientificData

        hasAnyScientificData = setFieldIfPresent(
            view, R.id.object_info_spectral_row, R.id.object_info_spectral, info.spectralClass
        ) || hasAnyScientificData

        hasAnyScientificData = setFieldIfPresent(
            view, R.id.object_info_magnitude_row, R.id.object_info_magnitude, info.magnitude
        ) || hasAnyScientificData

        // Hide the entire data section if no scientific data is available
        val dataSection = view.findViewById<View>(R.id.object_info_data_section)
        dataSection.visibility = if (hasAnyScientificData) View.VISIBLE else View.GONE

        val builder = AlertDialog.Builder(parentActivity).setView(view)
        if (showFind) {
            builder.setPositiveButton(R.string.action_find_in_sky_map) { dialog, _ ->
                (activity as? OnFindClickedListener)?.onFindClicked(info)
                dialog.dismiss()
            }
        } else {
            builder.setPositiveButton(R.string.dialog_ok_button) { dialog, _ -> dialog.dismiss() }
        }
        val alertDialog = builder.create()
        alertDialog.setOnShowListener { NightModeHelper.applyAlertDialogNightMode(alertDialog, isNight) }
        return alertDialog
    }

    /**
     * Sets a field value if it's not null, and makes the row visible.
     * Returns true if the field was set (value was not null).
     */
    private fun setFieldIfPresent(
        view: View,
        rowId: Int,
        valueId: Int,
        value: String?
    ): Boolean {
        if (value != null) {
            view.findViewById<View>(rowId).visibility = View.VISIBLE
            view.findViewById<TextView>(valueId).text = value
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageLoadHandle?.cancel()
        imageLoadHandle = null
    }

    companion object {
        private val TAG = MiscUtil.getTag(ObjectInfoDialogFragment::class.java)
        private const val ARG_OBJECT_INFO = "object_info"
        private const val ARG_SHOW_FIND = "show_find"

        /**
         * Creates a new instance of the dialog with the given object info.
         * Using this factory method ensures the data survives configuration changes.
         *
         * @param info The ObjectInfo to display in the dialog
         * @param showFind true to show a "Find" button (e.g. from gallery); false to show "OK"
         * @return A new ObjectInfoDialogFragment instance
         */
        @JvmStatic
        @JvmOverloads
        fun newInstance(info: ObjectInfo, showFind: Boolean = true): ObjectInfoDialogFragment {
            return ObjectInfoDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_OBJECT_INFO, info)
                    putBoolean(ARG_SHOW_FIND, showFind)
                }
            }
        }
    }
}
