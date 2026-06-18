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
