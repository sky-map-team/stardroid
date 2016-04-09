package com.google.android.stardroid;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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

  @Provides @Singleton
  StardroidApplication provideApplication() {
    return app;
  }

  @Provides @Singleton
  SharedPreferences provideSharedPreferences() {
    Log.d(TAG, "Providing shared preferences");
    return PreferenceManager.getDefaultSharedPreferences(app);
  }

  @Provides @Singleton
  ExecutorService provideBackgroundExecutor() {
    return new ScheduledThreadPoolExecutor(1);
  }

  @Provides @Singleton
  AssetManager provideAssetManager() {
    return app.getAssets();
  }

  @Provides @Singleton
  Resources provideResources() {
    return app.getResources();
  }
}
