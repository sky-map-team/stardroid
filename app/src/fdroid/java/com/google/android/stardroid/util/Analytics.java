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

import com.google.android.stardroid.StardroidApplication;

import javax.inject.Inject;

/**
 * Encapsulates interactions with Google Analytics, allowing it to be
 * disabled etc.
 *
 * @author John Taylor
 */
public class Analytics implements AnalyticsInterface {

  @Inject
  Analytics(StardroidApplication application) { }

  public void setEnabled(boolean enabled) { }

  /**
   * Tracks a screen view.
   */
  public void trackPageView(String page) { }

  /**
   * Tracks and event.
   *
   * @see com.google.android.gms.analytics.HitBuilders.EventBuilder
   */
  public void trackEvent(String category, String action, String label, long value) { }

  /**
   * Sets custom variables for slicing.
   */
  public void setCustomVar(Slice slice, String value) { }
}
