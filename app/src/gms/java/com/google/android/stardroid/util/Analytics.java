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

import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.stardroid.BuildConfig;
import com.google.android.stardroid.R;
import com.google.android.stardroid.StardroidApplication;

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
  private static final String WEB_PROPERTY_ID = BuildConfig.GOOGLE_ANALYTICS_CODE;
  private static final int DISPATCH_INTERVAL_SECS = 10;
  private static volatile Analytics instance;
  private final HitBuilders.ScreenViewBuilder screenViewBuilder = new HitBuilders.ScreenViewBuilder();
  private final HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder();
  private GoogleAnalytics googleAnalytics;
  private Tracker tracker;
  private static final String TAG = MiscUtil.getTag(Analytics.class);

  @Inject
  Analytics(StardroidApplication application) {
    googleAnalytics = GoogleAnalytics.getInstance(application);
    // Can also use R.xml.global_tracker if we're prepared to reveal our analytics Id.
    tracker = googleAnalytics.newTracker(BuildConfig.GOOGLE_ANALYTICS_CODE);
    tracker.setAppVersion(application.getVersionName());
    tracker.setAppId("com.google.android.stardroid");
    tracker.setAppName(application.getString(R.string.app_name));
    // Sample only 0.01% of events in order to avoid violating Analytics' Terms of Service.
    // TODO(jontayler): move to Firebase
    tracker.setSampleRate(0.01);
  }

  public void setEnabled(boolean enabled) {
    Log.d(TAG, enabled ? "Enabling stats collection" : "Disabling stats collection");
    googleAnalytics.setAppOptOut(!enabled);
  }

  /**
   * Tracks a screen view.
   */
  public void trackPageView(String page) {
    Log.d(TAG, "Logging page " + page);
    tracker.setScreenName(page);
    tracker.send(screenViewBuilder.build());
  }

  /**
   * Tracks and event.
   *
   * @see com.google.android.gms.analytics.HitBuilders.EventBuilder
   */
  public void trackEvent(String category, String action, String label, long value) {
    Log.d(TAG, String.format("Logging event %s (%s) label %s value %d",
        action, category, label, value));
    tracker.send(eventBuilder.setCategory(category).setAction(action).setLabel(label)
        .setValue(value).build());
  }

  /**
   * Sets custom variables for slicing.
   */
  public void setCustomVar(Slice slice, String value) {
    Log.d(TAG, String.format("Setting custom variable %s to %s", slice.toString(), value));
    eventBuilder.setCustomDimension(slice.ordinal() + 1, value);
    screenViewBuilder.setCustomDimension(slice.ordinal() + 1, value);
  }
}
