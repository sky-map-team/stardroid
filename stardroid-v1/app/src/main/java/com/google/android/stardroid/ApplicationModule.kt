package com.google.android.stardroid

import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.google.android.stardroid.control.*
import com.google.android.stardroid.layers.*
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.ExperimentConfig
import com.google.android.stardroid.util.ExperimentConfigImpl
import com.google.android.stardroid.util.MiscUtil.getTag
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger module
 * Created by johntaylor on 3/26/16.
 */
@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {

  @Provides
  @Singleton
  fun provideApplication(@ApplicationContext context: Context): StardroidApplication =
      context as StardroidApplication

  @Provides
  @Singleton
  fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
      PreferenceManager.getDefaultSharedPreferences(context)

  @Provides
  @Singleton
  fun provideLocationManager(@ApplicationContext context: Context) =
      context.getSystemService<LocationManager>()

  @Provides
  @Singleton
  fun provideAstronomerModel(
    @Named("zero") magneticDeclinationCalculator: MagneticDeclinationCalculator
  ): AstronomerModel = AstronomerModelImpl(magneticDeclinationCalculator)

  @Provides
  @Singleton
  @Named("zero")
  fun provideDefaultMagneticDeclinationCalculator(): MagneticDeclinationCalculator =
      ZeroMagneticDeclinationCalculator()

  @Provides
  @Singleton
  @Named("real")
  fun provideRealMagneticDeclinationCalculator(): MagneticDeclinationCalculator =
      RealMagneticDeclinationCalculator()

  @Provides
  @Singleton
  fun provideAnalytics(analytics: Analytics): AnalyticsInterface = analytics

  @Provides
  @Singleton
  fun provideExperimentConfig(impl: ExperimentConfigImpl): ExperimentConfig = impl

  @Provides
  @Singleton
  fun provideBackgroundExecutor(): ScheduledExecutorService {
    val cpuCount = Runtime.getRuntime().availableProcessors()
    // I/O-Bound (Network calls, Database, File writing)
    val corePoolSize = cpuCount * 2
    return ScheduledThreadPoolExecutor(corePoolSize)
  }

  @Provides
  @Singleton
  fun provideAssetManager(@ApplicationContext context: Context): AssetManager = context.assets

  @Provides
  @Singleton
  fun provideResources(@ApplicationContext context: Context): Resources = context.resources

  @Provides
  @Singleton
  fun provideSensorManager(@ApplicationContext context: Context) =
      context.getSystemService<SensorManager>()

  @Provides
  @Singleton
  fun provideConnectivityManager(@ApplicationContext context: Context) =
      context.getSystemService<ConnectivityManager>()

  @Provides
  @Singleton
  fun providePowerManager(@ApplicationContext context: Context) = context.getSystemService<PowerManager>()

  @Provides
  @Singleton
  fun provideAccountManager(@ApplicationContext context: Context): AccountManager =
      AccountManager.get(context)

  @Provides
  @Singleton
  fun provideLayerManager(
    assetManager: AssetManager, resources: Resources, model: AstronomerModel?,
    preferences: SharedPreferences
  ): LayerManager {
    Log.i(TAG, "Initializing LayerManager")
    val layerManager = LayerManager(preferences)
    layerManager.addLayer(StarsLayer(assetManager, resources, preferences))
    layerManager.addLayer(DeepSkyObjectLayer(assetManager, resources, preferences))
    layerManager.addLayer(ConstellationsLayer(assetManager, resources, preferences))
    layerManager.addLayer(SolarSystemLayer(model!!, resources, preferences))
    layerManager.addLayer(MeteorShowerLayer(model, resources, preferences))
    layerManager.addLayer(CometsLayer(model, resources, preferences))
    layerManager.addLayer(GridLayer(resources, 24, 9, preferences))
    layerManager.addLayer(HorizonLayer(model, resources, preferences))
    layerManager.addLayer(EclipticLayer(resources, preferences))
    layerManager.addLayer(SkyGradientLayer(model, resources))
    // layerManager.addLayer(new IssLayer(resources, model));
    layerManager.initialize()
    return layerManager
  }

  companion object {
    private val TAG = getTag(ApplicationModule::class.java)
  }
}
