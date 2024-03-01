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

import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.StardroidApplication;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;
import com.google.android.stardroid.activities.util.ConstraintsChecker;
import com.google.android.stardroid.databinding.SplashBinding;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * Shows a splash screen, then launch the next activity.
 */
// TODO(johntaylor): Probably get rid of this altogether
public class SplashScreenActivity extends AppCompatInjectableActivity
    implements EulaDialogFragment.EulaAcceptanceListener, WhatsNewDialogFragment.CloseListener,
    HasComponent<SplashScreenComponent> {
  private final static String TAG = MiscUtil.getTag(SplashScreenActivity.class);

  @Inject StardroidApplication app;
  @Inject Analytics analytics;
  @Inject SharedPreferences sharedPreferences;
  @Inject Animation fadeAnimation;
  @Inject EulaDialogFragment eulaDialogFragmentWithButtons;
  @Inject FragmentManager fragmentManager;
  @Inject WhatsNewDialogFragment whatsNewDialogFragment;
  @Inject ConstraintsChecker cc;
  private View graphic;
  private SplashScreenComponent daggerComponent;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate");
    // Despite all this, the splash screen still does not render top to bottom. With the right
    // lucky swipes on the splash screen it can be provoked into resizing and laying out properly.
    // There is official documentation at https://developer.android.com/training/gestures/edge-to-edge
    // but as is typical, it's inadequate.  Not worth further investment since the Splash Screen
    // should go away anyway.
    hideSystemBars();
    super.onCreate(savedInstanceState);

    daggerComponent = DaggerSplashScreenComponent.builder()
        .applicationComponent(getApplicationComponent())
        .splashScreenModule(new SplashScreenModule(this)).build();
    daggerComponent.inject(this);

    SplashBinding binding = SplashBinding.inflate(this.getLayoutInflater());
    setContentView(binding.getRoot());
    graphic = binding.splash;

    fadeAnimation.setAnimationListener(new AnimationListener() {
      public void onAnimationEnd(Animation unused) {
        Log.d(TAG, "onAnimationEnd");
        graphic.setVisibility(View.INVISIBLE);
        maybeShowWhatsNewAndEnd();
      }

      public void onAnimationRepeat(Animation arg0) {
      }

      public void onAnimationStart(Animation arg0) {
        Log.d(TAG, "SplashScreen.Animation onAnimationStart");
      }
    });
  }

  private void hideSystemBars() {
    WindowInsetsControllerCompat windowInsetsController =
        ViewCompat.getWindowInsetsController(getWindow().getDecorView());
    if (windowInsetsController == null) {
      Log.e(TAG, "WindowInsetsControllerCompat was null");
      return;
    }
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

    // Configure the behavior of the hidden system bars
    windowInsetsController.setSystemBarsBehavior(
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
    );
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume");
    super.onResume();
    boolean eulaShowing = maybeShowEula();
    Log.d(TAG, "Eula showing " + eulaShowing);
    if (!eulaShowing) {
      // User has previously accepted - let's get on with it!
      Log.d(TAG, "EULA already accepted");
      graphic.startAnimation(fadeAnimation);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
  }

  @Override
  public void onPause() {
    Log.d(TAG, "onPause");
    super.onPause();
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "onDestroy");
    super.onDestroy();
  }

  private boolean maybeShowEula() {
    boolean eulaAlreadyConfirmed = (sharedPreferences.getInt(
        ApplicationConstants.READ_TOS_PREF_VERSION, -1) == EULA_VERSION_CODE);
    if (!eulaAlreadyConfirmed) {
      eulaDialogFragmentWithButtons.show(fragmentManager, "Eula Dialog");
      return true;
    } else {
      return false;
    }
  }

  // Update this with new versions of the EULA
  private static final int EULA_VERSION_CODE = 1;

  @Override
  public void eulaAccepted() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putInt(ApplicationConstants.READ_TOS_PREF_VERSION, EULA_VERSION_CODE);
    editor.apply();
    // Let's go.
    graphic.startAnimation(fadeAnimation);
  }

  @Override
  public void eulaRejected() {
    Log.d(TAG, "Sorry chum, no accept, no app.");
    finish();
  }

  private void maybeShowWhatsNewAndEnd() {
    boolean whatsNewSeen = (sharedPreferences.getLong(
        ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, -1) == app.getVersion());
    if (whatsNewSeen) {
      launchSkyMap();
    } else {
      whatsNewDialogFragment.show(fragmentManager, "Whats New Dialog");
    }
  }

  // What's new dialog closed.
  @Override
  public void dialogClosed() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putLong(ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, app.getVersion());
    editor.apply();
    launchSkyMap();
  }

  private void launchSkyMap() {
    Intent intent = new Intent(SplashScreenActivity.this, DynamicStarMapActivity.class);
    cc.check();
    startActivity(intent);
    finish();
  }

  @Override
  public SplashScreenComponent getComponent() {
    return daggerComponent;
  }
}
