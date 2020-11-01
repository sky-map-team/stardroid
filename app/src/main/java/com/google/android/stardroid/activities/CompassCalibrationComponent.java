package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 4/24/16.
 */
@PerActivity
@Component(modules = CompassCalibrationModule.class, dependencies = ApplicationComponent.class)
public interface CompassCalibrationComponent {
  void inject(CompassCalibrationActivity activity);
}

