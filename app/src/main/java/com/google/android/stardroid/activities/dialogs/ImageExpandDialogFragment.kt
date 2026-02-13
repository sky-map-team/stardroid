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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.stardroid.R
import com.google.android.stardroid.util.AssetImageLoader

/**
 * Fullscreen dialog that displays an expanded celestial image.
 * Tapping the image or pressing back closes the dialog.
 */
class ImageExpandDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_image_expand, container, false)
        val imageView = view.findViewById<ImageView>(R.id.expanded_image)

        val imagePath = arguments?.getString(ARG_IMAGE_PATH)
        if (imagePath != null) {
            val bitmap = AssetImageLoader.loadBitmap(requireContext().assets, imagePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }

        // Display image credit if available
        val creditView = view.findViewById<TextView>(R.id.expanded_image_credit)
        val imageCredit = arguments?.getString(ARG_IMAGE_CREDIT)
        if (imageCredit != null) {
            creditView.text = imageCredit
            creditView.visibility = View.VISIBLE
        }

        val tapHint = view.findViewById<TextView>(R.id.expanded_image_tap_hint)
        tapHint.animate().alpha(0f).setStartDelay(2000).setDuration(500).start()

        view.setOnClickListener { dismiss() }

        return view
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
