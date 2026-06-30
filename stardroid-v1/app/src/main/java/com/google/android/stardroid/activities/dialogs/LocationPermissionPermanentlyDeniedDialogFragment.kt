/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.activities.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
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
        view.findViewById<Button>(R.id.permanently_denied_settings_button)
            .setOnClickListener {
                dismiss()
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireActivity().packageName, null)
                    }
                )
            }
        view.findViewById<Button>(R.id.permanently_denied_manually_button)
            .setOnClickListener {
                dismiss()
                onEnterManually?.invoke()
            }
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_permanently_denied_title)
            .setView(view)
            .create()
    }

    companion object {
        @JvmStatic fun newInstance() = LocationPermissionPermanentlyDeniedDialogFragment()
    }
}
