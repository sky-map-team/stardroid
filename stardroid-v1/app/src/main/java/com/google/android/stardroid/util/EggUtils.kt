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