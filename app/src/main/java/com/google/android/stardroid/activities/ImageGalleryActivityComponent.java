package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

@PerActivity
@Component(modules = ImageGalleryActivityModule.class, dependencies = ApplicationComponent.class)
public interface ImageGalleryActivityComponent {
  void inject(ImageGalleryActivity activity);
}
