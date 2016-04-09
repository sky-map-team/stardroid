package com.google.android.stardroid;

import android.content.SharedPreferences;

import com.google.android.stardroid.activities.EditSettingsActivity;
import com.google.android.stardroid.activities.ImageDisplayActivity;
import com.google.android.stardroid.activities.ImageGalleryActivity;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Dagger component.
 * Created by johntaylor on 3/26/16.
 */
@Singleton
@Component(modules={ApplicationModule.class})
public interface ApplicationComponent {
  // What we expose to dependent components
  StardroidApplication provideStardroidApplication();
  SharedPreferences provideSharedPreferences();

  // Who can we inject
  void inject(StardroidApplication app);
  void inject(EditSettingsActivity activity);
  void inject(ImageDisplayActivity activity);
  void inject(ImageGalleryActivity activity);
}
