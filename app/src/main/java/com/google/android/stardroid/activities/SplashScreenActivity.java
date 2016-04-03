// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Shows a splash screen, then launch the next activity.
 */
public class SplashScreenActivity extends Activity
    implements EulaDialogFragment.EulaAcceptanceListener {
  private final static String TAG = MiscUtil.getTag(SplashScreenActivity.class);

  @Inject Analytics analytics;
  @Inject SharedPreferences sharedPreferences;
  @Inject Animation fadeAnimation;
  @Inject EulaDialogFragment eulaDialogFragmentWithButtons;
  @Inject FragmentManager fragmentManager;
  private View graphic;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "SplashScreen onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.splash);
    ((StardroidApplication) getApplication()).getApplicationComponent().newSplashScreenSubcomponent(
        new SplashScreenModule(this)).inject(this);

    graphic = findViewById(R.id.splash);

    fadeAnimation.setAnimationListener(new AnimationListener() {
      public void onAnimationEnd(Animation arg0) {
        Log.d(TAG, "SplashScreen.Animation onAnimationEnd");
        graphic.setVisibility(View.INVISIBLE);
        Intent intent = new Intent(SplashScreenActivity.this, DynamicStarMapActivity.class);
        startActivity(intent);
        finish();
      }

      public void onAnimationRepeat(Animation arg0) {
      }

      public void onAnimationStart(Animation arg0) {
        Log.d(TAG, "SplashScreen.Animcation onAnimationStart");
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    boolean eulaShown = maybeShowEula();
    if (!eulaShown) {
      // User has previously accepted - let's get on with it!
      graphic.startAnimation(fadeAnimation);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    analytics.trackPageView(Analytics.SPLASH_SCREEN_ACTIVITY);
  }

  @Override
  public void onPause() {
    Log.d(TAG, "SplashScreen onPause");
    super.onPause();
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "SplashScreen onDestroy");
    super.onDestroy();
  }

  private boolean maybeShowEula() {
    int versionCode = ((StardroidApplication) getApplication()).getVersion();
    boolean eulaAlreadyConfirmed = (sharedPreferences.getInt(
        ApplicationConstants.READ_TOS_PREF_VERSION, -1) == versionCode);
    if (!eulaAlreadyConfirmed) {
      eulaDialogFragmentWithButtons.show(fragmentManager, "Eula Dialog");
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void eulaAccepted() {
    int versionCode = ((StardroidApplication) getApplication()).getVersion();
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putInt(ApplicationConstants.READ_TOS_PREF_VERSION, versionCode);
    editor.commit();
    // Let's go.
    View graphic = findViewById(R.id.splash);
    graphic.startAnimation(fadeAnimation);
  }

  @Override
  public void eulaRejected() {
    Log.d(TAG, "Sorry chum, no accept, no app.");
    finish();
  }
}
