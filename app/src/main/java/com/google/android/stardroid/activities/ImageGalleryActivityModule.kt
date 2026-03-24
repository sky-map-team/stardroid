package com.google.android.stardroid.activities

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.Window
import com.google.android.stardroid.activities.dialogs.ObjectInfoDialogFragment
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.inject.PerActivity
import dagger.Module
import dagger.Provides

@Module
class ImageGalleryActivityModule(private val activity: ImageGalleryActivity) {

    @Provides @PerActivity
    fun provideActivity(): Activity = activity

    @Provides @PerActivity
    fun provideActivityContext(): Context = activity

    @Provides @PerActivity
    fun provideAssetManager(): AssetManager = activity.assets

    @Provides @PerActivity
    fun provideHandler(): Handler = Handler(Looper.getMainLooper())

    @Provides @PerActivity
    fun provideWindow(): Window = activity.window

    @Provides @PerActivity
    fun provideNightModeable(): ActivityLightLevelChanger.NightModeable = activity

    @Provides @PerActivity
    fun provideObjectInfoDialogFragment(): ObjectInfoDialogFragment = ObjectInfoDialogFragment()
}
