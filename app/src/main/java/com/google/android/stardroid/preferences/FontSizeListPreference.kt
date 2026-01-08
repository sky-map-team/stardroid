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
package com.google.android.stardroid.preferences

import android.content.Context
import android.preference.ListPreference
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/**
 * Custom ListPreference that shows font size options with styled preview text.
 * Each option is displayed at its actual size to help users visualize the choice.
 *
 * @author John Taylor
 */
class FontSizeListPreference(context: Context, attrs: AttributeSet?) :
    ListPreference(context, attrs) {

    companion object {
        // Font scale factors matching AbstractLayer.kt
        private val FONT_SCALES = mapOf(
            "SMALL" to 0.75f,
            "MEDIUM" to 1.0f,
            "LARGE" to 1.5f,
            "EXTRALARGE" to 2.0f
        )

        private const val BASE_SIZE_SP = 16f  // Base font size in SP
    }

    override fun onPrepareDialogBuilder(builder: android.app.AlertDialog.Builder) {
        // Create custom adapter with styled text
        val adapter = FontSizeAdapter(
            context,
            android.R.layout.select_dialog_singlechoice,
            entries
        )

        val checkedItem = findIndexOfValue(value)
        builder.setSingleChoiceItems(adapter, checkedItem) { dialog, which ->
            if (which >= 0 && entryValues != null) {
                val value = entryValues[which].toString()
                if (callChangeListener(value)) {
                    setValue(value)
                }
            }
            dialog.dismiss()
        }

        // Remove default positive button since we handle selection in item click
        builder.setPositiveButton(null, null)
    }

    private inner class FontSizeAdapter(
        context: Context,
        resource: Int,
        private val items: Array<CharSequence>
    ) : ArrayAdapter<CharSequence>(context, resource, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val textView = view.findViewById<TextView>(android.R.id.text1)

            // Get the font scale for this position
            val enumValue = entryValues[position].toString()
            val scale = FONT_SCALES[enumValue] ?: 1.0f
            val scaledSize = BASE_SIZE_SP * scale

            // Apply the font size to the preview
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize)

            return view
        }
    }
}
