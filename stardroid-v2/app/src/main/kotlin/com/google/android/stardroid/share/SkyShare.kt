/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.share_chooser_title
import com.google.android.stardroid.ui.resources.share_invitation
import com.google.android.stardroid.ui.resources.share_text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.io.File
import kotlin.coroutines.resume

/**
 * Android's half of sharing the sky (camera-ar-mode.md Part 3): grab the GL plane with
 * [PixelCopy], and save or send what the shared [ShareCompositor] draws from it. The Compose
 * chrome is naturally excluded from every layout: the captures read surfaces, not the window,
 * so an open sheet or dialog never leaks into the image.
 *
 * ### Bitmap ownership (audit-2026-08 M5)
 *
 * Full-screen `ARGB_8888` bitmaps are ~18 MB each on a 1440×3120 display, and this pipeline
 * stacks four to six of them. Left to the GC that is a plausible [OutOfMemoryError] on the
 * app's growth loop, so lifetimes are explicit, under one rule:
 *
 * - **A private intermediate is recycled by whoever consumes it.**
 * - **A bitmap passed in from outside is never recycled.** The caller owns it and frees it
 *   when its own use is done — see the `map`/`still` handling in `MapRoute`.
 *
 * Captures are also downsampled to [MAX_SHARE_EDGE_PX] before compositing, which is the
 * larger win: it scales every bitmap in the pipeline down at once, and the share target
 * re-encodes the image anyway.
 *
 * The two finished [ShareLayouts] are deliberately **not** recycled. They live in a ViewModel
 * (surviving rotation) and are drawn as thumbnails, so recycling them races with the sheet
 * still drawing them — and a recycled bitmap that is still being drawn is a crash. They are
 * ordinary garbage instead, and bounded by [MAX_SHARE_EDGE_PX] to roughly 25 MB for the pair.
 */
object SkyShare {
    /** Map-only share (slice 3): capture, brand, and launch the chooser in one go. */
    suspend fun shareSky(
        context: Context,
        glSurfaceView: GLSurfaceView,
        compositor: ShareCompositor,
    ): Boolean {
        val map = captureMap(glSurfaceView) ?: return false
        val branded =
            try {
                withBranding(context) { compositor.mapOnly(map.asImageBitmap(), it) }
            } finally {
                map.recycle()
            }.asAndroidBitmap()
        sendBitmap(context, branded)
        // sendBitmap has compressed it to the share file; nothing reads the bitmap again.
        branded.recycle()
        return true
    }

    /**
     * The AR share layouts (slice 4): the map plane and the camera still composited both
     * ways, footered and ready to send — the user picks one from the layout sheet.
     * [map] and [cameraStill] stay the caller's to recycle.
     */
    suspend fun arShareLayouts(
        context: Context,
        compositor: ShareCompositor,
        map: Bitmap,
        cameraStill: Bitmap,
        scrim: Double,
    ): ShareLayouts =
        withBranding(context) {
            compositor.arLayouts(map.asImageBitmap(), cameraStill.asImageBitmap(), scrim, it)
        }

    /**
     * Saves [bitmap] to Pictures/Sky Map (scoped storage, no permission) and returns its URI,
     * or null on failure. On API 28, which lacks `RELATIVE_PATH`, this is a silent null.
     */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
    ): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val values =
                    ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "sky_map_${System.currentTimeMillis()}.png",
                        )
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/Sky Map",
                        )
                        // Hidden from the gallery until fully written.
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext null
                val written =
                    try {
                        resolver.openOutputStream(uri)?.use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        } == true
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not write the photo to the gallery", e)
                        false
                    }
                if (written) {
                    resolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        },
                        null,
                        null,
                    )
                    uri
                } else {
                    resolver.delete(uri, null, null)
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save the photo to the gallery", e)
                null
            }
        }

    /** Writes [bitmap] to the share cache and launches the system share sheet. */
    suspend fun sendBitmap(
        context: Context,
        bitmap: Bitmap,
    ) {
        val uri =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                // One well-known name: successive shares overwrite instead of piling up
                // stale captures in the cache.
                val file = File(dir, SHARE_FILE)
                file.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        sendUri(context, uri)
    }

    /** Launches the system share sheet for an image already at [uri]. */
    suspend fun sendUri(
        context: Context,
        uri: Uri,
    ) {
        val link = context.getString(R.string.share_store_link)
        val text = getString(Res.string.share_text, link)
        val title = getString(Res.string.share_chooser_title)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // The invitation rides as text for targets that take both (v1's pattern
                // for store links); image-only targets simply drop it.
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, title),
        )
    }

    /**
     * The GL plane as a bitmap ([PixelCopy] — available since API 24, fine at our minSdk), or null on failure,
     * downsampled to [MAX_SHARE_EDGE_PX].
     */
    suspend fun captureMap(view: GLSurfaceView): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val copied =
            suspendCancellableCoroutine { continuation ->
                PixelCopy.request(
                    view,
                    full,
                    { result ->
                        if (result != PixelCopy.SUCCESS) Log.w(TAG, "PixelCopy failed: $result")
                        continuation.resume(result == PixelCopy.SUCCESS)
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
        if (!copied) {
            full.recycle()
            return null
        }
        return toShareSize(full)
    }

    /**
     * [source] scaled so its long edge is at most [MAX_SHARE_EDGE_PX], **recycling [source]**
     * when it scales; [source] itself when it already fits.
     *
     * The scale happens after the capture rather than by asking [PixelCopy] for a smaller
     * destination: a full-size copy followed by an explicit scale has obvious semantics,
     * where relying on the platform to scale into an undersized destination does not, and
     * this decides what the user's shared image actually looks like.
     */
    fun toShareSize(source: Bitmap): Bitmap {
        val target =
            shareScaledSize(source.width, source.height, MAX_SHARE_EDGE_PX) ?: return source
        val scaled = Bitmap.createScaledBitmap(source, target.width, target.height, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    private suspend fun <T> withBranding(
        context: Context,
        block: suspend (ShareBranding) -> T,
    ): T {
        val invitation = getString(Res.string.share_invitation)
        val icon = context.packageManager.getApplicationIcon(context.packageName).toBitmap()
        try {
            return block(
                ShareBranding(
                    icon = icon.asImageBitmap(),
                    appName = context.getString(R.string.app_name),
                    invitation = invitation,
                    link = context.getString(R.string.share_store_link),
                ),
            )
        } finally {
            icon.recycle()
        }
    }

    private const val TAG = "SkyShare"
    private const val SHARE_DIR = "share"
    private const val SHARE_FILE = "sky_map.png"
}
