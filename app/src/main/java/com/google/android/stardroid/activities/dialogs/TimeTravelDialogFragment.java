package com.google.android.stardroid.activities.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.util.MiscUtil;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Time travel dialog fragment.
 * Created by johntaylor on 4/3/16.
 */
// TODO(jontayler): see if this crashes when backgrounded on older devices and use
// the fragment in this package if so.
@AndroidEntryPoint
public class TimeTravelDialogFragment extends DialogFragment {
  private static final String TAG = MiscUtil.getTag(TimeTravelDialogFragment.class);

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    DynamicStarMapActivity starMapActivity = (DynamicStarMapActivity) requireActivity();
    return new TimeTravelDialog(starMapActivity, starMapActivity.getModel());
  }
}
