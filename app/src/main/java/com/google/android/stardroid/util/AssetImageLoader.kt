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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Callback interface for async image loading (Java-compatible).
 */
fun interface BitmapCallback {
    fun onBitmapLoaded(bitmap: Bitmap?)
}

/**
 * A handle to a pending async image load. Call [cancel] when the recipient
 * (Activity/Fragment/View) is destroyed to break the reference chain and
 * prevent a memory leak.
 */
class ImageLoadHandle internal constructor(callback: BitmapCallback) {
    private val callbackRef = AtomicReference<BitmapCallback?>(callback)

    /** Clears the callback reference so the caller can be garbage-collected. */
    fun cancel() {
        callbackRef.set(null)
    }

    /** Delivers the result once, then clears the reference. */
    internal fun deliver(bitmap: Bitmap?) {
        callbackRef.getAndSet(null)?.onBitmapLoaded(bitmap)
    }
}

/**
 * Utility for loading images from the assets directory.
 */
object AssetImageLoader {
    private const val TAG = "AssetImageLoader"
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Cache up to 8 MB of bitmaps
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    /**
     * Loads a Bitmap from the given asset path synchronously.
     * This is called internally by loadBitmapAsync on a background thread.
     *
     * @param assetManager The AssetManager to load from
     * @param path The path relative to assets/ (e.g., "celestial_images/hubble_jupiter.jpg")
     * @return The decoded Bitmap, or null if loading fails
     */
    private fun loadBitmap(assetManager: AssetManager, path: String): Bitmap? {
        cache.get(path)?.let { return it }
        // Use RGB_565 instead of ARGB_8888 to halve memory usage (2 bytes/pixel vs 4).
        // These are opaque JPEG photos with no transparency, so alpha channel is unnecessary.
        // 65K colors is sufficient for space photography where banding is masked by texture.
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return try {
            assetManager.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.also {
                cache.put(path, it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load image: $path", e)
            null
        }
    }

    /**
     * Loads a Bitmap asynchronously off the UI thread.
     *
     * Returns an [ImageLoadHandle] that the caller **must** cancel (via [ImageLoadHandle.cancel])
     * when its host Activity/Fragment/View is destroyed. This prevents the callback from
     * holding a strong reference to a destroyed context while the task waits in the queue.
     *
     * @param assetManager The AssetManager to load from
     * @param path The path relative to assets/
     * @param callback Called on the main thread with the loaded bitmap (or null on failure)
     * @return A handle that can be cancelled to release the callback reference early
     */
    fun loadBitmapAsync(assetManager: AssetManager, path: String, callback: BitmapCallback): ImageLoadHandle {
        val handle = ImageLoadHandle(callback)
        cache.get(path)?.let {
            handle.deliver(it)
            return handle
        }
        executor.execute {
            val bitmap = loadBitmap(assetManager, path)
            mainHandler.post { handle.deliver(bitmap) }
        }
        return handle
    }

    /**
     * Trims the cache based on the memory pressure level.
     * Call this from Application.onTrimMemory().
     *
     * @param level The trim level from ComponentCallbacks2
     */
    fun onTrimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.d(TAG, "Clearing image cache (TRIM_MEMORY_COMPLETE)")
                cache.evictAll()
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.d(TAG, "Trimming image cache to half (TRIM_MEMORY_MODERATE)")
                cache.trimToSize(cache.maxSize() / 2)
            }
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                Log.d(TAG, "Trimming image cache to quarter (TRIM_MEMORY_BACKGROUND)")
                cache.trimToSize(cache.maxSize() / 4)
            }
        }
    }

    /**
     * Clears all cached bitmaps.
     */
    fun clearCache() {
        cache.evictAll()
    }
}
