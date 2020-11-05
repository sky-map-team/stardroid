package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 4/15/16.
 */
@PerActivity
@Component(modules = DiagnosticActivityModule.class, dependencies = ApplicationComponent.class)
public interface DiagnosticActivityComponent {
  void inject(DiagnosticActivity activity);
}
