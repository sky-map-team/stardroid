package com.google.android.stardroid.activities.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.util.MiscUtil;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Dialog shown when a search returns multiple results.
 */
@AndroidEntryPoint
public class MultipleSearchResultsDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(MultipleSearchResultsDialogFragment.class);
  private static final String RESULTS_KEY = "results";

  public static MultipleSearchResultsDialogFragment newInstance(ArrayList<SearchResultItem> results) {
    MultipleSearchResultsDialogFragment fragment = new MultipleSearchResultsDialogFragment();
    Bundle args = new Bundle();
    args.putParcelableArrayList(RESULTS_KEY, results);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    ArrayList<SearchResultItem> results = requireArguments().getParcelableArrayList(RESULTS_KEY);
    final DynamicStarMapActivity starMapActivity = (DynamicStarMapActivity) requireActivity();
    final ArrayAdapter<SearchResultItem> adapter = new ArrayAdapter<>(
        starMapActivity, android.R.layout.simple_list_item_1, results);

    DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
        if (whichButton == Dialog.BUTTON_NEGATIVE) {
          Log.d(TAG, "Many search results Dialog closed with cancel");
        } else {
          final SearchResultItem item = adapter.getItem(whichButton);
          starMapActivity.activateSearchTarget(
              new Vector3(item.getX(), item.getY(), item.getZ()), item.getName());
        }
        dialog.dismiss();
      }
    };

    return new AlertDialog.Builder(starMapActivity)
        .setTitle(R.string.many_search_results_title)
        .setNegativeButton(android.R.string.cancel, onClickListener)
        .setAdapter(adapter, onClickListener)
        .create();
  }
}
