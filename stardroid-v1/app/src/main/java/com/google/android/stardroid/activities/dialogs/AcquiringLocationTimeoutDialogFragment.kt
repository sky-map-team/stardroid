package com.google.android.stardroid.activities.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
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
        view.findViewById<android.widget.Button>(R.id.acquiring_keep_waiting_button)
            .setOnClickListener {
                dismiss()
                onKeepWaiting?.invoke()
            }
        view.findViewById<android.widget.Button>(R.id.acquiring_enter_manually_button)
            .setOnClickListener {
                dismiss()
                onEnterManually?.invoke()
            }
        return android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_acquiring_title)
            .setView(view)
            .create()
    }

    companion object {
        @JvmStatic fun newInstance() = AcquiringLocationTimeoutDialogFragment()
    }
}
