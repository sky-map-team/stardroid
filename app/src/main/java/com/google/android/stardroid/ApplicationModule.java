package com.google.android.stardroid;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module
 * Created by johntaylor on 3/26/16.
 */
@Module
public class ApplicationModule {
  private static final String TAG = MiscUtil.getTag(StardroidApplication.class);
  private Application app;

  public ApplicationModule(Application app) {
    Log.d(TAG, "Creating application module for " + app);
    this.app = app;
  }

  @Provides
  @Singleton
  SharedPreferences provideSharedPreferences() {
    Log.d(TAG, "Providing shared preferences");
    return PreferenceManager.getDefaultSharedPreferences(app);
  }
}
