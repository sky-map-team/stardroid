package com.google.android.stardroid.activities;

import android.app.Activity;
import android.os.Handler;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.scopes.ActivityScoped;

/**
 * Provides bindings that are common across all activities.
 */
@Module
@InstallIn(ActivityComponent.class)
public class ActivityBindingsModule {

  @Provides
  @ActivityScoped
  Window provideWindow(Activity activity) {
    return activity.getWindow();
  }

  @Provides
  @ActivityScoped
  @Nullable
  ActivityLightLevelChanger.NightModeable provideNightModeable(Activity activity) {
    if (activity instanceof ActivityLightLevelChanger.NightModeable) {
      return (ActivityLightLevelChanger.NightModeable) activity;
    }
    return null;
  }

  @Provides
  @ActivityScoped
  Handler provideHandler() {
    return new Handler();
  }

  @Provides
  @ActivityScoped
  FragmentManager provideFragmentManager(Activity activity) {
    return ((FragmentActivity) activity).getSupportFragmentManager();
  }
}
