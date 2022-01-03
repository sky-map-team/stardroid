package com.google.android.stardroid.activities.dialogs;

import android.app.FragmentManager;

/**
 * A dialog fragment that only shows itself if it's not already shown.  This prevents
 * a java.lang.IllegalStateException when the activity gets backgrounded.
 * Created by johntaylor on 4/11/16.
 */
public abstract class DialogFragment extends android.app.DialogFragment {
  @Override
  public void show(FragmentManager fragmentManager, String tag) {
    if (this.isAdded()) return;
    super.show(fragmentManager, tag);
  }
}
