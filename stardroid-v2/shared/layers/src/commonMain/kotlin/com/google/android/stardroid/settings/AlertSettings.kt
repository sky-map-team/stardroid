/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.settings

import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

// The alert settings are layer parameters (#1092), so the Layers sheet and Settings agree. They
// are extensions on [Settings] here rather than its members, as they are on master's single
// module, because :shared:layers depends on :shared:model and not the reverse. The package is
// Settings' own, so callers there need no import.

/** Meteor-shower peak notifications (D77). Off until the user opts in — quiet by default. */
val Settings.showerAlertsEnabled: Flow<Boolean>
    get() =
        layerParameter(
            MeteorShowerLayer.LAYER_ID,
            LayerParameter.SHOWER_ALERTS,
            LayerParameter.SHOWER_ALERTS_PARAMETER.defaultValue,
        ).map { it.toBoolean() }

suspend fun Settings.setShowerAlertsEnabled(enabled: Boolean) =
    setLayerParameter(
        MeteorShowerLayer.LAYER_ID,
        LayerParameter.SHOWER_ALERTS,
        enabled.toString(),
    )

/**
 * The lunar-eclipse reminder (D106), backed by the Solar System layer's alert toggle so the
 * Layers sheet and Settings agree. Off until the user opts in.
 */
val Settings.eclipseAlertsEnabled: Flow<Boolean>
    get() =
        layerParameter(
            SolarSystemLayer.LAYER_ID,
            LayerParameter.ECLIPSE_ALERTS,
            LayerParameter.ECLIPSE_ALERTS_PARAMETER.defaultValue,
        ).map { it.toBoolean() }

suspend fun Settings.setEclipseAlertsEnabled(enabled: Boolean) =
    setLayerParameter(
        SolarSystemLayer.LAYER_ID,
        LayerParameter.ECLIPSE_ALERTS,
        enabled.toString(),
    )

/**
 * Satellite pass alerts (D92), backed by the Satellites layer's alert toggle so the Layers
 * sheet and Settings agree. Off until the user opts in.
 */
val Settings.passAlertsEnabled: Flow<Boolean>
    get() =
        // Mirrors the runtime gate (`passAlertsEnabled(context)`): with the Satellites layer
        // off no alert fires, so the row must not claim it is on.
        combine(
            layerEnabled(SatelliteLayer.LAYER_ID),
            layerParameter(
                SatelliteLayer.LAYER_ID,
                LayerParameter.PASS_ALERTS,
                LayerParameter.PASS_ALERTS_PARAMETER.defaultValue,
            ).map { it.toBoolean() },
        ) { layerOn, alerts -> layerOn && alerts }

/** Turning alerts on also turns the Satellites layer on, since nothing fires without it. */
suspend fun Settings.setPassAlertsEnabled(enabled: Boolean) {
    if (enabled) setLayerEnabled(SatelliteLayer.LAYER_ID, true)
    setLayerParameter(
        SatelliteLayer.LAYER_ID,
        LayerParameter.PASS_ALERTS,
        enabled.toString(),
    )
}
