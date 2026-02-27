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

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.util.AssetImageLoader
import com.google.android.stardroid.util.ImageLoadHandle

/**
 * Fullscreen dialog that displays an expanded celestial image.
 * Tapping the image or pressing back closes the dialog.
 */
class ImageExpandDialogFragment : DialogFragment() {

    private var imageLoadHandle: ImageLoadHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val isNight = ActivityLightLevelManager.isNightMode(prefs)
        val nightTextColor = requireContext().getColor(R.color.night_text_color)

        val view = inflater.inflate(R.layout.dialog_image_expand, container, false)
        val imageView = view.findViewById<ImageView>(R.id.expanded_image)

        val imagePath = arguments?.getString(ARG_IMAGE_PATH)
        if (imagePath != null) {
            imageLoadHandle = AssetImageLoader.loadBitmapAsync(requireContext().assets, imagePath) { bitmap ->
                if (bitmap != null && isAdded) {
                    imageView.setImageBitmap(bitmap)
                    if (isNight) {
                        imageView.setColorFilter(nightTextColor, PorterDuff.Mode.MULTIPLY)
                    }
                }
            }
        }

        // Display image credit if available
        val creditView = view.findViewById<TextView>(R.id.expanded_image_credit)
        val imageCredit = arguments?.getString(ARG_IMAGE_CREDIT)
        if (imageCredit != null) {
            creditView.text = imageCredit
            creditView.visibility = View.VISIBLE
        }
        if (isNight) creditView.setTextColor(nightTextColor)

        val tapHint = view.findViewById<TextView>(R.id.expanded_image_tap_hint)
        if (isNight) tapHint.setTextColor(nightTextColor)
        tapHint.animate().alpha(0f).setStartDelay(2000).setDuration(500).start()

        view.setOnClickListener { dismiss() }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageLoadHandle?.cancel()
        imageLoadHandle = null
    }

    companion object {
        private const val ARG_IMAGE_PATH = "image_path"
        private const val ARG_IMAGE_CREDIT = "image_credit"

        fun newInstance(imagePath: String, imageCredit: String? = null): ImageExpandDialogFragment {
            return ImageExpandDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, imagePath)
                    imageCredit?.let { putString(ARG_IMAGE_CREDIT, it) }
                }
            }
        }
    }
}
