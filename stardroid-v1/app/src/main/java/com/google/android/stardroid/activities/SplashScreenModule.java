package com.google.android.stardroid.activities;

import android.app.Activity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.stardroid.R;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.scopes.ActivityScoped;

/**
 * Created by johntaylor on 4/2/16.
 */
@Module
@InstallIn(ActivityComponent.class)
public class SplashScreenModule {

  @Provides
  @ActivityScoped
  @Named("fadeout")
  Animation provideFadeoutAnimation(Activity activity) {
    return AnimationUtils.loadAnimation(activity, R.anim.fadeout);
  }
}
