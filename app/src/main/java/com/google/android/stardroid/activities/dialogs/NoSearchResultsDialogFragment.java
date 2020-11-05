package com.google.android.stardroid.activities.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import com.google.android.stardroid.R;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
public class NoSearchResultsDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(NoSearchResultsDialogFragment.class);
  @Inject Activity parentActivity;

  public interface ActivityComponent {
    void inject(NoSearchResultsDialogFragment fragment);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    // Activities using this dialog MUST implement this interface.  Obviously.
    ((HasComponent<ActivityComponent>) getActivity()).getComponent().inject(this);

    return new AlertDialog.Builder(parentActivity)
        .setTitle(R.string.no_search_title).setMessage(R.string.no_search_results_text)
        .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog1, int whichButton) {
            Log.d(TAG, "No search results Dialog closed");
            dialog1.dismiss();
          }
        }).create();
  }
}
