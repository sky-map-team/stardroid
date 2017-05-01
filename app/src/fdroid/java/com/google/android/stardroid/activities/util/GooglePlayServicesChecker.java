package com.google.android.stardroid.activities.util;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleFragment;
import com.google.android.stardroid.control.LocationController;

import javax.inject.Inject;

/**
 * Created by johntaylor on 4/2/16.
 */
public class GooglePlayServicesChecker extends AbstractGooglePlayServicesChecker {

  @Inject
  GooglePlayServicesChecker(Activity parent, SharedPreferences preferences,
                            LocationPermissionRationaleFragment rationaleDialog,
                            FragmentManager fragmentManager) {
    super(parent, preferences, rationaleDialog, fragmentManager);
  }

  /**
   * Checks whether play services is available and up to date and prompts the user
   * if necessary.
   * <p/>
   * Note that at present we only need it for location services so if the user is setting
   * their location manually we don't do the check.
   */
  public void maybeCheckForGooglePlayServices() {
    Log.d(TAG, "Google Play Services check");
    if (preferences.getBoolean(LocationController.NO_AUTO_LOCATE, false)) {
      Log.d(TAG, "Auto location disabled - not checking for GMS");
      return;
    }
    super.checkLocationServicesEnabled();
  }
}
