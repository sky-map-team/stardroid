/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.locale

import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import com.google.android.stardroid.catalog.LocaleSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The locale every catalog read is made in, kept current across a language change.
 *
 * Switching language — the system per-app picker `android:localeConfig` enables, or the device
 * language — recreates the activities but leaves the process alive. Resource strings therefore
 * come back translated while anything captured once at startup does not, and a captured
 * [LocaleSpec] left every catalog-sourced string — object names, map labels, info-card prose —
 * in the old language until the process happened to die.
 *
 * So nothing captures one: [current] re-reads the live configuration on each call for the
 * on-demand reads, and [specs] re-emits on a configuration change for the layers, which rebuild
 * their scenes from a flow. `MutableStateFlow` conflates equal values and [LocaleSpec] compares
 * by tag, so configuration changes that leave the language alone (a rotation) emit nothing.
 */
@Singleton
class LocaleSource
    @Inject
    constructor(
        private val application: Application,
    ) {
        /** The app's locale right now, read from the live configuration — never cached. */
        val current: LocaleSpec
            get() = LocaleSpec(application.resources.configuration.locales[0].toLanguageTag())

        private val mutableSpecs = MutableStateFlow(current)

        /** [current], re-emitted whenever the configuration changes. */
        val specs: StateFlow<LocaleSpec> = mutableSpecs.asStateFlow()

        init {
            application.registerComponentCallbacks(
                object : ComponentCallbacks {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        mutableSpecs.value = current
                    }

                    override fun onLowMemory() = Unit
                },
            )
        }
    }
