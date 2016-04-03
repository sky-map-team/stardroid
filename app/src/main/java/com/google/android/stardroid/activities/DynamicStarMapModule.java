package com.google.android.stardroid.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module
 * Created by johntaylor on 3/29/16.
 */
@Module
public class DynamicStarMapModule {
  private static final String TAG = MiscUtil.getTag(DynamicStarMapModule.class);
  private DynamicStarMapActivity activity;

  public DynamicStarMapModule(DynamicStarMapActivity activity) {
    Log.d(TAG, "Creating activity module for " + activity);
    this.activity = activity;
  }

  @Provides
  @Singleton
  DynamicStarMapActivity provideDynamicStarMapActivity() {
    return activity;
  }

  @Provides
  @Singleton
  Activity provideActivity() {
    return activity;
  }

  @Provides
  @Singleton
  Context provideActivityContext() {
    return activity;
  }

  @Provides
  @Singleton
  @Named("timetravel")
  MediaPlayer provideTimeTravelNoise() {
    return MediaPlayer.create(activity, R.raw.timetravel);
  }

  @Provides
  @Singleton
  @Named("timetravelback")
  MediaPlayer provideTimeTravelBackNoise() {
    return MediaPlayer.create(activity, R.raw.timetravelback);
  }

  @Provides
  @Singleton
  Animation provideTimeTravelFlashAnimation() {
    return AnimationUtils.loadAnimation(activity, R.anim.timetravelflash);
  }

  @Provides
  @Singleton
  Handler provideHandler() {
    return new Handler();
  }

  @Provides
  FragmentManager provideFragmentManager() {
    return activity.getFragmentManager();
  }

  @Provides
  EulaDialogFragment provideEulaFragmentWithoutButtons(Analytics analytics) {
    return new EulaDialogFragment(activity, false, analytics, null);
  }
}
