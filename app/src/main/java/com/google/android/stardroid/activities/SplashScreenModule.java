package com.google.android.stardroid.activities;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.stardroid.R;

import javax.inject.Singleton;

import dagger.Provides;

/**
 * Created by johntaylor on 4/2/16.
 */
public class SplashScreenModule {
  private SplashScreenActivity activity;

  public SplashScreenModule(SplashScreenActivity activity) {
    this.activity = activity;
  }

  @Provides
  @Singleton
  Animation provideTimeTravelFlashAnimation() {
    return AnimationUtils.loadAnimation(activity, R.anim.fadeout);
  }
}
