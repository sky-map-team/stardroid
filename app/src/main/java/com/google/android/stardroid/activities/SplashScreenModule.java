package com.google.android.stardroid.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;
import com.google.android.stardroid.inject.PerActivity;

import dagger.Module;
import dagger.Provides;

/**
 * Created by johntaylor on 4/2/16.
 */
@Module
public class SplashScreenModule {
  private SplashScreenActivity activity;

  public SplashScreenModule(SplashScreenActivity activity) {
    this.activity = activity;
  }

  @Provides
  @PerActivity
  EulaDialogFragment provideEulaDialogFragment() {
    EulaDialogFragment fragment = new EulaDialogFragment();
    fragment.setEulaAcceptanceListener(activity);
    return fragment;
  }

  @Provides
  @PerActivity
  WhatsNewDialogFragment provideWhatsNewDialogFragment() {
    WhatsNewDialogFragment whatsNewDialogFragment = new WhatsNewDialogFragment();
    whatsNewDialogFragment.setCloseListener(activity);
    return whatsNewDialogFragment;
  }

  @Provides
  @PerActivity
  Activity provideActivity() {
    return activity;
  }

  @Provides
  @PerActivity
  Animation provideFadeoutAnimation() {
    return AnimationUtils.loadAnimation(activity, R.anim.fadeout);
  }

  @Provides
  @PerActivity
  FragmentManager provideFragmentManager() {
    return activity.getFragmentManager();
  }
}
