package com.google.android.stardroid.util;

import android.content.Context;
import android.widget.Toast;

import javax.inject.Inject;

/**
 * A wrapper around the Toast mechanism for easier unit testing.
 *
 * Created by johntaylor on 4/24/16.
 */
public class Toaster {
  private Context context;

  @Inject
  public Toaster(Context context) {
    this.context = context;
  }

  public void toastLong(int resId) {
    Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
  }

  public void toastLong(String s) {
    Toast.makeText(context, s, Toast.LENGTH_LONG).show();
  }
}
