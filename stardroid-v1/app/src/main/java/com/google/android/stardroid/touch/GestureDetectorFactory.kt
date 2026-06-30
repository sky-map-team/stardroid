/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.touch

import android.app.Activity
import android.view.GestureDetector
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class GestureDetectorFactory @Inject constructor(private val activity: Activity) {
    fun createGestureDetector(gestureInterpreter: GestureInterpreter): GestureDetector
        = GestureDetector(activity, gestureInterpreter);
}