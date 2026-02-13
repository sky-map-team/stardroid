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
package com.google.android.stardroid.util

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Utility for loading images from the assets directory.
 */
object AssetImageLoader {
    private const val TAG = "AssetImageLoader"

    /**
     * Loads a Bitmap from the given asset path.
     *
     * @param assetManager The AssetManager to load from
     * @param path The path relative to assets/ (e.g., "celestial_images/hubble_jupiter.jpg")
     * @return The decoded Bitmap, or null if loading fails
     */
    fun loadBitmap(assetManager: AssetManager, path: String): Bitmap? {
        return try {
            assetManager.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load image: $path", e)
            null
        }
    }
}
