// Copyright 2024 Google Inc.
//
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
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.google.android.stardroid.R
import com.google.android.stardroid.education.ObjectInfo
import com.google.android.stardroid.inject.HasComponent
import com.google.android.stardroid.util.MiscUtil
import javax.inject.Inject

/**
 * Dialog fragment that displays educational information about a celestial object.
 * Shows the object name, description, and a fun fact.
 */
class ObjectInfoDialogFragment : DialogFragment() {

    @Inject
    lateinit var parentActivity: Activity

    private var objectInfo: ObjectInfo? = null

    /**
     * Interface that hosting activities must implement for dependency injection.
     */
    interface ActivityComponent {
        fun inject(fragment: ObjectInfoDialogFragment)
    }

    /**
     * Sets the object info to display. Must be called before showing the dialog.
     */
    fun setObjectInfo(info: ObjectInfo) {
        this.objectInfo = info
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Activities using this dialog MUST implement this interface
        @Suppress("UNCHECKED_CAST")
        (activity as HasComponent<ActivityComponent>).component.inject(this)

        val info = objectInfo
        if (info == null) {
            Log.w(TAG, "ObjectInfo not set, dismissing dialog")
            return AlertDialog.Builder(parentActivity)
                .setMessage("No information available")
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .create()
        }

        val inflater = parentActivity.layoutInflater
        val view = inflater.inflate(R.layout.object_info_card, null)

        // Populate the view with object information
        view.findViewById<TextView>(R.id.object_info_name).text = info.name
        view.findViewById<TextView>(R.id.object_info_description).text = info.description
        view.findViewById<TextView>(R.id.object_info_funfact).text = info.funFact

        return AlertDialog.Builder(parentActivity)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                Log.d(TAG, "Object info dialog closed for: ${info.id}")
                dialog.dismiss()
            }
            .create()
    }

    companion object {
        private val TAG = MiscUtil.getTag(ObjectInfoDialogFragment::class.java)
    }
}
