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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;

import androidx.fragment.app.FragmentManager;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Shows a splash screen, then launches the next activity.
 */
@AndroidEntryPoint
public class SplashScreenActivity extends androidx.fragment.app.FragmentActivity
    implements EulaDialogFragment.EulaAcceptanceListener, WhatsNewDialogFragment.CloseListener {
  private final static String TAG = MiscUtil.getTag(SplashScreenActivity.class);

  @Inject StartupRouter startupRouter;
  @Inject @Named("fadeout") Animation fadeAnimation;
  @Inject FragmentManager fragmentManager;
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
        proceedToNextActivity();
      }

      public void onAnimationRepeat(Animation arg0) {
      }

      public void onAnimationStart(Animation arg0) {
        Log.d(TAG, "SplashScreen.Animation onAnimationStart");
      }
    });
    if (!startupRouter.needsWhatsNew()) {
      // Shorten the animation for returning users who have already seen this version.
      fadeAnimation.setDuration(1000);
    }
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume");
    super.onResume();
    boolean eulaShowing = maybeShowEula();
    Log.d(TAG, "Eula showing " + eulaShowing);
    if (!eulaShowing) {
      Log.d(TAG, "EULA already accepted");
      startSplashScreen();
    }
  }

  private void startSplashScreen() {
    graphic.startAnimation(fadeAnimation);
  }

  private boolean maybeShowEula() {
    if (startupRouter.needsEula()) {
      showDialog(EulaDialogFragment.newInstance(), EulaDialogFragment.class.getSimpleName());
      return true;
    }
    return false;
  }

  @Override
  public void eulaAccepted() {
    startupRouter.markEulaAccepted();
    startSplashScreen();
  }

  @Override
  public void eulaRejected() {
    Log.d(TAG, "Sorry chum, no accept, no app.");
    finish();
  }

  private void proceedToNextActivity() {
    if (startupRouter.needsWarmWelcome()) {
      startActivity(new Intent(this, WarmWelcomeActivity.class));
      finish();
    } else if (startupRouter.needsWhatsNew()) {
      showDialog(WhatsNewDialogFragment.newInstance(), WhatsNewDialogFragment.class.getSimpleName());
    } else {
      startActivity(new Intent(this, DynamicStarMapActivity.class));
      finish();
    }
  }

  @Override
  public void dialogClosed() {
    startupRouter.markWhatsNewSeen();
    startActivity(new Intent(this, DynamicStarMapActivity.class));
    finish();
  }

  private void showDialog(androidx.fragment.app.DialogFragment fragment, String tag) {
    if (fragmentManager.findFragmentByTag(tag) == null) {
      fragment.show(fragmentManager, tag);
    }
  }
}
