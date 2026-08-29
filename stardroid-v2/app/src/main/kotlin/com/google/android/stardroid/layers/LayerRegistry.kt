/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Owns the full set of [SkyLayer] instances (layers-and-app.md). Constructed once per process
 * in the app graph; a future downloaded pack can contribute additional catalog-backed layers
 * as data. Visibility toggles live in DataStore, keyed by [LayerId] — the registry itself
 * knows nothing about preferences.
 */
class LayerRegistry(
    val layers: List<SkyLayer>,
) {
    companion object {
        /**
         * The eight current layers in toggle-UI display order. Static so the toggle UI can
         * enumerate ids before the catalog DB has opened; [create] builds the matching
         * instances. Comets and the sky-gradient render state are later slices (D37).
         */
        val TOGGLEABLE_IDS: List<LayerId> =
            listOf(
                CatalogLayers.STARS_LAYER_ID,
                CatalogLayers.CONSTELLATIONS_LAYER_ID,
                CatalogLayers.DEEP_SKY_LAYER_ID,
                SolarSystemLayer.LAYER_ID,
                MeteorShowerLayer.LAYER_ID,
                GridLayer.LAYER_ID,
                HorizonLayer.LAYER_ID,
                EclipticLayer.LAYER_ID,
                SatelliteLayer.LAYER_ID,
            )

        /**
         * The toggle rows to show, with satellites present only when [Experiment.SATELLITES] is on
         * (D92).
         *
         * Absent rather than disabled, matching how the `CAMERA_AR`-gated surfaces behave: a
         * toggle for a feature that cannot do anything is worse than no toggle, because it invites
         * a user to turn it on and conclude the app is broken.
         */
        fun toggleableIds(satellitesEnabled: Boolean): List<LayerId> =
            if (satellitesEnabled) {
                TOGGLEABLE_IDS
            } else {
                TOGGLEABLE_IDS - SatelliteLayer.LAYER_ID
            }

        /**
         * Every (layer, parameter) pair the Layers sheet can render, in the same static form
         * and for the same reason as [TOGGLEABLE_IDS]: the sheet lays out its rows before the
         * catalog DB has opened, so it cannot ask live layer instances. Each entry references
         * the declaring layer's own list rather than copying it (D91), so a parameter exists
         * in exactly one place.
         */
        val PARAMETERS: List<Pair<LayerId, LayerParameter>> =
            SolarSystemLayer.PARAMETERS.map { SolarSystemLayer.LAYER_ID to it } +
                SatelliteLayer.PARAMETERS.map { SatelliteLayer.LAYER_ID to it }

        /**
         * Wires every layer to its declared dependencies — no shared context bundle. [settings]
         * is one such dependency: a layer with user-settable parameters (D87) reads its own
         * values from it, so the registry never names an individual parameter (D91).
         */
        fun create(
            catalog: CatalogRepository,
            locale: Flow<LocaleSpec>,
            strings: Flow<LayerStrings>,
            clock: Flow<Instant>,
            location: Flow<LatLong>,
            ephemeris: Ephemeris,
            settings: Settings,
            satelliteElements: Flow<SatelliteElements>,
            satellitesEnabled: Boolean,
        ): LayerRegistry =
            LayerRegistry(
                CatalogLayers.create(catalog, locale) +
                    listOfNotNull(
                        // Not registered at all when the experiment is off, so the feature is
                        // absent rather than present-but-inert.
                        SatelliteLayer(satelliteElements, clock, location)
                            .takeIf { satellitesEnabled },
                        MeteorShowerLayer(catalog, locale, clock),
                        GridLayer(strings),
                        EclipticLayer(strings),
                        HorizonLayer(clock, location, strings),
                        SolarSystemLayer.create(
                            ephemeris,
                            clock,
                            strings,
                            location,
                            PlanetImages(),
                            settings,
                        ),
                    ),
            )
    }
}
