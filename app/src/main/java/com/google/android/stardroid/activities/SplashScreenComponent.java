package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.scopes.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 4/2/16.
 */
@PerActivity
@Component(modules={SplashScreenModule.class}, dependencies = {ApplicationComponent.class})
public interface SplashScreenComponent {
  void inject(SplashScreenActivity activity);
}
