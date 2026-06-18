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

import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.stardroid.R
import coil.load
import coil.request.Disposable
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fullscreen dialog that displays an expanded celestial image.
 * Tapping the image or pressing back closes the dialog.
 */
@AndroidEntryPoint
class ImageExpandDialogFragment : DialogFragment() {
    @Inject lateinit var preferences: SharedPreferences

    private var imageDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val isNight = ActivityLightLevelManager.isNightMode(preferences)
        val nightTextColor = requireContext().getColor(R.color.night_text_color)

        val view = inflater.inflate(R.layout.dialog_image_expand, container, false)
        val imageView = view.findViewById<ImageView>(R.id.expanded_image)

        val imagePath = arguments?.getString(ARG_IMAGE_PATH)
        if (imagePath != null) {
            imageDisposable = imageView.load("file:///android_asset/$imagePath") {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        if (isNight) imageView.setColorFilter(nightTextColor, PorterDuff.Mode.MULTIPLY)
                    },
                    onError = { _, result ->
                        Log.w("ImageExpandDialogFragment", "Failed to load image: $imagePath", result.throwable)
                    }
                )
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
        imageDisposable?.dispose()
        imageDisposable = null
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
