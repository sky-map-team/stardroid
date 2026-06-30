/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.util

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExperimentConfigModule::class])
object ExperimentConfigTestModule {
    @Provides
    @Singleton
    fun provideExperimentConfig(): ExperimentConfig = object : ExperimentConfig {
        override fun isEnabled(experiment: Experiment): Boolean = when (experiment) {
            Experiment.WARM_WELCOME -> true
            else -> false
        }

        override fun getDouble(experiment: Experiment, default: Double): Double = default

        override fun waitForInitialFetch(timeoutMs: Long): Boolean = true
    }
}
