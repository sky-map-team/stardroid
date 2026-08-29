/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.content.Context
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.data.satellites.SatelliteElementsRepository
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.ExperimentConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The app singletons [SatelliteRefreshWorker] needs, reached without a ViewModel — the fetch runs
 * in a WorkManager worker, not an activity.
 *
 * Follows the same shape as `WidgetEntryPoint` (D75) rather than adding `androidx.hilt:hilt-work`
 * and a `HiltWorkerFactory`: the entry-point pattern already exists here for exactly this
 * situation, and one worker does not justify a new dependency plus the WorkManager initializer
 * surgery `@HiltWorker` requires.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SatelliteEntryPoint {
    fun satelliteElementsRepository(): SatelliteElementsRepository

    fun settings(): Settings

    fun experimentConfig(): ExperimentConfig

    fun analytics(): Analytics
}

fun satelliteEntryPoint(context: Context): SatelliteEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, SatelliteEntryPoint::class.java)
