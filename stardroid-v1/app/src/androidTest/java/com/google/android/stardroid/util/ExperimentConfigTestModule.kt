package com.google.android.stardroid.util

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExperimentConfigImpl::class])
object ExperimentConfigTestModule {
    @Provides
    @Singleton
    fun provideExperimentConfig(): ExperimentConfig = object : ExperimentConfig {
        override fun isEnabled(experiment: Experiment): Boolean = when (experiment) {
            Experiment.WARM_WELCOME -> true
        }

        override fun waitForInitialFetch(timeoutMs: Long): Boolean = true
    }
}
