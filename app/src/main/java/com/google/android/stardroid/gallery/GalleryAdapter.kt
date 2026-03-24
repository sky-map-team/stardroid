package com.google.android.stardroid.gallery

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.util.ActivityLightLevelManager
import com.google.android.stardroid.education.ObjectInfo
import com.google.android.stardroid.util.AssetImageLoader
import com.google.android.stardroid.util.ImageLoadHandle

/** RecyclerView adapter that displays a grid of celestial object thumbnails. */
class GalleryAdapter(
    private val items: List<ObjectInfo>,
    private val onItemClick: (ObjectInfo) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.thumbnail_image)
        val titleView: TextView = view.findViewById(R.id.thumbnail_title)
        var loadHandle: ImageLoadHandle? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_thumbnail_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleView.text = item.name
        holder.imageView.setImageBitmap(null)
        holder.itemView.setOnClickListener { onItemClick(item) }

        val context = holder.itemView.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isNight = ActivityLightLevelManager.isNightMode(prefs)
        val nightColor = context.getColor(R.color.night_text_color)

        // Tint the title label in night mode.
        holder.titleView.setTextColor(
            if (isNight) nightColor else context.getColor(android.R.color.white)
        )

        val imagePath = item.imagePath ?: return

        // cancel() in AssetImageLoader atomically nulls the callback, so any load cancelled in
        // onViewRecycled will never deliver. No need for an isAttachedToWindow guard here.
        holder.loadHandle = AssetImageLoader.loadBitmapAsync(context.assets, imagePath) { bitmap ->
            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap)
                if (isNight) {
                    holder.imageView.setColorFilter(nightColor, PorterDuff.Mode.MULTIPLY)
                } else {
                    holder.imageView.clearColorFilter()
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Cancel the pending load so its callback cannot fire on a rebound ViewHolder.
        holder.loadHandle?.cancel()
        holder.loadHandle = null
        holder.imageView.setImageBitmap(null)
    }

    // Do NOT override onViewDetachedFromWindow to cancel loads: RecyclerView detaches views
    // during layout passes (not only when items scroll off), which would cancel loads for
    // still-visible items. onViewRecycled is the correct and sufficient cancellation point.

    override fun getItemCount(): Int = items.size
}
