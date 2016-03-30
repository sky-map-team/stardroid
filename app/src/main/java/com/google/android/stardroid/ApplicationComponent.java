package com.google.android.stardroid;

import com.google.android.stardroid.activities.DiagnosticActivity;
import com.google.android.stardroid.activities.DynamicStarMapModule;
import com.google.android.stardroid.activities.DynamicStarMapSubcomponent;
import com.google.android.stardroid.activities.EditSettingsActivity;
import com.google.android.stardroid.activities.ImageDisplayActivity;
import com.google.android.stardroid.activities.ImageGalleryActivity;
import com.google.android.stardroid.activities.SplashScreenActivity;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Dagger component.
 * Created by johntaylor on 3/26/16.
 */
@Singleton
@Component(modules={ApplicationModule.class})
public interface ApplicationComponent {
  void inject(StardroidApplication app);

  void inject(DiagnosticActivity activity);
  void inject(EditSettingsActivity activity);
  void inject(ImageDisplayActivity activity);
  void inject(ImageGalleryActivity activity);
  void inject(SplashScreenActivity activity);

  DynamicStarMapSubcomponent newDynamicStarMapSubcomponent(DynamicStarMapModule activityModule);
}
