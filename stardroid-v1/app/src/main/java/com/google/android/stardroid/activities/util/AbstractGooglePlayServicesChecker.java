package com.google.android.stardroid.activities.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

/**
 * Created by johntaylor on 4/2/16.
 */
public abstract class AbstractGooglePlayServicesChecker {
  protected static final String TAG = MiscUtil.getTag(GooglePlayServicesChecker.class);
  protected final Activity parent;
  protected final SharedPreferences preferences;

  AbstractGooglePlayServicesChecker(Activity parent, SharedPreferences preferences) {
    this.parent = parent;
    this.preferences = preferences;
  }

  public abstract void maybeCheckForGooglePlayServices();

  public void runAfterDialog() {
    Log.d(TAG, "Play Services Dialog has been shown");
  }
}
