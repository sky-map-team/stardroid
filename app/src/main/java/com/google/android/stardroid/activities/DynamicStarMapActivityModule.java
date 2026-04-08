package com.google.android.stardroid.activities;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;

import androidx.annotation.Nullable;

import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.FullscreenControlsManager;
import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.touch.DragRotateZoomGestureDetector;
import com.google.android.stardroid.touch.GestureInterpreter;
import com.google.android.stardroid.touch.MapMover;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.views.ButtonLayerView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import dagger.Binds;
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
public abstract class DynamicStarMapActivityModule {
  private static final String TAG = MiscUtil.getTag(DynamicStarMapActivityModule.class);

  @Binds
  @ActivityScoped
  public abstract DragRotateZoomGestureDetector.DragRotateZoomGestureDetectorListener
      bindDragRotateZoomGestureDetectorListener(MapMover mapMover);

  @Provides
  @ActivityScoped
  public static GestureDetector provideGestureDetector(Activity activity, GestureInterpreter gestureInterpreter) {
    return new GestureDetector(activity, gestureInterpreter);
  }

  // NOT @ActivityScoped — released in onPause(), Provider.get() must return a fresh instance
  // each call. See AGENTS.md.
  @Provides
  @Named("timetravel")
  @Nullable
  public static MediaPlayer provideTimeTravelNoise(Activity activity) {
    return prepareMediaPlayerAsync(activity, R.raw.timetravel);
  }

  // NOT @ActivityScoped — released in onPause(), Provider.get() must return a fresh instance
  // each call. See AGENTS.md.
  @Provides
  @Named("timetravelback")
  @Nullable
  public static MediaPlayer provideTimeTravelBackNoise(Activity activity) {
    return prepareMediaPlayerAsync(activity, R.raw.timetravelback);
  }

  /**
   * Creates a MediaPlayer using prepareAsync() so the caller's thread (typically the main thread)
   * is never blocked. Returns null if the resource cannot be opened.
   */
  @Nullable
  private static MediaPlayer prepareMediaPlayerAsync(Activity activity, int rawResId) {
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
  @ActivityScoped
  @Named("timetravelflash")
  public static Animation provideTimeTravelFlashAnimation(Activity activity) {
    return AnimationUtils.loadAnimation(activity, R.anim.timetravelflash);
  }
}
