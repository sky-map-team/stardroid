/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer

/** Allocates one GL texture name. Must be called on the GL thread. */
fun genTexture(): Int {
    val name = IntArray(1)
    GLES30.glGenTextures(1, name, 0)
    return name[0]
}

/**
 * Deletes [names], ignoring zeroes, telling [gl] first so its bind cache cannot outlive them.
 * Must be called on the GL thread. See [GlState.onTexturesDeleted] for why the notification
 * matters.
 */
fun deleteTextures(
    gl: GlState,
    names: IntArray,
) {
    val live = names.filter { it != 0 }.toIntArray()
    if (live.isEmpty()) return
    gl.onTexturesDeleted(live)
    GLES30.glDeleteTextures(live.size, live, 0)
}

/** Linear filtering, clamped at the edges — what a photograph or a planet disc wants. */
fun setLinearClampParams() {
    setTextureParams(GLES30.GL_LINEAR, GLES30.GL_LINEAR)
}

/** Nearest filtering, clamped — what text wants, so glyphs stay crisp at 1:1. */
fun setNearestClampParams() {
    setTextureParams(GLES30.GL_NEAREST, GLES30.GL_NEAREST)
}

private fun setTextureParams(
    minFilter: Int,
    magFilter: Int,
) {
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, minFilter)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, magFilter)
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_2D,
        GLES30.GL_TEXTURE_WRAP_S,
        GLES30.GL_CLAMP_TO_EDGE,
    )
    GLES30.glTexParameteri(
        GLES30.GL_TEXTURE_2D,
        GLES30.GL_TEXTURE_WRAP_T,
        GLES30.GL_CLAMP_TO_EDGE,
    )
}

/**
 * Uploads [bitmap] as an RGBA texture and returns its name.
 *
 * Unlike GLES1 there is no second, red-shifted copy: night mode is a colour transform in every
 * fragment shader, so one upload serves both modes. That halves image texture memory and removes
 * the whole night-variant branch from the cache.
 */
fun uploadRgbaTexture(bitmap: Bitmap): Int {
    val name = genTexture()
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name)
    setLinearClampParams()
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
    return name
}

/**
 * Uploads an 8-bit coverage mask as a single-channel `R8` texture and returns its name.
 *
 * The label atlas is a coverage mask, not colour — GLES1 stores it as ARGB_8888 only because
 * GLES1 has no single-channel format to store it in. `R8` is core in GL ES 3.0 and costs a
 * quarter as much, which is what makes a catalog-scale label set affordable.
 */
fun uploadR8Texture(
    widthPx: Int,
    heightPx: Int,
    pixels: ByteBuffer,
): Int {
    val name = genTexture()
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, name)
    setNearestClampParams()
    // Rows are byte-aligned, not word-aligned: an R8 row of, say, 1023 px would otherwise be
    // read with 4-byte row padding that is not there, skewing every row after the first.
    GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
    pixels.position(0)
    GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        0,
        GLES30.GL_R8,
        widthPx,
        heightPx,
        0,
        GLES30.GL_RED,
        GLES30.GL_UNSIGNED_BYTE,
        pixels,
    )
    GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
    return name
}

/** A rough byte cost for budgeting: four bytes per pixel, as uploaded. */
fun textureByteSize(bitmap: Bitmap): Long = bitmap.width.toLong() * bitmap.height.toLong() * 4L
