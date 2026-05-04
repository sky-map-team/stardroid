package com.google.android.stardroid.activities.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.stardroid.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocationPermissionRationaleDialogFragment : DialogFragment() {

    var onGrant: (() -> Unit)? = null
        private set
    var onEnterManually: (() -> Unit)? = null
        private set
    var onLater: (() -> Unit)? = null
        private set

    fun setOnGrant(callback: Runnable?) { onGrant = callback?.let { { it.run() } } }
    fun setOnEnterManually(callback: Runnable?) { onEnterManually = callback?.let { { it.run() } } }
    fun setOnLater(callback: Runnable?) { onLater = callback?.let { { it.run() } } }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_location_rationale, null)
        view.findViewById<android.widget.Button>(R.id.rationale_grant_button).setOnClickListener {
            dismiss()
            onGrant?.invoke()
        }
        view.findViewById<android.widget.Button>(R.id.rationale_manually_button).setOnClickListener {
            dismiss()
            onEnterManually?.invoke()
        }
        view.findViewById<android.widget.Button>(R.id.rationale_later_button).setOnClickListener {
            dismiss()
            onLater?.invoke()
        }
        return android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_permission_dialog_title)
            .setView(view)
            .create()
    }

    companion object {
        @JvmStatic fun newInstance() = LocationPermissionRationaleDialogFragment()
    }
}
