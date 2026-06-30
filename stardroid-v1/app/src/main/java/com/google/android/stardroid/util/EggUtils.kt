/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.util

import java.util.Calendar

/**
 * THere's nothing to see here.
 */
fun processImage(objectId: String, originalImagePath: String?): String? {
    return when (objectId) {
        "moon" -> if (isAprilFirst()) "celestial_images/planets/moon_melies.webp" else originalImagePath
        else -> originalImagePath
    }
}

fun isAprilFirst(): Boolean {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.MONTH) == Calendar.APRIL &&
            calendar.get(Calendar.DAY_OF_MONTH) == 1
}