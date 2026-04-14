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