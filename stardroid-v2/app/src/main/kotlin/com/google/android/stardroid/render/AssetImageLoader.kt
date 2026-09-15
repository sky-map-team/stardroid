/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.stardroid.render.api.ImageRef
import java.io.IOException

/**
 * The app-side [ImageRef] resolver for the GLES1 backend: maps the `icon/<name>` namespace
 * (deep-sky type markers, [CatalogLayers.iconFor][com.google.android.stardroid.layers.CatalogLayers])
 * to the bundled `.webp` files under `assets/catalog/icons`, and the `planet/<name>` namespace
 * ([PlanetImages][com.google.android.stardroid.layers.PlanetImages]) to `assets/planets`.
 * Unknown keys and decode failures resolve to `null`, which the backend skips silently (D24).
 *
 * Called on the GL thread; returned bitmaps are owned (and recycled) by the renderer.
 */
class AssetImageLoader(private val assets: AssetManager) {
    fun load(ref: ImageRef): Bitmap? {
        val path =
            when {
                ref.key.startsWith(ICON_PREFIX) ->
                    assetPath("catalog/icons", ref.key.removePrefix(ICON_PREFIX))
                ref.key.startsWith(PLANET_PREFIX) ->
                    assetPath("planets", ref.key.removePrefix(PLANET_PREFIX))
                else -> null
            }
        return path?.let { decodeAsset(it) }
    }

    /** Keys are internal, but cheap defense keeps a malformed one from becoming a path. */
    private fun assetPath(
        dir: String,
        name: String,
    ): String? {
        if (name.isEmpty() || !name.all { it.isLowerCase() || it.isDigit() || it == '_' }) {
            return null
        }
        return "$dir/$name.webp"
    }

    private fun decodeAsset(path: String): Bitmap? =
        try {
            assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            Log.w(TAG, "Missing image asset: $path", e)
            null
        }

    private companion object {
        const val TAG = "AssetImageLoader"
        const val ICON_PREFIX = "icon/"
        const val PLANET_PREFIX = "planet/"
    }
}
