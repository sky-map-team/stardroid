package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.search.SearchResult;
import com.google.android.stardroid.util.MiscUtil;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
@AndroidEntryPoint
public class MultipleSearchResultsDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(MultipleSearchResultsDialogFragment.class);
  @Inject Activity parentActivity;

  private ArrayAdapter<SearchResult> multipleSearchResultsAdaptor;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    DynamicStarMapActivity starMapActivity = (DynamicStarMapActivity) parentActivity;

    // TODO(jontayler): inject
    multipleSearchResultsAdaptor = new ArrayAdapter<>(
        starMapActivity, android.R.layout.simple_list_item_1, new ArrayList<SearchResult>());


    DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
        if (whichButton == Dialog.BUTTON_NEGATIVE) {
          Log.d(TAG, "Many search results Dialog closed with cancel");
        } else {
          final SearchResult item = multipleSearchResultsAdaptor.getItem(whichButton);
          starMapActivity.activateSearchTarget(item.coords(), item.getCapitalizedName());
        }
        dialog.dismiss();
      }
    };

    return new AlertDialog.Builder(starMapActivity)
        .setTitle(R.string.many_search_results_title)
        .setNegativeButton(android.R.string.cancel, onClickListener)
        .setAdapter(multipleSearchResultsAdaptor, onClickListener)
        .create();
  }

  public void clearResults() {
    multipleSearchResultsAdaptor.clear();
  }

  public void add(SearchResult result) {
    multipleSearchResultsAdaptor.add(result);
  }
}
