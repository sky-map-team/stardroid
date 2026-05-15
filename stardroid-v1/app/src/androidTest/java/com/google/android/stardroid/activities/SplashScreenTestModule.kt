package com.google.android.stardroid.activities

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import dagger.Module
import dagger.Provides
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.testing.TestInstallIn
import javax.inject.Named

// On CI emulators (especially API 36 with no window focus), the Choreographer may not deliver
// vsync callbacks, so View.startAnimation() never fires onAnimationEnd. When start() is called
// (from View.startAnimation -> Animation.getTransformation -> start), post the listener
// callbacks to the main thread so they run independently of Choreographer.
private class ImmediateFadeoutAnimation : AlphaAnimation(1.0f, 0.0f) {
    init { duration = 0 }

    private var capturedListener: AnimationListener? = null
    private var fired = false

    override fun setAnimationListener(listener: AnimationListener?) {
        super.setAnimationListener(listener)
        capturedListener = listener
    }

    override fun setStartTime(startTimeMillis: Long) {
        super.setStartTime(startTimeMillis)
        if (fired) return
        fired = true
        val listener = capturedListener ?: return
        Handler(Looper.getMainLooper()).post {
            listener.onAnimationStart(this)
            listener.onAnimationEnd(this)
        }
    }
}

@Module
@TestInstallIn(components = [ActivityComponent::class], replaces = [SplashScreenModule::class])
object SplashScreenTestModule {
    @Provides
    @ActivityScoped
    @Named("fadeout")
    fun provideFadeoutAnimation(activity: Activity): Animation = ImmediateFadeoutAnimation()
}
