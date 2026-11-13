/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.ios

import com.google.android.stardroid.catalog.CelestialObjectId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where something outside the app (a tapped notification now, a shared link later) asks for an
 * object's card. Held until the screen is up, because a tap that launches the app arrives before
 * there is a screen to show it on.
 */
internal object DeepLinks {
    private val _pending = MutableStateFlow<CelestialObjectId?>(null)
    val pending: StateFlow<CelestialObjectId?> = _pending.asStateFlow()

    fun openObject(id: CelestialObjectId) {
        _pending.value = id
    }

    /** Called once the card is on its way; a newer request that arrived meanwhile is kept. */
    fun consume(id: CelestialObjectId) {
        if (_pending.value == id) _pending.value = null
    }
}

/** For Swift: a notification carrying an object id was tapped. Without an id the map just opens. */
fun openObjectFromNotification(objectId: String?) {
    if (!objectId.isNullOrEmpty()) DeepLinks.openObject(CelestialObjectId(objectId))
}
