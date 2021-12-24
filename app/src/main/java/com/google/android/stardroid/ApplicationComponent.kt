package com.google.android.stardroid

import android.accounts.AccountManager
import android.content.SharedPreferences
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import com.google.android.stardroid.activities.EditSettingsActivity
import com.google.android.stardroid.activities.ImageDisplayActivity
import com.google.android.stardroid.activities.ImageGalleryActivity
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.control.MagneticDeclinationCalculator
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.search.SearchTermsProvider
import com.google.android.stardroid.util.AnalyticsInterface
import dagger.Component
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger component.
 * Created by johntaylor on 3/26/16.
 */
@Singleton
@Component(modules = [ApplicationModule::class])
interface ApplicationComponent {
  // What we expose to dependent components
  fun provideStardroidApplication(): StardroidApplication
  fun provideSharedPreferences(): SharedPreferences
  fun provideSensorManager(): SensorManager?
  fun provideConnectivityManager(): ConnectivityManager?
  fun provideAstronomerModel(): AstronomerModel
  fun provideLocationManager(): LocationManager?
  fun provideLayerManager(): LayerManager
  fun provideAccountManager(): AccountManager
  fun provideAnalytics(): AnalyticsInterface

  @Named("zero")
  fun provideMagDec1(): MagneticDeclinationCalculator

  @Named("real")
  fun provideMagDec2(): MagneticDeclinationCalculator

  // Who can we inject
  fun inject(app: StardroidApplication)
  fun inject(activity: EditSettingsActivity)
  fun inject(activity: ImageDisplayActivity)
  fun inject(activity: ImageGalleryActivity)
  fun inject(provider: SearchTermsProvider)
}