package com.google.android.stardroid.activities;

import javax.inject.Singleton;

import dagger.Subcomponent;

/**
 * Created by johntaylor on 4/2/16.
 */
@Singleton
@Subcomponent(modules={SplashScreenModule.class})
public interface SplashScreenSubcomponent {
  void inject(SplashScreenActivity activity);
}
