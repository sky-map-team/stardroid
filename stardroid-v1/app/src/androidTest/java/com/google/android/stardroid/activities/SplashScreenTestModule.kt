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
        AlphaAnimation(1.0f, 0.1f).apply { duration = 1 }
}
