/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** Bytes in a `float`, for stride and offset arithmetic. */
const val FLOAT_BYTES = 4

/** Bytes in a `short`, for index-buffer arithmetic. */
const val SHORT_BYTES = 2

/** A native-order direct [FloatBuffer] of [size] floats, ready for a GL upload. */
fun directFloatBuffer(size: Int): FloatBuffer =
    ByteBuffer
        .allocateDirect(size * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

/** A native-order direct [ShortBuffer] of [size] shorts, ready for a GL upload. */
fun directShortBuffer(size: Int): ShortBuffer =
    ByteBuffer
        .allocateDirect(size * SHORT_BYTES)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()

/**
 * A vertex array object and the buffer objects it owns.
 *
 * The GLES1 backend re-walked client-side arrays every frame; here the data lives on the GPU and
 * a frame is a bind plus a draw. [release] is only for buffers outliving a scene — everything
 * here dies with the EGL context, and `onSurfaceCreated` simply drops the references (G9).
 */
class Mesh(
    val vao: Int,
    private val buffers: IntArray,
    /** Vertices for a non-indexed draw, or indices for an indexed one. */
    val count: Int,
) {
    /**
     * Deletes this mesh's GL objects, telling [gl] first so its bind cache cannot outlive them.
     *
     * The notification is the point: GL reverts the vertex-array binding to zero when the bound
     * one is deleted, and the caller (`cachedMesh`) deletes the old mesh immediately before
     * generating the replacement — which can be handed the same name back. Without telling
     * [gl], its cache would still name the freed VAO, and the replacement's first bind would be
     * skipped as redundant.
     */
    fun release(gl: GlState) {
        gl.onVertexArrayDeleted(vao)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        GLES30.glDeleteBuffers(buffers.size, buffers, 0)
    }

    companion object {
        /** Allocates [n] buffer names in one call. */
        fun genBuffers(n: Int): IntArray {
            val names = IntArray(n)
            GLES30.glGenBuffers(n, names, 0)
            return names
        }

        /** Allocates one vertex array name. */
        fun genVertexArray(): Int {
            val name = IntArray(1)
            GLES30.glGenVertexArrays(1, name, 0)
            return name[0]
        }

        /** Uploads [data]'s first [floats] floats into [buffer] as a static array buffer. */
        fun uploadFloats(
            buffer: Int,
            data: FloatBuffer,
            floats: Int,
            usage: Int = GLES30.GL_STATIC_DRAW,
        ) {
            data.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * FLOAT_BYTES, data, usage)
        }

        /** Uploads [data]'s first [shorts] shorts into [buffer] as a static element buffer. */
        fun uploadIndices(
            buffer: Int,
            data: ShortBuffer,
            shorts: Int,
        ) {
            data.position(0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffer)
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                shorts * SHORT_BYTES,
                data,
                GLES30.GL_STATIC_DRAW,
            )
        }

        /**
         * Declares a float vertex attribute on the currently bound VAO and array buffer.
         * A [location] of -1 (an attribute the compiler removed) is silently ignored.
         */
        fun floatAttrib(
            location: Int,
            components: Int,
            strideFloats: Int,
            offsetFloats: Int,
            divisor: Int = 0,
        ) {
            if (location < 0) return
            GLES30.glEnableVertexAttribArray(location)
            GLES30.glVertexAttribPointer(
                location,
                components,
                GLES30.GL_FLOAT,
                false,
                strideFloats * FLOAT_BYTES,
                offsetFloats * FLOAT_BYTES,
            )
            if (divisor != 0) GLES30.glVertexAttribDivisor(location, divisor)
        }
    }
}
