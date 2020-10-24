package com.google.android.stardroid;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.AstronomerModelImpl;
import com.google.android.stardroid.control.MagneticDeclinationCalculator;
import com.google.android.stardroid.control.RealMagneticDeclinationCalculator;
import com.google.android.stardroid.control.ZeroMagneticDeclinationCalculator;
import com.google.android.stardroid.layers.EclipticLayer;
import com.google.android.stardroid.layers.GridLayer;
import com.google.android.stardroid.layers.HorizonLayer;
import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.layers.MeteorShowerLayer;
import com.google.android.stardroid.layers.NewConstellationsLayer;
import com.google.android.stardroid.layers.NewMessierLayer;
import com.google.android.stardroid.layers.NewStarsLayer;
import com.google.android.stardroid.layers.PlanetsLayer;
import com.google.android.stardroid.layers.SkyGradientLayer;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.AnalyticsInterface;
import com.google.android.stardroid.util.MiscUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module
 * Created by johntaylor on 3/26/16.
 */
@Module
public class ApplicationModule {
  private static final String TAG = MiscUtil.getTag(ApplicationModule.class);
  private StardroidApplication app;

  public ApplicationModule(StardroidApplication app) {
    Log.d(TAG, "Creating application module for " + app);
    this.app = app;
  }

  @Provides
  @Singleton
  StardroidApplication provideApplication() {
    return app;
  }

  @Provides
  Context provideContext() {
    return app;
  }

  @Provides
  @Singleton
  SharedPreferences provideSharedPreferences() {
    Log.d(TAG, "Providing shared preferences");
    return PreferenceManager.getDefaultSharedPreferences(app);
  }

  @Provides
  @Singleton
  LocationManager provideLocationManager() {
    return ContextCompat.getSystemService(app, LocationManager.class);
  }

  @Provides
  @Singleton
  AstronomerModel provideAstronomerModel(
      @Named("zero") MagneticDeclinationCalculator magneticDeclinationCalculator) {
    return new AstronomerModelImpl(magneticDeclinationCalculator);
  }

  @Provides
  @Singleton
  @Named("zero")
  MagneticDeclinationCalculator provideDefaultMagneticDeclinationCalculator() {
    return new ZeroMagneticDeclinationCalculator();
  }

  @Provides
  @Singleton
  @Named("real")
  MagneticDeclinationCalculator provideRealMagneticDeclinationCalculator() {
    return new RealMagneticDeclinationCalculator();
  }

  @Provides
  @Singleton
  AnalyticsInterface provideAnalytics(Analytics analytics) {
    return analytics;
  }

  @Provides
  @Singleton
  ExecutorService provideBackgroundExecutor() {
    return new ScheduledThreadPoolExecutor(1);
  }

  @Provides
  @Singleton
  AssetManager provideAssetManager() {
    return app.getAssets();
  }

  @Provides
  @Singleton
  Resources provideResources() {
    return app.getResources();
  }

  @Provides
  @Singleton
  SensorManager provideSensorManager() {
    return ContextCompat.getSystemService(app, SensorManager.class);
  }

  @Provides
  @Singleton
  ConnectivityManager provideConnectivityManager() {
    return ContextCompat.getSystemService(app, ConnectivityManager.class);
  }

  @Provides
  @Singleton
  AccountManager provideAccountManager(Context context) {
    return AccountManager.get(context);
  }

  @Provides @Singleton
  LayerManager provideLayerManager(
      AssetManager assetManager, Resources resources, AstronomerModel model,
      SharedPreferences preferences) {
    Log.i(TAG, "Initializing LayerManager");
    LayerManager layerManager = new LayerManager(preferences);
    layerManager.addLayer(new NewStarsLayer(assetManager, resources));
    layerManager.addLayer(new NewMessierLayer(assetManager, resources));
    layerManager.addLayer(new NewConstellationsLayer(assetManager, resources));
    layerManager.addLayer(new PlanetsLayer(model, resources, preferences));
    layerManager.addLayer(new MeteorShowerLayer(model, resources));
    layerManager.addLayer(new GridLayer(resources, 24, 9));
    layerManager.addLayer(new HorizonLayer(model, resources));
    layerManager.addLayer(new EclipticLayer(resources));
    layerManager.addLayer(new SkyGradientLayer(model, resources));
    // layerManager.addLayer(new IssLayer(resources, model));

    layerManager.initialize();
    return layerManager;
  }
}
