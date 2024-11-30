package com.google.android.stardroid.util;

import android.os.Bundle;

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
  Analytics(StardroidApplication application) {
  }

  @Override
  public void setEnabled(boolean enabled) {
  }

  @Override
  public void trackEvent(String event, Bundle params) {
  }

  @Override
  public void setUserProperty(String propertyName, String propertyValue) {

  }
}
