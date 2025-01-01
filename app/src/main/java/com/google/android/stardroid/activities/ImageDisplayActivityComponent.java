package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

@PerActivity
@Component(modules = ImageDisplayActivityModule.class, dependencies = ApplicationComponent.class)
public interface ImageDisplayActivityComponent {
  void inject(ImageDisplayActivity activity);
}
