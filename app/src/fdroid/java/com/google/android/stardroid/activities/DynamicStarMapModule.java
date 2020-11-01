package com.google.android.stardroid.activities;

import dagger.Module;

/**
 * Dagger module
 * Created by johntaylor on 3/29/16.
 */
@Module
public class DynamicStarMapModule extends AbstractDynamicStarMapModule {
  public DynamicStarMapModule(DynamicStarMapActivity activity) {
    super(activity);
  }
}
