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

package com.google.android.stardroid.activities;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.search.SearchResult;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * A grab bag of dialogs used in the DynamicStarMapActivity.  Extracted
 * to this class simply to reduce clutter in an already complex class.
 *
 * @author John Taylor
 */
// TODO(jontayler): rework this into dialog fragments.
public class DialogFactory {
  private static final String TAG = MiscUtil.getTag(DialogFactory.class);

  static final int DIALOG_ID_NO_SENSORS = 7;

  private DynamicStarMapActivity parentActivity;
  private ArrayAdapter<SearchResult> multipleSearchResultsAdaptor;
  private SharedPreferences preferences;
  private Analytics analytics;

  /**
   * Constructor.
   *
   * @param parentActivity the parent activity showing these dialogs.
   */
  @Inject
  DialogFactory(DynamicStarMapActivity parentActivity, SharedPreferences preferences,
                Analytics analytics) {
    this.parentActivity = parentActivity;
    this.preferences = preferences;
    this.analytics = analytics;
    multipleSearchResultsAdaptor = new ArrayAdapter<>(
        parentActivity, android.R.layout.simple_list_item_1, new ArrayList<SearchResult>());
  }

  /**
   * Creates dialogs on demand.  Delegated to by the parentActivity.
   */
  public Dialog onCreateDialog(int id) {
    switch (id) {
      case (DIALOG_ID_NO_SENSORS):
        return createNoSensorsDialog();
    }
    throw new RuntimeException("Unknown dialog Id.");
  }

  private Dialog createNoSensorsDialog() {
    LayoutInflater inflater = parentActivity.getLayoutInflater();
    final View view = inflater.inflate(R.layout.no_sensor_warning, null);
    AlertDialog alertDialog = new Builder(parentActivity)
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
    return alertDialog;
  }




  /**
   * Helper method that modifies the {@link #multipleSearchResultsAdaptor}.
   * This isn't really the right way to do it.  The correct thing
   * to do would be to do away with this method and pass the search results to
   * the dialog creator inside a Bundle.  However, that would require
   * SearchResults to be made into a Parcelable.  Furthermore, onCreateDialog
   * is not called the second time this dialog is requested, so we would have
   * to override onPrepareDialog instead
   * ...and that has only been around since API v8.
   * So, enough excuses - this is wrong, but so much easier.
   *
   * @param results the search results
   */
  // TODO? Dead?
  private void showUserChooseResultDialog(List<SearchResult> results) {
    multipleSearchResultsAdaptor.clear();
    for (SearchResult result : results) {
      multipleSearchResultsAdaptor.add(result);
    }
    //parentActivity.showDialog(DialogFactory.DIALOG_ID_MULTIPLE_SEARCH_RESULTS);
  }
}
