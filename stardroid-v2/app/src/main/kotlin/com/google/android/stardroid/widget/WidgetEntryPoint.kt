/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.content.Context
import com.google.android.stardroid.CatalogAccess
import com.google.android.stardroid.locale.LocaleSource
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.ExperimentConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The app singletons the widgets need, reached without a ViewModel — widget code runs in
 * receivers and workers, not activities (D75).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): Settings

    fun experimentConfig(): ExperimentConfig

    fun catalogAccess(): CatalogAccess

    fun localeSource(): LocaleSource
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
