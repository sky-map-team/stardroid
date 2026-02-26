package com.google.android.stardroid.activities;

import android.content.Context;
import android.view.Window;

import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.inject.PerActivity;

import javax.annotation.Nullable;

import dagger.Module;
import dagger.Provides;

/**
 * Created by johntaylor on 4/24/16.
 */
@Module
public class CompassCalibrationModule {
  private CompassCalibrationActivity activity;
  public CompassCalibrationModule(CompassCalibrationActivity activity) {
    this.activity = activity;
  }

  @Provides
  @PerActivity
  Context provideContext() {
    return activity;
  }

  @Provides
  @PerActivity
  Window provideWindow() {
    return activity.getWindow();
  }

  @Provides
  @PerActivity
  @Nullable
  ActivityLightLevelChanger.NightModeable provideNightModeable() {
    return activity;
  }
}
