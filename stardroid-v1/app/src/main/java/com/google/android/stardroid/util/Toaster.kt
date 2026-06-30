/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.widget.Toast

/**
 * A wrapper around the Toast mechanism for easier unit testing.
 *
 * Created by johntaylor on 4/24/16.
 */
class Toaster @Inject constructor(@ApplicationContext private val context: Context) {
  fun toastLong(resId: Int) {
    Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
  }

  fun toastLong(s: String?) {
    Toast.makeText(context, s, Toast.LENGTH_LONG).show()
  }
}