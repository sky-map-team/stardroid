/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid

import android.app.Application
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.data.RoomCatalogRepository
import com.google.android.stardroid.data.SkyMapDatabaseFactory
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.ResourceLayerStrings
import com.google.android.stardroid.locale.LocaleSource
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.satellites.satelliteElementsFlow
import com.google.android.stardroid.satellites.satelliteEntryPoint
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.time.TimeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The process's one catalog handle and the layer registry built over it. Suspend-initialized
 * state Hilt cannot provide directly (opening the bundled DB does I/O and may run the D24/G11
 * recovery), so the mutex-guarded first-use caching lives here rather than in a `@Provides`
 * method; everything synchronous stays in the Hilt graph (D59).
 */
@Singleton
class CatalogAccess
    @Inject
    constructor(
        private val application: Application,
        private val timeController: TimeController,
        private val locationController: LocationController,
        private val localeSource: LocaleSource,
        private val settings: Settings,
        private val experimentConfig: ExperimentConfig,
    ) {
        private val catalogMutex = Mutex()
        private var catalog: CatalogRepository? = null

        /**
         * Opens the bundled DB (with D24/G11 recovery) on first use and keeps it open for the
         * process lifetime. Shared by the layer registry and the search feature, so both see
         * the same store.
         */
        suspend fun repository(): CatalogRepository =
            withContext(Dispatchers.IO) {
                catalogMutex.withLock {
                    catalog
                        ?: RoomCatalogRepository(
                            SkyMapDatabaseFactory.createWithRecovery(application),
                        ).also { catalog = it }
                }
            }

        private val registryMutex = Mutex()
        private var registry: LayerRegistry? = null

        /** Builds the full layer set over [repository] on first use; process-cached. */
        suspend fun layerRegistry(): LayerRegistry {
            val repository = repository()
            return withContext(Dispatchers.IO) {
                registryMutex.withLock {
                    registry
                        ?: LayerRegistry.create(
                            catalog = repository,
                            locale = localeSource.specs,
                            // A new reader per locale, so the computed layers' own labels
                            // ("Zenith", the planet names) re-resolve with the scene.
                            strings =
                                localeSource.specs.map {
                                    ResourceLayerStrings(application.resources)
                                },
                            clock = timeController.times,
                            location = locationController.locations,
                            ephemeris = MeeusEphemeris,
                            settings = settings,
                            satelliteElements =
                                satelliteElementsFlow(
                                    satelliteEntryPoint(application).satelliteElementsRepository(),
                                ),
                            satellitesEnabled =
                                experimentConfig.isEnabled(Experiment.SATELLITES),
                        ).also { registry = it }
                }
            }
        }
    }
