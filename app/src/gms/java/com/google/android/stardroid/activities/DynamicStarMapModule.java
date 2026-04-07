package com.google.android.stardroid.activities;

import com.google.android.gms.common.GoogleApiAvailability;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.scopes.ActivityScoped;

/**
 * Dagger module
 * Created by johntaylor on 3/29/16.
 */
@Module
@InstallIn(ActivityComponent.class)
public class DynamicStarMapModule {

  @Provides
  @ActivityScoped
  GoogleApiAvailability providePlayServicesApiAvailability() {
    return GoogleApiAvailability.getInstance();
  }
}
