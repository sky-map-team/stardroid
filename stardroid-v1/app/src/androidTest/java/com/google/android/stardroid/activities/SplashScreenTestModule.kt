package com.google.android.stardroid.activities

import android.app.Activity
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import dagger.Module
import dagger.Provides
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.testing.TestInstallIn
import javax.inject.Named

@Module
@TestInstallIn(components = [ActivityComponent::class], replaces = [SplashScreenModule::class])
object SplashScreenTestModule {
    @Provides
    @ActivityScoped
    @Named("fadeout")
    fun provideFadeoutAnimation(activity: Activity): Animation =
        // On CI emulators (especially API 36 with no window focus), the Choreographer may not
        // deliver vsync callbacks, so View.startAnimation() never fires onAnimationEnd.
        // Override setAnimationListener to fire the listener immediately so the splash
        // transition doesn't depend on Choreographer at all.
        object : AlphaAnimation(1.0f, 0.0f) {
            override fun setAnimationListener(listener: Animation.AnimationListener?) {
                super.setAnimationListener(listener)
                if (listener != null) {
                    listener.onAnimationStart(this)
                    listener.onAnimationEnd(this)
                }
            }
        }.apply { duration = 0 }
}
