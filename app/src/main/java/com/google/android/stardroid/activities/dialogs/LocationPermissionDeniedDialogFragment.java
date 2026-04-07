package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.EditSettingsActivity;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Dialog fragment shown when location permission is not granted.
 * Offers the user three options: grant permission, enter location manually, or decide later.
 */
@AndroidEntryPoint
public class LocationPermissionDeniedDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(LocationPermissionDeniedDialogFragment.class);

  @Inject Activity parentActivity;
  @Inject SharedPreferences preferences;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    return new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.location_permission_dialog_title)
        .setMessage(R.string.location_permission_dialog_message)
        .setNeutralButton(R.string.location_permission_later, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "User chose later");
            dialog.dismiss();
          }
        })
        .setNegativeButton(R.string.location_permission_enter_manually, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "User chose to enter location manually");
            preferences.edit()
                .putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true)
                .apply();
            Intent intent = new Intent(parentActivity, EditSettingsActivity.class);
            parentActivity.startActivity(intent);
          }
        })
        .setPositiveButton(R.string.location_permission_grant, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "User chose to grant permission - opening app settings");
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", parentActivity.getPackageName(), null);
            intent.setData(uri);
            parentActivity.startActivity(intent);
          }
        })
        .create();
  }

  @Override
  public void onStart() {
    super.onStart();
    // Highlight the Grant Permission button to make it more prominent
    AlertDialog dialog = (AlertDialog) getDialog();
    if (dialog != null) {
      Button grantButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
      if (grantButton != null) {
        grantButton.setTypeface(null, Typeface.BOLD);
      }
    }
  }
}
