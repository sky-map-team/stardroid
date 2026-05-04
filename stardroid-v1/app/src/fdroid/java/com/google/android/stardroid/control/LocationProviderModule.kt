package com.google.android.stardroid.control

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
abstract class LocationProviderModule {

    @Binds
    @ActivityScoped
    abstract fun bindLocationProvider(impl: PlatformLocationProvider): LocationProvider
}
