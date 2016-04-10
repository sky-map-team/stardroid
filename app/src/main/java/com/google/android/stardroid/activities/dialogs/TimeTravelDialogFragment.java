package com.google.android.stardroid.activities.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.views.TimeTravelDialog;

import javax.inject.Inject;

/**
 * End User License agreement dialog.
 * Created by johntaylor on 4/3/16.
 */
public class TimeTravelDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(TimeTravelDialogFragment.class);
  @Inject DynamicStarMapActivity parentActivity;

  public interface ActivityComponent {
    void inject(TimeTravelDialogFragment fragment);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    // Activities using this dialog MUST implement this interface.  Obviously.
    ((HasComponent<ActivityComponent>) getActivity()).getComponent().inject(this);

    TimeTravelDialog timeTravelDialog = new TimeTravelDialog(parentActivity,
        parentActivity.getModel());
    return timeTravelDialog;
  }
}
