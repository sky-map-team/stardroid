/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** A native-order direct [FloatBuffer] sized for [floatCapacity] floats, ready to `put` into. */
internal fun directFloatBuffer(floatCapacity: Int): FloatBuffer =
    ByteBuffer.allocateDirect(floatCapacity * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

/** A native-order direct [ShortBuffer] sized for [shortCapacity] shorts, ready to `put` into. */
internal fun directShortBuffer(shortCapacity: Int): ShortBuffer =
    ByteBuffer.allocateDirect(shortCapacity * Short.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
