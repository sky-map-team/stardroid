/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.activities.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import com.google.android.stardroid.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AcquiringLocationTimeoutDialogFragment : DialogFragment() {

    var onKeepWaiting: (() -> Unit)? = null
        private set
    var onEnterManually: (() -> Unit)? = null
        private set

    fun setOnKeepWaiting(callback: Runnable?) { onKeepWaiting = callback?.let { { it.run() } } }
    fun setOnEnterManually(callback: Runnable?) { onEnterManually = callback?.let { { it.run() } } }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_acquiring_timeout, null)
        view.findViewById<Button>(R.id.acquiring_keep_waiting_button)
            .setOnClickListener {
                dismiss()
                onKeepWaiting?.invoke()
            }
        view.findViewById<Button>(R.id.acquiring_enter_manually_button)
            .setOnClickListener {
                dismiss()
                onEnterManually?.invoke()
            }
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_acquiring_title)
            .setView(view)
            .create()
    }

    companion object {
        @JvmStatic fun newInstance() = AcquiringLocationTimeoutDialogFragment()
    }
}
