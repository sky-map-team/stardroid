/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.widgets

import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.surfaces.ObjectLink
import com.google.android.stardroid.surfaces.WidgetSkyCodec
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/**
 * What the app hands its widget extension through their App Group (ios-platform-surfaces.md
 * §6.1): the saved location, and the tonight widget's showers and passes ([WidgetSkyCodec]),
 * which come from the catalog database and the element sets. The app's settings stay in its own
 * DataStore; it mirrors these few keys here whenever they change, and the extension only reads
 * them.
 */
object WidgetSharedStore {
    private const val LATITUDE = "savedLocation.latitudeDeg"
    private const val LONGITUDE = "savedLocation.longitudeDeg"
    private const val SHOWERS = "tonight.showers"
    private const val PASSES = "tonight.passes"

    // Null when the bundle names no group (a test host), which reads as nothing saved.
    private val defaults: NSUserDefaults? by lazy {
        infoString(APP_GROUP_KEY)?.let { NSUserDefaults(suiteName = it) }
    }

    var savedLocation: LatLong?
        get() {
            val store = defaults ?: return null
            if (store.objectForKey(LATITUDE) == null || store.objectForKey(LONGITUDE) == null) {
                return null
            }
            return LatLong(store.doubleForKey(LATITUDE), store.doubleForKey(LONGITUDE))
        }
        set(value) {
            val store = defaults ?: return
            if (value == null) {
                store.removeObjectForKey(LATITUDE)
                store.removeObjectForKey(LONGITUDE)
            } else {
                store.setDouble(value.latitudeDeg, LATITUDE)
                store.setDouble(value.longitudeDeg, LONGITUDE)
            }
        }

    /** In the app's language: the names are the catalog's. */
    var showers: List<MeteorShower>
        get() = WidgetSkyCodec.decodeShowers(defaults?.stringForKey(SHOWERS).orEmpty())
        set(value) {
            defaults?.setObject(WidgetSkyCodec.encodeShowers(value), SHOWERS)
        }

    /** Good passes for the next couple of days; empty with satellites or their layer off. */
    var passes: List<SatellitePass>
        get() = WidgetSkyCodec.decodePasses(defaults?.stringForKey(PASSES).orEmpty())
        set(value) {
            defaults?.setObject(WidgetSkyCodec.encodePasses(value), PASSES)
        }
}

// Both bundles' Info.plist carry these, set per build configuration in project.yml.
internal const val APP_GROUP_KEY = "SkyMapAppGroup"
internal const val URL_SCHEME_KEY = "SkyMapURLScheme"

/** `<scheme>://object/<id>`, which opens the app on that object's card. */
internal fun objectLink(objectId: String): String =
    ObjectLink.build(infoString(URL_SCHEME_KEY) ?: "skymap", objectId)

internal fun infoString(key: String): String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotEmpty() }
