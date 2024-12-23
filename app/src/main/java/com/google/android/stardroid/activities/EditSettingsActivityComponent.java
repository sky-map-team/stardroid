package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 11/12/24.
 */
@PerActivity
@Component(modules = EditSettingsActivityModule.class, dependencies = ApplicationComponent.class)
public interface EditSettingsActivityComponent {
  void inject(EditSettingsActivity activity);
}
