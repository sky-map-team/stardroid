package com.google.android.stardroid.control

import android.content.Context
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
abstract class LocationProviderModule {

    @Binds
    @ActivityScoped
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    companion object {
        @Provides
        @ActivityScoped
        fun provideFusedLocationProviderClient(@ApplicationContext context: Context) =
            LocationServices.getFusedLocationProviderClient(context)
    }
}
