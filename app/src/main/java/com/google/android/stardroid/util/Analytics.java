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

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.content.Context;
import android.util.Log;

/**
 * Encapsulates interactions with Google Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
// TODO(johntaylor): Can we avoid this being a global singleton?
public class Analytics {
  /**
   * Analytics ID associated with http://stardroid-server.appspot.com
   */
  private static final String WEB_PROPERTY_ID = "TODO";
  private static final int DISPATCH_INTERVAL_SECS = 10;
  public static final String PREF_KEY = "enable_analytics";
  private static volatile Analytics analytics;

  private volatile boolean isEnabled = true;
  private boolean isRunning;
  private Context context;
  private static final String TAG = MiscUtil.getTag(Analytics.class);

  /**
   * Custom vars (for slicing and dicing)
   * At most 5 can be defined.
   */
  public static enum Slice {
    ANDROID_OS, SKYMAP_VERSION, DEVICE_NAME;
  }

  // Page Views
  public static final String APPLICATION_CREATE = "/ApplicationCreate";
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
  public static final String TOGGLED_TIME_TRAVEL_MODE_LABEL = "Toggled Time Travel Mode";
  public static final String MENU_ITEM = "Pressed Menu Item";
  public static final String TOGGLED_NIGHT_MODE_LABEL = "Toggled Night Mode";
  public static final String SEARCH_REQUESTED_LABEL = "Search Requested";
  public static final String SETTINGS_OPENED_LABEL = "Settings Opened";
  public static final String HELP_OPENED_LABEL = "Help Opened";
  public static final String TIME_TRAVEL_OPENED_LABEL = "Time Travel Opened";
  public static final String GALLERY_OPENED_LABEL = "Gallery Opened";
  public static final String TOS_OPENED_LABEL = "TOS Opened";
  public static final String SEARCH = "Search";
  public static final String GENERAL_CATEGORY = "General";
  public static final String START_HOUR = "Start up hour";
  public static final String SESSION_LENGTH_BUCKET = "Session length bucket";
  // TODO(johntaylor): use CustomVariable.VISITOR_SCOPE if it gets made public.
  private static final int VISITOR_SCOPE = 1;

  /**
   * Returns a shared Analytics object.  If one doesn't exist, then it's
   * created and linked to the supplied context.  You must call setEnabled(true)
   * to start data collection.
   */
  public static Analytics getInstance(Context context) {
    if (analytics == null) {
      analytics = new Analytics(context);
    }
    return analytics;
  }

  private Analytics(Context context) {
    this.context = context;
  }

  public void setEnabled(boolean enabled) {
    this.isEnabled = enabled;
    Log.d(TAG, isEnabled ? "Enabling stats collection" : "Disabling stats collection");
    if (isEnabled && !isRunning) {
      Log.d(TAG, "Enabling analytics");
      GoogleAnalyticsTracker.getInstance().start(
          WEB_PROPERTY_ID, DISPATCH_INTERVAL_SECS, context);
      isRunning = true;
    } else if (!isEnabled && isRunning){
      Log.d(TAG, "Disabling analytics");
      // TODO(johntaylor): find out if we really need to dispatch first
      GoogleAnalyticsTracker.getInstance().dispatch();
      GoogleAnalyticsTracker.getInstance().stop();
      isRunning = false;
    }
  }

  /**
   * Sets the version of Sky Map.  This must be called before the first call to setEnabled();
   */
  public void setProductVersion(String versionString) {
    GoogleAnalyticsTracker.getInstance().setProductVersion("Google Sky Map", versionString);
  }

  /**
   * Tracks a webpage visit - see {@link GoogleAnalyticsTracker#trackPageView(String)}.
   */
  public void trackPageView(String page) {
    if (isEnabled) {
      Log.d(TAG, "Logging page " + page);
      GoogleAnalyticsTracker.getInstance().trackPageView(page);
    }
  }

  /**
   * Tracks events - see {@link GoogleAnalyticsTracker#trackEvent(String, String, String, int)}.
   */
  public void trackEvent(String category, String action, String label, int value) {
    if (isEnabled) {
      Log.d(TAG, String.format("Logging event %s (%s) label %s value %d",
          action, category, label, value));
      GoogleAnalyticsTracker.getInstance().trackEvent(category, action, label, value);
    }
  }

  /**
   * Sets customer variables - see {@link GoogleAnalyticsTracker#setCustomVar(int, String, String)}.
   */
  public void setCustomVar(Slice slice, String value) {
    Log.d(TAG, String.format("Setting custom variable %s to %s", slice.toString(), value));
    GoogleAnalyticsTracker.getInstance().setCustomVar(
        slice.ordinal() + 1, slice.toString(), value, VISITOR_SCOPE);
  }
}
