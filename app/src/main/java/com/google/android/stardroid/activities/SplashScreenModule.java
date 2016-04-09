package com.google.android.stardroid.activities;

import android.app.FragmentManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.scopes.PerActivity;
import com.google.android.stardroid.util.Analytics;

import dagger.Module;
import dagger.Provides;

/**
 * Created by johntaylor on 4/2/16.
 */
@PerActivity
@Module
public class SplashScreenModule {
  private SplashScreenActivity activity;

  public SplashScreenModule(SplashScreenActivity activity) {
    this.activity = activity;
  }

  @Provides
//  @Singleton
  Animation provideFadeoutAnimation() {
    return AnimationUtils.loadAnimation(activity, R.anim.fadeout);
  }

  @Provides
  EulaDialogFragment provideEulaFragmentWithButtons(Analytics analytics) {
    return new EulaDialogFragment(activity, true, analytics, activity);
  }

  @Provides
  FragmentManager provideFragmentManager() {
    return activity.getFragmentManager();
  }
}
