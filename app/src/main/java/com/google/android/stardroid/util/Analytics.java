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

import android.hardware.Sensor;
import android.os.Build;
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
public class Analytics {
  /**
   * Analytics ID associated with http://stardroid-server.appspot.com
   */
  private static final String WEB_PROPERTY_ID = BuildConfig.GOOGLE_ANALYTICS_CODE;
  private static final int DISPATCH_INTERVAL_SECS = 10;
  public static final String PREF_KEY = "enable_analytics";
  private static volatile Analytics instance;
  private final HitBuilders.ScreenViewBuilder screenViewBuilder = new HitBuilders.ScreenViewBuilder();
  private final HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder();
  private GoogleAnalytics googleAnalytics;
  private Tracker tracker;
  private static final String TAG = MiscUtil.getTag(Analytics.class);

  /**
   * Returns either the name of the sensor or a string version of the sensor type id, depending
   * on the supported OS level along with some context.
   */
  public static String getSafeNameForSensor(Sensor sensor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
      return "Sensor type: " + sensor.getStringType() + ": " + sensor.getType();
    } else {
      return "Sensor type: " + sensor.getType();
    }
  }

  /**
   * Custom vars (for slicing and dicing)
   * At most 5 can be defined.
   */
  public static enum Slice {
    ANDROID_OS, SKYMAP_VERSION, DEVICE_NAME, NEW_USER;
  }

  // Page Views
  public static final String APPLICATION_CREATE = "/ApplicationCreate";
  public static final String COMPASS_CALIBRATION_ACTIVITY = "/MainPage/Calibration";
  public static final String DIAGNOSTICS_ACTIVITY = "/MainPage/Diagnostics";
  public static final String DYNAMIC_STARMAP_ACTIVITY = "/MainPage";
  public static final String EDIT_SETTINGS_ACTIVITY = "/MainPage/EditSettings";
  public static final String SPLASH_SCREEN_ACTIVITY = "/SplashScreen";
  public static final String IMAGE_GALLERY_ACTIVITY = "/MainPage/ImageGallery";
  public static final String IMAGE_DISPLAY_ACTIVITY = "/MainPage/ImageGallery/ImageDisplay";

  // Events & Categories
  public static final String TOS_ACCEPT = "Terms Of Service";
  public static final String APP_CATEGORY = "Application";
  public static final String TOS_ACCEPTED = "TOS Accepted";
  public static final String TOS_REJECTED = "TOS Rejected";
  public static final String INSTALL_CATEGORY = "Installation";
  public static final String INSTALL_EVENT = "Installed Version: ";
  public static final String PREVIOUS_VERSION = "Prevous Version: ";
  public static final String PREFERENCE_TOGGLE = "Preference toggled";
  public static final String PREFERENCE_BUTTON_TOGGLE = "Preference button toggled";
  public static final String USER_ACTION_CATEGORY = "User Action";
  public static final String TOGGLED_MANUAL_MODE_LABEL = "Toggled Manual Mode";
  public static final String MENU_ITEM = "Pressed Menu Item";
  public static final String TOGGLED_NIGHT_MODE_LABEL = "Toggled Night Mode";
  public static final String SEARCH_REQUESTED_LABEL = "Search Requested";
  public static final String SETTINGS_OPENED_LABEL = "Settings Opened";
  public static final String HELP_OPENED_LABEL = "Help Opened";
  public static final String CALIBRATION_OPENED_LABEL = "Calibration Opened";
  public static final String TIME_TRAVEL_OPENED_LABEL = "Time Travel Opened";
  public static final String GALLERY_OPENED_LABEL = "Gallery Opened";
  public static final String TOS_OPENED_LABEL = "TOS Opened";
  public static final String DIAGNOSTICS_OPENED_LABEL = "Diagnostics Opened";
  public static final String SEARCH = "Search";
  public static final String GENERAL_CATEGORY = "General";
  public static final String START_HOUR = "Start up hour";

  public static final String SENSOR_CATEGORY = "Sensors";
  public static final String SESSION_LENGTH_BUCKET = "Session length bucket";
  public static final String SENSOR_AVAILABILITY = "Minimal Sensor Availability";
  public static final String ROT_SENSOR_AVAILABILITY = "Rotation Sensor Availability";
  public static final String SENSOR_TYPE = "Sensor Type - ";
  public static final String SENSOR_NAME = "Sensor Name";
  public static final String HIGH_SENSOR_ACCURACY_ACHIEVED = "High Accuracy Achieved";
  public static final String SENSOR_ACCURACY_CHANGED = "Sensor Accuracy Changed";
  // Phone claims to have a sensor, but then doesn't allow registration of a listener.
  public static final String SENSOR_LIAR = "Sensor Liar!";

  @Inject
  Analytics(StardroidApplication application) {
    googleAnalytics = GoogleAnalytics.getInstance(application);
    // Can also use R.xml.global_tracker if we're prepared to reveal our analytics Id.
    tracker = googleAnalytics.newTracker(BuildConfig.GOOGLE_ANALYTICS_CODE);
    tracker.setAppVersion(application.getVersionName());
    tracker.setAppId("com.google.android.stardroid");
    tracker.setAppName(application.getString(R.string.app_name));
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
