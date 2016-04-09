package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.scopes.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 3/29/16.
 */
@PerActivity
@Component(modules={DynamicStarMapModule.class}, dependencies={ApplicationComponent.class})
public interface DynamicStarMapComponent {
  void inject(DynamicStarMapActivity activity);
}

