package com.google.android.stardroid.util

import android.content.Context
import javax.inject.Inject
import android.widget.Toast

/**
 * A wrapper around the Toast mechanism for easier unit testing.
 *
 * Created by johntaylor on 4/24/16.
 */
class Toaster @Inject constructor(private val context: Context) {
  fun toastLong(resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
  }

  fun toastLong(s: String?) {
    Toast.makeText(context, s, Toast.LENGTH_LONG).show()
  }
}