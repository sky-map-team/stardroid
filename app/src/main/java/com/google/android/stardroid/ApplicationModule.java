package com.google.android.stardroid;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module
 * Created by johntaylor on 3/26/16.
 */
@Module
public class ApplicationModule {
  private Application app;

  public ApplicationModule(Application app) {
    this.app = app;
  }

  @Provides
  @Singleton
  SharedPreferences provideSharedPrefs() {
    return PreferenceManager.getDefaultSharedPreferences(app);
  }
}
