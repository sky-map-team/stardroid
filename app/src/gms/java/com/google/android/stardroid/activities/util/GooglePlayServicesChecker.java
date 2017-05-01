package com.google.android.stardroid.activities.util;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleFragment;
import com.google.android.stardroid.control.LocationController;

import javax.inject.Inject;

/**
 * Created by johntaylor on 4/2/16.
 */
public class GooglePlayServicesChecker extends AbstractGooglePlayServicesChecker {
  private final GoogleApiAvailability apiAvailability;

  @Inject
  GooglePlayServicesChecker(Activity parent, SharedPreferences preferences,
                            GoogleApiAvailability apiAvailability,
                            LocationPermissionRationaleFragment rationaleDialog,
                            FragmentManager fragmentManager) {
    super(parent, preferences, rationaleDialog, fragmentManager);
    this.apiAvailability = apiAvailability;
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
    int googlePlayServicesAvailability = apiAvailability.isGooglePlayServicesAvailable(parent);
    if (googlePlayServicesAvailability == ConnectionResult.SUCCESS) {
      Log.d(TAG, "Google Play Services is available and up to date");
    } else {
      Log.d(TAG, "Google Play Status availability: " + googlePlayServicesAvailability);
      if (apiAvailability.isUserResolvableError(googlePlayServicesAvailability)) {
        Log.d(TAG, "...but we can fix it");
        apiAvailability.getErrorDialog(parent, googlePlayServicesAvailability,
            DynamicStarMapActivity.GOOGLE_PLAY_SERVICES_REQUEST_CODE).show();
      } else {
        Log.d(TAG, "...and we can't fix it");
        // For now just warn the user, though we may need to do something like disable
        // auto location.
        Toast.makeText(parent, R.string.play_services_error, Toast.LENGTH_LONG).show();
      }
    }
    super.checkLocationServicesEnabled();
  }
}
