package com.google.android.stardroid.activities

import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.ObjectInfoDialogFragment
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.education.ObjectInfoRegistry
import com.google.android.stardroid.gallery.GalleryAdapter
import com.google.android.stardroid.inject.HasComponent
import javax.inject.Inject

/**
 * Displays a scrollable grid of celestial object thumbnails sourced from [ObjectInfoRegistry].
 * Tapping a thumbnail shows its info card via [ObjectInfoDialogFragment].
 */
class ImageGalleryActivity : InjectableActivity(),
    HasComponent<ImageGalleryActivityComponent>,
    ActivityLightLevelChanger.NightModeable {

    @Inject lateinit var registry: ObjectInfoRegistry
    @Inject lateinit var activityLightLevelManager: ActivityLightLevelManager

    override lateinit var component: ImageGalleryActivityComponent
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component = DaggerImageGalleryActivityComponent.builder()
            .applicationComponent(getApplicationComponent())
            .imageGalleryActivityModule(ImageGalleryActivityModule(this))
            .build()
        component.inject(this)

        setContentView(R.layout.activity_image_gallery)

        val items = registry.getAllWithImages()
        val recyclerView = findViewById<RecyclerView>(R.id.gallery_grid)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = GalleryAdapter(items) { info ->
            if (!supportFragmentManager.isStateSaved) {
                ObjectInfoDialogFragment.newInstance(info)
                    .show(supportFragmentManager, "ObjectInfo")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityLightLevelManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        activityLightLevelManager.onPause()
    }

    override fun setNightMode(nightMode: Boolean) {
        // Night mode is applied per-item in GalleryAdapter.onBindViewHolder
    }
}
