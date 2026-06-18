package com.google.android.stardroid.control

import android.content.Context
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationProviderModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    companion object {
        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(@ApplicationContext context: Context) =
            LocationServices.getFusedLocationProviderClient(context)
    }
}
