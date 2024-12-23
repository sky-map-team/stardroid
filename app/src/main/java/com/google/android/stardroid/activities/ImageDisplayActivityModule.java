package com.google.android.stardroid.activities;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.view.Window;

import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Module;
import dagger.Provides;

@Module
public class ImageDisplayActivityModule {
  private final ImageDisplayActivity activity;

  public ImageDisplayActivityModule(ImageDisplayActivity activity) {
    this.activity = activity;
  }

  @Provides
  @PerActivity
  Activity provideActivity() {
    return activity;
  }

  @Provides
  @PerActivity
  Context provideActivityContext() {
    return activity;
  }

  @Provides
  @PerActivity
  Handler provideHandler() {
    return new Handler();
  }

  @Provides
  @PerActivity
  Window provideWindow() {
    return activity.getWindow();
  }

  @Provides
  @PerActivity
  ActivityLightLevelChanger.NightModeable provideNightModeable() {
    return null;
  }
}

