/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.google.android.stardroid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Share the sky (camera-ar-mode.md Part 3, slices 3–4): grab the GL plane with
 * [PixelCopy], composite it — map-only over black, or with a real camera still in the
 * overlay and side-by-side layouts while the AR layer is on — stamp the branding footer,
 * and hand the image to the system share sheet with the download invitation as text. The
 * Compose chrome is naturally excluded from every layout: the captures read surfaces,
 * not the window, so an open sheet or dialog never leaks into the image.
 *
 * ### Bitmap ownership (audit-2026-08 M5)
 *
 * Full-screen `ARGB_8888` bitmaps are ~18 MB each on a 1440×3120 display, and this pipeline
 * stacks four to six of them. Left to the GC that is a plausible [OutOfMemoryError] on the
 * app's growth loop, so lifetimes are explicit here, under one rule:
 *
 * - **A private intermediate is recycled by whoever consumes it.** Every bitmap created
 *   inside this file is consumed exactly once and freed at that point.
 * - **A bitmap passed in from outside is never recycled here.** The caller owns it and
 *   frees it when its own use is done — see the `map`/`still` handling in `MapScreen`.
 *
 * Captures are also downsampled to [MAX_SHARE_EDGE_PX] before compositing, which is the
 * larger win: it scales every bitmap in the pipeline down at once, and the share target
 * re-encodes the image anyway.
 *
 * The two finished [ShareLayouts] are deliberately **not** recycled. They are held in Compose
 * state and drawn as thumbnails, so recycling them races with the recomposition that drops
 * the sheet — and a recycled bitmap that is still being drawn is a crash. They are ordinary
 * garbage instead, and bounded by [MAX_SHARE_EDGE_PX] to roughly 25 MB for the pair.
 */
object SkyShare {
    /** Map-only share (slice 3): capture, brand, and launch the chooser in one go. */
    suspend fun shareSky(
        context: Context,
        glSurfaceView: GLSurfaceView,
    ): Boolean {
        val map = captureMap(glSurfaceView) ?: return false
        val branded =
            withContext(Dispatchers.Default) {
                val overBlack = mapOverBlack(map)
                // This capture is ours — nobody outside saw it — so it goes as soon as it is
                // flattened, rather than staying live through the footer pass.
                map.recycle()
                val branding = branding(context)
                try {
                    appendFooter(overBlack, branding)
                } finally {
                    branding.recycle()
                }
            }
        sendBitmap(context, branded)
        // sendBitmap has compressed it to the share file; nothing reads the bitmap again.
        branded.recycle()
        return true
    }

    /**
     * The AR share layouts (slice 4): the map plane and the camera still composited both
     * ways, footered and ready to send — the user picks one from the layout sheet.
     *
     * [map] and [cameraStill] stay the caller's to recycle — both are read twice here, once
     * per layout, and the sheet shows the two results side by side, so neither can be
     * released early and neither belongs to this function.
     */
    suspend fun arShareLayouts(
        context: Context,
        map: Bitmap,
        cameraStill: Bitmap,
        scrim: Double,
    ): ShareLayouts =
        withContext(Dispatchers.Default) {
            val branding = branding(context)
            try {
                ShareLayouts(
                    overlay = appendFooter(overlayComposite(map, cameraStill, scrim), branding),
                    sideBySide =
                        appendFooter(sideBySideComposite(map, cameraStill, scrim), branding),
                )
            } finally {
                branding.recycle()
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
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // The invitation rides as text for targets that take both (v1's pattern
                // for store links); image-only targets simply drop it.
                putExtra(
                    Intent.EXTRA_TEXT,
                    context.getString(
                        R.string.share_text,
                        context.getString(R.string.share_store_link),
                    ),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_chooser_title)),
        )
    }

    /**
     * The GL plane as a bitmap ([PixelCopy] — fine at minSdk 29), or null on failure,
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

    /** The two AR share layouts, fully composited and footered. */
    data class ShareLayouts(
        val overlay: Bitmap,
        val sideBySide: Bitmap,
    )

    private data class Branding(
        val icon: Bitmap?,
        val appName: String,
        val invitation: String,
        val link: String,
    ) {
        /**
         * Frees the launcher-icon bitmap. Separate from [appendFooter] because one [Branding]
         * is shared by both AR layouts — the footer pass may not free what it is handed.
         */
        fun recycle() = icon?.recycle() ?: Unit
    }

    private fun branding(context: Context): Branding =
        Branding(
            icon =
                context.packageManager
                    .getApplicationIcon(context.packageName)
                    .toBitmap(),
            appName = context.getString(R.string.app_name),
            invitation = context.getString(R.string.share_invitation),
            link = context.getString(R.string.share_store_link),
        )

    /**
     * The map plane flattened over opaque black (it carries alpha while AR is on).
     *
     * Never recycles [map]: one caller owns the capture and frees it right after, the other
     * needs it again for the second layout.
     */
    private fun mapOverBlack(map: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(map, 0f, 0f, null)
        return result
    }

    /**
     * The overlay layout (slice 4): the camera still centre-cropped to the map's aspect,
     * dimmed by the scrim the user had dialled in, with the translucent map plane over it —
     * the composite matches what the screen showed.
     */
    private fun overlayComposite(
        map: Bitmap,
        cameraStill: Bitmap,
        scrim: Double,
    ): Bitmap {
        val result = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        drawCameraColumn(canvas, cameraStill, map.width, map.height, 0, scrim)
        canvas.drawBitmap(map, 0f, 0f, null)
        return result
    }

    /**
     * The side-by-side layout (slice 4): the (scrimmed) camera view on the left, the drawn
     * sky over black on the right — "what I saw | what it was".
     */
    private fun sideBySideComposite(
        map: Bitmap,
        cameraStill: Bitmap,
        scrim: Double,
    ): Bitmap {
        val gap = (map.width * SIDE_BY_SIDE_GAP_FRACTION).toInt()
        val result =
            Bitmap.createBitmap(map.width * 2 + gap, map.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        drawCameraColumn(canvas, cameraStill, map.width, map.height, 0, scrim)
        val mapColumn = mapOverBlack(map)
        canvas.drawBitmap(mapColumn, (map.width + gap).toFloat(), 0f, null)
        // A private intermediate that was previously dropped un-recycled — a full-screen
        // bitmap's worth of garbage per share.
        mapColumn.recycle()
        return result
    }

    /** Draws the still centre-cropped into a `width × height` column at [left], scrimmed. */
    private fun drawCameraColumn(
        canvas: Canvas,
        cameraStill: Bitmap,
        width: Int,
        height: Int,
        left: Int,
        scrim: Double,
    ) {
        val crop =
            centerCrop(
                cameraStill.width,
                cameraStill.height,
                width.toDouble() / height,
            )
        canvas.drawBitmap(
            cameraStill,
            Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
            Rect(left, 0, left + width, height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val alpha = (scrim.coerceIn(0.0, 1.0) * 255).toInt()
        if (alpha > 0) {
            canvas.drawRect(
                Rect(left, 0, left + width, height),
                Paint().apply { color = Color.argb(alpha, 0, 0, 0) },
            )
        }
    }

    /**
     * Appends the branding footer strip — icon, app name, invitation, and store link.
     * Footer metrics scale with the image width so the strip reads the same at any capture
     * resolution; the body type shrinks until the longest line fits.
     *
     * **Recycles [content]**, which is always a private intermediate composite. [branding] is
     * not recycled — it outlives this call when both AR layouts are being built.
     */
    private fun appendFooter(
        content: Bitmap,
        branding: Branding,
    ): Bitmap {
        val width = content.width
        val contentHeight = content.height
        // The side-by-side layout is twice as wide; keep the footer sized like the
        // portrait one instead of doubling with the canvas.
        val footerHeight = (minOf(width, contentHeight) * FOOTER_HEIGHT_FRACTION).toInt()
        val result =
            Bitmap.createBitmap(width, contentHeight + footerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(content, 0f, 0f, null)
        // Drawn, and never read again: freed here rather than held live through the whole
        // footer pass alongside `result`, which is the same size again.
        content.recycle()

        val footerTop = contentHeight.toFloat()
        canvas.drawRect(
            0f,
            footerTop,
            width.toFloat(),
            (contentHeight + footerHeight).toFloat(),
            Paint().apply { color = FOOTER_BACKGROUND },
        )
        val pad = footerHeight * 0.18f
        val iconSize = footerHeight - 2 * pad
        var textLeft = pad
        val icon = branding.icon
        if (icon != null) {
            val scaled =
                Bitmap.createScaledBitmap(icon, iconSize.toInt(), iconSize.toInt(), true)
            canvas.drawBitmap(scaled, pad, footerTop + pad, null)
            // createScaledBitmap hands back the source itself when the size already matches,
            // and `icon` belongs to the shared Branding — only free a genuine copy.
            if (scaled !== icon) scaled.recycle()
            textLeft = pad + iconSize + pad
        }
        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TITLE_COLOR
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = footerHeight * 0.30f
            }
        val bodyPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = BODY_COLOR
                typeface = Typeface.SANS_SERIF
                textSize = footerHeight * 0.17f
                // The store link is the longest line; shrink the body type until it fits
                // rather than letting it clip at the edge.
                val available = width - textLeft - pad
                val widest = maxOf(measureText(branding.link), measureText(branding.invitation))
                if (widest > available) textSize *= available / widest
            }
        canvas.drawText(
            branding.appName,
            textLeft,
            footerTop + pad + titlePaint.textSize,
            titlePaint,
        )
        val bodyTop = footerTop + pad + titlePaint.textSize + bodyPaint.textSize * 1.4f
        canvas.drawText(branding.invitation, textLeft, bodyTop, bodyPaint)
        canvas.drawText(branding.link, textLeft, bodyTop + bodyPaint.textSize * 1.4f, bodyPaint)
        return result
    }

    private const val TAG = "SkyShare"
    private const val SHARE_DIR = "share"
    private const val SHARE_FILE = "sky_map.png"

    /**
     * Longest edge of a capture before compositing (audit-2026-08 M5).
     *
     * Generous — above 1080p, and the share target re-encodes the image anyway — while
     * cutting a 1440×3120 capture from ~18 MB to ~7.7 MB, and every downstream composite
     * with it. This is the number that decides the shared image's resolution.
     */
    private const val MAX_SHARE_EDGE_PX = 2048

    /** The seam between the two side-by-side columns, as a fraction of one column. */
    private const val SIDE_BY_SIDE_GAP_FRACTION = 0.008

    /** Footer strip height as a fraction of the image width (~a fifth of the short side). */
    private const val FOOTER_HEIGHT_FRACTION = 0.16

    private const val FOOTER_BACKGROUND = 0xFF131720.toInt()
    private const val TITLE_COLOR = 0xFFF2B04A.toInt()
    private const val BODY_COLOR = 0xFF93A0B4.toInt()
}
