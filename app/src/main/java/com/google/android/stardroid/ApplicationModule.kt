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
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.google.android.stardroid.control.*
import com.google.android.stardroid.layers.*
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.AnalyticsInterface
import com.google.android.stardroid.util.MiscUtil.getTag
import dagger.Module
import dagger.Provides
import java.util.concurrent.ScheduledThreadPoolExecutor
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger module
 * Created by johntaylor on 3/26/16.
 */
@Module
class ApplicationModule(private val app: StardroidApplication) {

  @Provides
  @Singleton
  fun provideApplication() = app

  @Provides
  fun provideContext(): Context = app

  @Provides
  @Singleton
  fun provideSharedPreferences() = PreferenceManager.getDefaultSharedPreferences(app)

  @Provides
  @Singleton
  fun provideLocationManager() = app.getSystemService<LocationManager>()

  @Provides
  @Singleton
  fun provideAstronomerModel(
    @Named("zero") magneticDeclinationCalculator: MagneticDeclinationCalculator
  ): AstronomerModel = AstronomerModelImpl(magneticDeclinationCalculator)

  @Provides
  @Singleton
  @Named("zero")
  fun provideDefaultMagneticDeclinationCalculator(): MagneticDeclinationCalculator = ZeroMagneticDeclinationCalculator()

  @Provides
  @Singleton
  @Named("real")
  fun provideRealMagneticDeclinationCalculator(): MagneticDeclinationCalculator = RealMagneticDeclinationCalculator()

  @Provides
  @Singleton
  fun provideAnalytics(analytics: Analytics): AnalyticsInterface = analytics

  @Provides
  @Singleton
  fun provideBackgroundExecutor() = ScheduledThreadPoolExecutor(1)

  @Provides
  @Singleton
  fun provideAssetManager() = app.assets

  @Provides
  @Singleton
  fun provideResources() = app.resources

  @Provides
  @Singleton
  fun provideSensorManager() = app.getSystemService<SensorManager>()

  @Provides
  @Singleton
  fun provideConnectivityManager() = app.getSystemService<ConnectivityManager>()

  @Provides
  @Singleton
  fun provideAccountManager(context: Context) = AccountManager.get(context)

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
}