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

import androidx.fragment.app.FragmentManager;
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
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;
import com.google.android.stardroid.activities.util.ConstraintsChecker;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Shows a splash screen, then launch the next activity.
 */
@AndroidEntryPoint
public class SplashScreenActivity extends androidx.fragment.app.FragmentActivity
    implements EulaDialogFragment.EulaAcceptanceListener, WhatsNewDialogFragment.CloseListener {
  private final static String TAG = MiscUtil.getTag(SplashScreenActivity.class);

  @Inject StardroidApplication app;
  @Inject Analytics analytics;
  @Inject SharedPreferences sharedPreferences;
  @Inject @Named("fadeout") Animation fadeAnimation;
  @Inject FragmentManager fragmentManager;
  @Inject ConstraintsChecker cc;
  private View graphic;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.splash);

    graphic = findViewById(R.id.splash);

    fadeAnimation.setAnimationListener(new AnimationListener() {
      public void onAnimationEnd(Animation unused) {
        Log.d(TAG, "onAnimationEnd");
        graphic.setVisibility(View.INVISIBLE);
        maybeShowWarmWelcomeAndEnd();
      }

      public void onAnimationRepeat(Animation arg0) {
      }

      public void onAnimationStart(Animation arg0) {
        Log.d(TAG, "SplashScreen.Animation onAnimationStart");
      }
    });
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
      showDialog(EulaDialogFragment.newInstance(), EulaDialogFragment.class.getSimpleName());
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
    editor.commit();
    // Let's go.
    graphic.startAnimation(fadeAnimation);
  }

  @Override
  public void eulaRejected() {
    Log.d(TAG, "Sorry chum, no accept, no app.");
    finish();
  }

  private void maybeShowWarmWelcomeAndEnd() {
    boolean warmWelcomeSeen = (sharedPreferences.getLong(
        ApplicationConstants.READ_WARM_WELCOME_PREF_VERSION, -1) > 0);
    if (warmWelcomeSeen) {
      maybeShowWhatsNewAndEnd();
    } else {
      Intent intent = new Intent(SplashScreenActivity.this, WarmWelcomeActivity.class);
      startActivity(intent);
      finish();
    }
  }

  private void maybeShowWhatsNewAndEnd() {
    boolean whatsNewSeen = (sharedPreferences.getLong(
        ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, -1) == app.getVersion());
    if (whatsNewSeen) {
      launchSkyMap();
    } else {
      showDialog(WhatsNewDialogFragment.newInstance(), WhatsNewDialogFragment.class.getSimpleName());
    }
  }

  // What's new dialog closed.
  @Override
  public void dialogClosed() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putLong(ApplicationConstants.READ_WHATS_NEW_PREF_VERSION, app.getVersion());
    editor.commit();
    launchSkyMap();
  }

  private void showDialog(androidx.fragment.app.DialogFragment fragment, String tag) {
    if (fragmentManager.findFragmentByTag(tag) == null) {
      fragment.show(fragmentManager, tag);
    }
  }

  private void launchSkyMap() {
    Intent intent = new Intent(SplashScreenActivity.this, DynamicStarMapActivity.class);
    cc.check();
    startActivity(intent);
    finish();
  }
}
