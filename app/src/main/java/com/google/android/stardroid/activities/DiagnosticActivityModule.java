package com.google.android.stardroid.activities;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;

/**
 * Created by johntaylor on 4/15/16.
 */
@Module
@InstallIn(ActivityComponent.class)
public class DiagnosticActivityModule {
  // Window, NightModeable, Handler, and FragmentManager are provided by ActivityBindingsModule.
}
