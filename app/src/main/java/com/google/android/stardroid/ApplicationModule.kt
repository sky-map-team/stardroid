package com.google.android.stardroid

import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.stardroid.control.*
import com.google.android.stardroid.layers.*
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil.getTag
import dagger.Module
import dagger.Provides
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger module
 * Created by johntaylor on 3/26/16.
 */
@Module
class ApplicationModule(app: StardroidApplication) {
  private val app: StardroidApplication

  @Provides
  @Singleton
  fun provideApplication(): StardroidApplication {
    return app
  }

  @Provides
  fun provideContext(): Context {
    return app
  }

  @Provides
  @Singleton
  fun provideSharedPreferences(): SharedPreferences {
    Log.d(TAG, "Providing shared preferences")
    return PreferenceManager.getDefaultSharedPreferences(app)
  }

  @Provides
  @Singleton
  fun provideLocationManager(): LocationManager? {
    return ContextCompat.getSystemService(app, LocationManager::class.java)
  }

  @Provides
  @Singleton
  fun provideAstronomerModel(
    @Named("zero") magneticDeclinationCalculator: MagneticDeclinationCalculator
  ): AstronomerModel {
    return AstronomerModelImpl(magneticDeclinationCalculator)
  }

  @Provides
  @Singleton
  @Named("zero")
  fun provideDefaultMagneticDeclinationCalculator(): MagneticDeclinationCalculator {
    return ZeroMagneticDeclinationCalculator()
  }

  @Provides
  @Singleton
  @Named("real")
  fun provideRealMagneticDeclinationCalculator(): MagneticDeclinationCalculator {
    return RealMagneticDeclinationCalculator()
  }

  @Provides
  @Singleton
  fun provideAnalytics(analytics: Analytics): AnalyticsInterface {
    return analytics
  }

  @Provides
  @Singleton
  fun provideBackgroundExecutor(): ExecutorService {
    return ScheduledThreadPoolExecutor(1)
  }

  @Provides
  @Singleton
  fun provideAssetManager(): AssetManager {
    return app.assets
  }

  @Provides
  @Singleton
  fun provideResources(): Resources {
    return app.resources
  }

  @Provides
  @Singleton
  fun provideSensorManager(): SensorManager? {
    return ContextCompat.getSystemService(app, SensorManager::class.java)
  }

  @Provides
  @Singleton
  fun provideConnectivityManager(): ConnectivityManager? {
    return ContextCompat.getSystemService(app, ConnectivityManager::class.java)
  }

  @Provides
  @Singleton
  fun provideAccountManager(context: Context): AccountManager {
    return AccountManager.get(context)
  }

  @Provides
  @Singleton
  fun provideLayerManager(
    assetManager: AssetManager, resources: Resources, model: AstronomerModel?,
    preferences: SharedPreferences
  ): LayerManager {
    Log.i(TAG, "Initializing LayerManager")
    val layerManager = LayerManager(preferences)
    layerManager.addLayer(StarsLayer(assetManager, resources))
    layerManager.addLayer(MessierLayer(assetManager, resources))
    layerManager.addLayer(ConstellationsLayer(assetManager, resources))
    layerManager.addLayer(SolarSystemLayer(model!!, resources, preferences))
    layerManager.addLayer(MeteorShowerLayer(model, resources))
    layerManager.addLayer(CometsLayer(model, resources))
    layerManager.addLayer(GridLayer(resources, 24, 9))
    layerManager.addLayer(HorizonLayer(model, resources))
    layerManager.addLayer(EclipticLayer(resources))
    layerManager.addLayer(SkyGradientLayer(model, resources))
    // layerManager.addLayer(new IssLayer(resources, model));
    layerManager.initialize()
    return layerManager
  }

  companion object {
    private val TAG = getTag(ApplicationModule::class.java)
  }

  init {
    Log.d(TAG, "Creating application module for $app")
    this.app = app
  }
}