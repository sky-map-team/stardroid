package com.google.android.stardroid.activities.dialogs

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import com.google.android.stardroid.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocationPermissionPermanentlyDeniedDialogFragment : DialogFragment() {

    var onEnterManually: (() -> Unit)? = null
        private set

    fun setOnEnterManually(callback: Runnable?) { onEnterManually = callback?.let { { it.run() } } }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_location_permanently_denied, null)
        view.findViewById<android.widget.Button>(R.id.permanently_denied_settings_button)
            .setOnClickListener {
                dismiss()
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireActivity().packageName, null)
                    }
                )
            }
        view.findViewById<android.widget.Button>(R.id.permanently_denied_manually_button)
            .setOnClickListener {
                dismiss()
                onEnterManually?.invoke()
            }
        return android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_permanently_denied_title)
            .setView(view)
            .create()
    }

    companion object {
        @JvmStatic fun newInstance() = LocationPermissionPermanentlyDeniedDialogFragment()
    }
}
