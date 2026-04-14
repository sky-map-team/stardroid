package com.google.android.stardroid.touch

import android.content.SharedPreferences
import com.google.android.stardroid.activities.util.FullscreenControlsManager
import com.google.android.stardroid.education.ObjectInfoTapHandler
import com.google.android.stardroid.util.Analytics
import com.google.android.stardroid.util.Toaster
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class GestureInterpreterFactory @Inject constructor(
    val mapMover: MapMover,
    val objectInfoTapHandler: ObjectInfoTapHandler?,
    val preferences: SharedPreferences,
    val toaster: Toaster,
    val analytics: Analytics
) {
    fun createGestureInterpreter(
        fullscreenControlsManager: FullscreenControlsManager,
        screenDimensionsProvider: GestureInterpreter.ScreenDimensionsProvider
    ): GestureInterpreter {
        return GestureInterpreter(
            mapMover,
            objectInfoTapHandler,
            preferences,
            toaster,
            analytics,
            fullscreenControlsManager,
            screenDimensionsProvider)
    }
}
