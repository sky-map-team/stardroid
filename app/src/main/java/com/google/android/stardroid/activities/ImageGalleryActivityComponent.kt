package com.google.android.stardroid.activities

import com.google.android.stardroid.ApplicationComponent
import com.google.android.stardroid.activities.dialogs.ObjectInfoDialogFragment
import com.google.android.stardroid.inject.PerActivity
import dagger.Component

@PerActivity
@Component(
    modules = [ImageGalleryActivityModule::class],
    dependencies = [ApplicationComponent::class]
)
interface ImageGalleryActivityComponent : ObjectInfoDialogFragment.ActivityComponent {
    fun inject(activity: ImageGalleryActivity)
}
