/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.render.api.ImageRef
import platform.Foundation.NSBundle
import platform.UIKit.UIImage

/**
 * The renderer's images from the app bundle — the iOS twin of Android's `AssetImageLoader`,
 * with the same keys and paths: the Xcode project copies `app/src/main/assets/catalog` and
 * `planets` into the bundle as they are. iOS decodes WebP natively.
 */
object BundleImageLoader {
    private const val ICON_PREFIX = "icon/"
    private const val PLANET_PREFIX = "planet/"

    fun load(ref: ImageRef): UIImage? {
        val (directory, name) =
            when {
                ref.key.startsWith(
                    ICON_PREFIX,
                ) -> "catalog/icons" to ref.key.removePrefix(ICON_PREFIX)
                ref.key.startsWith(
                    PLANET_PREFIX,
                ) -> "planets" to ref.key.removePrefix(PLANET_PREFIX)
                else -> return null
            }
        // Keys are internal, but cheap defense keeps a malformed one from becoming a path.
        if (name.isEmpty() || !name.all { it.isLowerCase() || it.isDigit() || it == '_' }) {
            return null
        }
        val path = NSBundle.mainBundle.pathForResource(name, "webp", directory) ?: return null
        return UIImage.imageWithContentsOfFile(path)
    }
}
