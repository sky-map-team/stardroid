package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * No sensors dialog fragment.
 * Created by johntaylor on 4/9/16.
 */
@AndroidEntryPoint
public class NoSensorsDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(NoSensorsDialogFragment.class);
  @Inject Activity parentActivity;
  @Inject SharedPreferences preferences;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = parentActivity.getLayoutInflater();
    final View view = inflater.inflate(R.layout.no_sensor_warning, null);
    return new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.warning_dialog_title)
        .setView(view).setNegativeButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(TAG, "No Sensor Dialog closed");
                preferences.edit().putBoolean(
                    ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS,
                    ((CheckBox) view.findViewById(R.id.no_show_dialog_again)).isChecked()).commit();
                dialog.dismiss();
              }
            }).create();
  }
}
