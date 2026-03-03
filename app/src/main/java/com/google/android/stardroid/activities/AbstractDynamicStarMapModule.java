package com.google.android.stardroid.activities;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.util.Log;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.CreditsDialogFragment;
import com.google.android.stardroid.activities.dialogs.HelpDialogFragment;
import com.google.android.stardroid.activities.dialogs.LocationPermissionDeniedDialogFragment;
import com.google.android.stardroid.activities.dialogs.MultipleSearchResultsDialogFragment;
import com.google.android.stardroid.activities.dialogs.NoSearchResultsDialogFragment;
import com.google.android.stardroid.activities.dialogs.NoSensorsDialogFragment;
import com.google.android.stardroid.activities.dialogs.TimeTravelDialogFragment;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.inject.PerActivity;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module
 * Created by johntaylor on 3/29/16.
 */
@Module
public class AbstractDynamicStarMapModule {
  private static final String TAG = MiscUtil.getTag(DynamicStarMapModule.class);
  private final DynamicStarMapActivity activity;

  public AbstractDynamicStarMapModule(DynamicStarMapActivity activity) {
    Log.d(TAG, "Creating activity module for " + activity);
    this.activity = activity;
  }

  @Provides
  @PerActivity
  DynamicStarMapActivity provideDynamicStarMapActivity() {
    return activity;
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
  AssetManager provideAssetManager() {
    return activity.getAssets();
  }

  @Provides
  @PerActivity
  ActivityLightLevelChanger.NightModeable provideNightModeable() {
    return activity;
  }

  @Provides
  @PerActivity
  Window provideWindow() {
    return activity.getWindow();
  }

  @Provides
  @PerActivity
  EulaDialogFragment provideEulaDialogFragment() {
    return new EulaDialogFragment();
  }

  @Provides
  @PerActivity
  TimeTravelDialogFragment provideTimeTravelDialogFragment() {
    return new TimeTravelDialogFragment();
  }

  @Provides
  @PerActivity
  CreditsDialogFragment provideCreditsDialogFragment() {
    return new CreditsDialogFragment();
  }

  @Provides
  @PerActivity
  HelpDialogFragment provideHelpDialogFragment() {
    return new HelpDialogFragment();
  }

  @Provides
  @PerActivity
  NoSearchResultsDialogFragment provideNoSearchResultsDialogFragment() {
    return new NoSearchResultsDialogFragment();
  }

  @Provides
  @PerActivity
  MultipleSearchResultsDialogFragment provideMultipleSearchResultsDialogFragment() {
    return new MultipleSearchResultsDialogFragment();
  }

  @Provides
  @PerActivity
  NoSensorsDialogFragment provideNoSensorsDialogFragment() {
    return new NoSensorsDialogFragment();
  }

  // Not @PerActivity scoped: onPause() releases the player, so each resume needs a fresh instance.
  @Provides
  @Named("timetravel")
  @Nullable
  MediaPlayer provideTimeTravelNoise() {
    return prepareMediaPlayerAsync(R.raw.timetravel);
  }

  @Provides
  @Named("timetravelback")
  @Nullable
  MediaPlayer provideTimeTravelBackNoise() {
    return prepareMediaPlayerAsync(R.raw.timetravelback);
  }

  /**
   * Creates a MediaPlayer using prepareAsync() so the caller's thread (typically the main thread)
   * is never blocked. Returns null if the resource cannot be opened.
   */
  @Nullable
  private MediaPlayer prepareMediaPlayerAsync(int rawResId) {
    MediaPlayer mp = new MediaPlayer();
    try (AssetFileDescriptor afd = activity.getResources().openRawResourceFd(rawResId)) {
      mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      mp.prepareAsync();
      return mp;
    } catch (Exception e) {
      Log.e(TAG, "Could not initialize media player for resource " + rawResId, e);
      mp.release();
      return null;
    }
  }

  @Provides
  @PerActivity
  Animation provideTimeTravelFlashAnimation() {
    return AnimationUtils.loadAnimation(activity, R.anim.timetravelflash);
  }

  @Provides
  @PerActivity
  Handler provideHandler() {
    return new Handler();
  }

  @Provides
  @PerActivity
  FragmentManager provideFragmentManager() {
    return activity.getSupportFragmentManager();
  }

  @Provides
  @PerActivity
  LocationPermissionDeniedDialogFragment provideLocationPermissionDeniedFragment() {
    return new LocationPermissionDeniedDialogFragment();
  }
}
