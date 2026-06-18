package com.google.android.stardroid.activities.util

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
abstract class MapAdapterModule {

    @Binds
    @ActivityScoped
    abstract fun bindMapAdapter(impl: GeoapifyMapsAdapter): MapAdapter
}
