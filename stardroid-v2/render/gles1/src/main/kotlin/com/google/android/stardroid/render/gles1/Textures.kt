/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.opengl.GLUtils
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

/*
 * Texture-upload helpers shared by the drawers that keep per-image GL textures
 * ([ImageDrawer], [IconDrawer], both through [TextureCache]). All functions must be called on
 * the GL thread.
 */

/** Uploads [bmp] as-is with mipmapped linear/clamp params; returns the new texture ID. */
internal fun uploadRgbaTexture(
    gl: GL10,
    bmp: Bitmap,
): Int {
    val id = genTexture(gl)
    gl.glBindTexture(GL10.GL_TEXTURE_2D, id)
    val mipmapped = requestMipmaps(gl, bmp)
    setLinearClampParams(gl, mipmapped)
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0)
    return id
}

/**
 * Uploads a red-channel-only version of [bmp]: R = per-pixel luminance, G = B = 0,
 * A = original alpha. Mirrors v1's `ImageObjectManager.createRedImage` for night-mode display.
 *
 * Uses a [ColorMatrixColorFilter] on a temporary [Canvas] so the conversion runs in Skia
 * native code rather than a Kotlin pixel loop, and avoids allocating a direct byte buffer.
 */
internal fun uploadRedTexture(
    gl: GL10,
    bmp: Bitmap,
): Int {
    val redBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(redBmp)
    val paint =
        Paint().apply {
            colorFilter =
                ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            // NTSC luminance (matches StellarStyler.applyNightMode):
                            // G_out = B_out = 0, A_out = A
                            0.299f, 0.587f, 0.114f, 0f, 0f,
                            0f, 0f, 0f, 0f, 0f,
                            0f, 0f, 0f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
        }
    canvas.drawBitmap(bmp, 0f, 0f, paint)
    val id = genTexture(gl)
    gl.glBindTexture(GL10.GL_TEXTURE_2D, id)
    val mipmapped = requestMipmaps(gl, redBmp)
    setLinearClampParams(gl, mipmapped)
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, redBmp, 0)
    redBmp.recycle()
    return id
}

/**
 * Asks GL to build the mipmap chain for the texture currently bound to `GL_TEXTURE_2D` as part of
 * the upload that follows, returning whether it did.
 *
 * Without mipmaps a large disc drawn small — a 1024² Moon at 12 px when zoomed out — samples one
 * texel per pixel and shimmers as it moves (D85). `GL_GENERATE_MIPMAP` is a GL ES 1.1 feature and
 * is undefined for non-power-of-two textures, so both are checked; a texture that fails either
 * check stays single-level and keeps plain `GL_LINEAR` minification.
 */
private fun requestMipmaps(
    gl: GL10,
    bmp: Bitmap,
): Boolean {
    if (gl !is GL11 || !isPowerOfTwo(bmp.width) || !isPowerOfTwo(bmp.height)) return false
    gl.glTexParameterf(
        GL10.GL_TEXTURE_2D,
        GL11.GL_GENERATE_MIPMAP,
        GL10.GL_TRUE.toFloat(),
    )
    return true
}

/**
 * Estimated GPU bytes for [bmp] uploaded as RGBA_8888, including the mipmap chain (+1/3) when
 * [requestMipmaps] would generate one. Drivers pad and compress, so this is a budgeting estimate
 * for [TextureCache], not a measurement.
 */
internal fun textureByteSize(bmp: Bitmap): Long {
    val base = bmp.width.toLong() * bmp.height.toLong() * 4L
    return if (isPowerOfTwo(bmp.width) && isPowerOfTwo(bmp.height)) base * 4 / 3 else base
}

private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

internal fun setLinearClampParams(
    gl: GL10,
    mipmapped: Boolean = false,
) {
    gl.glTexParameterf(
        GL10.GL_TEXTURE_2D,
        GL10.GL_TEXTURE_MIN_FILTER,
        if (mipmapped) GL10.GL_LINEAR_MIPMAP_LINEAR.toFloat() else GL10.GL_LINEAR.toFloat(),
    )
    gl.glTexParameterf(
        GL10.GL_TEXTURE_2D,
        GL10.GL_TEXTURE_MAG_FILTER,
        GL10.GL_LINEAR.toFloat(),
    )
    gl.glTexParameterf(
        GL10.GL_TEXTURE_2D,
        GL10.GL_TEXTURE_WRAP_S,
        GL10.GL_CLAMP_TO_EDGE.toFloat(),
    )
    gl.glTexParameterf(
        GL10.GL_TEXTURE_2D,
        GL10.GL_TEXTURE_WRAP_T,
        GL10.GL_CLAMP_TO_EDGE.toFloat(),
    )
}

internal fun genTexture(gl: GL10): Int {
    val ids = IntArray(1)
    gl.glGenTextures(1, ids, 0)
    return ids[0]
}

internal fun deleteTextures(
    gl: GL10,
    ids: IntArray,
) {
    if (ids.isNotEmpty()) gl.glDeleteTextures(ids.size, ids, 0)
}
