// Copyright 2010 Google Inc.
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

package com.google.android.stardroid.util;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.stardroid.BuildConfig;
import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;
import com.google.firebase.analytics.FirebaseAnalytics;

import javax.inject.Inject;

/**
 * Encapsulates interactions with Google Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
public class Analytics implements AnalyticsInterface {
  /**
   * Analytics ID associated with http://stardroid-server.appspot.com
   */
  private final HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder();
  private FirebaseAnalytics firebaseAnalytics;
  private static final String TAG = MiscUtil.getTag(Analytics.class);

  @Inject
  Analytics(StardroidApplication application) {
    firebaseAnalytics = FirebaseAnalytics.getInstance(application);
    Task<String> appId = firebaseAnalytics.getAppInstanceId();
    appId.addOnCompleteListener(task -> Log.d(TAG, "Firebase ID " + task.getResult()));
  }

  @Override
  public void setEnabled(boolean enabled) {
    Log.d(TAG, enabled ? "Enabling stats collection" : "Disabling stats collection");
    firebaseAnalytics.setAnalyticsCollectionEnabled(enabled);
  }

  @Override
  public void trackEvent(String event, Bundle params) {
    Log.d(TAG, String.format("Logging event %s, %s", event, params));
    firebaseAnalytics.logEvent(event, params);
  }

  @Override
  public void setUserProperty(String propertyName, String propertyValue) {
    Log.d(TAG, String.format("Logging user property %s, %s", propertyName, propertyValue));
    firebaseAnalytics.setUserProperty(propertyName, propertyValue);
  }
}
