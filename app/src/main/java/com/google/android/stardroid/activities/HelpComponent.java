package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 2/17/22.
 */
@PerActivity
@Component(modules = HelpModule.class, dependencies = ApplicationComponent.class)
public interface HelpComponent {
  void inject(HelpActivity activity);
}

