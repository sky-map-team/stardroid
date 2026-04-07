package com.google.android.stardroid.activities;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;

/**
 * Created by johntaylor on 4/24/16.
 */
@Module
@InstallIn(ActivityComponent.class)
public class CompassCalibrationModule {
  // Window and NightModeable are provided by ActivityBindingsModule.
}
