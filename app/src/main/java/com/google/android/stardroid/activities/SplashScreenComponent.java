package com.google.android.stardroid.activities;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 4/2/16.
 */
@PerActivity
@Component(modules = SplashScreenModule.class, dependencies = ApplicationComponent.class)
public interface SplashScreenComponent extends EulaDialogFragment.ActivityComponent,
    WhatsNewDialogFragment.ActivityComponent {
  void inject(SplashScreenActivity activity);
}
