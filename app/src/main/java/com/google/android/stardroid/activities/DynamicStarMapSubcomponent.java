package com.google.android.stardroid.activities;

import javax.inject.Singleton;

import dagger.Subcomponent;

/**
 * Created by johntaylor on 3/29/16.
 */
@Singleton
@Subcomponent(modules={DynamicStarMapModule.class})
public interface DynamicStarMapSubcomponent {
  void inject(DynamicStarMapActivity activity);
}

