/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

/**
 * The app's own link to an object's card, `<scheme>://object/<catalog id>`, which iOS widgets open
 * the app with (Android passes an intent extra instead). The scheme differs between builds so a
 * development install never answers the App Store app's links.
 */
object ObjectLink {
    private const val HOST = "object"

    fun build(
        scheme: String,
        objectId: String,
    ): String = "$scheme://$HOST/$objectId"

    /** The catalog id [url] asks for, or null when it isn't an object link. */
    fun objectIdOf(url: String): String? {
        val rest = url.substringAfter("://", missingDelimiterValue = "")
        if (!rest.startsWith("$HOST/")) return null
        return rest
            .removePrefix("$HOST/")
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .takeIf { it.isNotEmpty() }
    }
}
