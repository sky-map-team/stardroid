package com.google.android.stardroid.activities

import android.app.SearchManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.ObjectInfoDialogFragment
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.activities.util.EdgeToEdgeFixer
import com.google.android.stardroid.activities.util.NightModeHelper
import com.google.android.stardroid.education.ObjectInfo
import com.google.android.stardroid.education.ObjectInfoRegistry
import com.google.android.stardroid.gallery.GalleryAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Displays a scrollable grid of celestial object thumbnails sourced from [ObjectInfoRegistry].
 * Tapping a thumbnail shows its info card via [ObjectInfoDialogFragment].
 */
@AndroidEntryPoint
class ImageGalleryActivity : FragmentActivity(),
    ActivityLightLevelChanger.NightModeable,
    ObjectInfoDialogFragment.OnFindClickedListener,
    ObjectInfoDialogFragment.OnSeeAlsoClickedListener {

    @Inject lateinit var registry: ObjectInfoRegistry
    @Inject lateinit var activityLightLevelManager: ActivityLightLevelManager
    @Inject lateinit var sharedPreferences: SharedPreferences

    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_image_gallery)
        EdgeToEdgeFixer.applyEdgeToEdgeFixForActionBarActivity(this)

        val items = registry.getAllWithImages()
        val recyclerView = findViewById<RecyclerView>(R.id.gallery_grid)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        galleryAdapter = GalleryAdapter(items) { info ->
            showDialog(ObjectInfoDialogFragment.newInstance(info), "Object Info:${info.id}")
        }
        recyclerView.adapter = galleryAdapter
    }

    override fun onStart() {
        super.onStart()
        EdgeToEdgeFixer.applyTopPaddingForActionBar(this, findViewById(R.id.gallery_grid))
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
        NightModeHelper.applyActionBarNightMode(actionBar, this, nightMode)
        galleryAdapter.notifyDataSetChanged()
    }

    override fun onSeeAlsoClicked(objectId: String) {
        val info = registry.getInfo(objectId) ?: return
        showDialog(ObjectInfoDialogFragment.newInstance(info), "Object Info:$objectId")
    }

    private fun showDialog(fragment: DialogFragment, tag: String) {
        if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentByTag(tag) == null) {
            fragment.show(supportFragmentManager, tag)
        }
    }

    override fun onFindClicked(info: ObjectInfo) {
        // Ensure the relevant layers are visible so the object can be found.
        sharedPreferences.edit()
            .putBoolean("source_provider.0", true) // Stars
            .putBoolean("source_provider.1", true) // Constellations
            .putBoolean("source_provider.2", true) // Deep Sky Objects
            .putBoolean("source_provider.3", true) // Planets
            .apply()
        val searchIntent = Intent(this, DynamicStarMapActivity::class.java).apply {
            action = Intent.ACTION_SEARCH
            putExtra(SearchManager.QUERY, info.name)
        }
        startActivity(searchIntent)
    }
}
