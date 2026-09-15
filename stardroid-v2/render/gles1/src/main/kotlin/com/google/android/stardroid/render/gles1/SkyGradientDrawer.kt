/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The dome mesh in +y-toward-the-sun coordinates: latitude bands of vertices with per-vertex
 * colors, stitched into triangles by [indices]. Positions and colors never change; only the
 * per-frame rotation to the actual sun direction does.
 */
internal class SkyGradientBuffers(
    val vertices: FloatBuffer,
    val colors: FloatBuffer,
    val indices: ShortBuffer,
    val indexCount: Int,
)

/**
 * Draws `RenderState.skyGradient`'s dome — the port of v1's `SkyBox`: a unit sphere around the
 * eye whose cap toward the sun is daylight blue, shading through grey at 90° from the sun to
 * black opposite it. Geometry is built once ([build]); each frame rotates the dome's +y axis to
 * the sun's celestial direction on the modelview stack, exactly as v1 did.
 *
 * The caller skips this drawer entirely in night mode (v1 `SkyBox.drawInternal` returned early);
 * there is no night-transformed variant of the dome.
 */
internal object SkyGradientDrawer {
    /** v1 `SkyBox.NUM_VERTEX_BANDS`. */
    private const val NUM_VERTEX_BANDS = 8

    /** v1 `SkyBox.NUM_STEPS_IN_BAND`. */
    private const val NUM_STEPS_IN_BAND = 10

    /** v1's guard against band iteration landing exactly on -1 and rounding past it. */
    private const val EPSILON = 1e-3f

    // The sun moves ~1°/day, so the rotation is cached and only recomputed when the direction
    // actually changes, sparing onDrawFrame's per-frame acos/cross-product work.
    private var lastSunDirection: Vector3? = null
    private var cachedAngleDeg = 0f
    private var cachedAxis = Vector3.UNIT_X

    /**
     * Vertex positions (xyz triples) and colors (rgba quadruples) for the dome, sun along +y.
     *
     * Port of the v1 `SkyBox` constructor. Colors (v1 packed these as ABGR ints): bands facing
     * the sun are blue with intensity 70 at the sun falling to 50 at 90°; bands beyond 90° are
     * grey with intensity 40 falling to 0 opposite the sun.
     */
    fun buildGeometry(): Pair<FloatArray, FloatArray> {
        val numVertices = NUM_VERTEX_BANDS * NUM_STEPS_IN_BAND
        val positions = FloatArray(numVertices * 3)
        val colors = FloatArray(numVertices * 4)

        val sinAngles = FloatArray(NUM_STEPS_IN_BAND)
        val cosAngles = FloatArray(NUM_STEPS_IN_BAND)
        val dAngle = 2.0 * Math.PI / (NUM_STEPS_IN_BAND - 1)
        for (i in 0 until NUM_STEPS_IN_BAND) {
            sinAngles[i] = sin(i * dAngle).toFloat()
            cosAngles[i] = cos(i * dAngle).toFloat()
        }

        val bandStep = 2.0f / (NUM_VERTEX_BANDS - 1) + EPSILON
        var bandPos = 1f
        var p = 0
        var c = 0
        for (band in 0 until NUM_VERTEX_BANDS) {
            val (r, g, b) =
                if (bandPos > 0) {
                    // Above the sun's equator: blue sky, I=70 at the sun, I=50 at 90° from it.
                    val intensity = (bandPos * 20 + 50) / 255f
                    Triple(0f, 0f, intensity)
                } else {
                    // Below: grey to black, I=40 at 90° from the sun, I=0 opposite it.
                    val intensity = ((bandPos * 40 + 40) / 255f).coerceAtLeast(0f)
                    Triple(intensity, intensity, intensity)
                }
            val sinPhi = if (bandPos > -1) sqrt(1 - bandPos * bandPos) else 0f
            for (i in 0 until NUM_STEPS_IN_BAND) {
                positions[p++] = cosAngles[i] * sinPhi
                positions[p++] = bandPos
                positions[p++] = sinAngles[i] * sinPhi
                colors[c++] = r
                colors[c++] = g
                colors[c++] = b
                colors[c++] = 1f
            }
            bandPos -= bandStep
        }
        return Pair(positions, colors)
    }

    /** Triangle indices stitching consecutive bands into quads, v1's winding (front face CW). */
    fun buildIndices(): ShortArray {
        val indices = ShortArray((NUM_VERTEX_BANDS - 1) * NUM_STEPS_IN_BAND * 6)
        var n = 0
        var topBandStart = 0
        var bottomBandStart = NUM_STEPS_IN_BAND
        repeat(NUM_VERTEX_BANDS - 1) {
            for (offsetFromStart in 0 until NUM_STEPS_IN_BAND - 1) {
                val topLeft = topBandStart + offsetFromStart
                val topRight = topLeft + 1
                val bottomLeft = bottomBandStart + offsetFromStart
                val bottomRight = bottomLeft + 1

                indices[n++] = topLeft.toShort()
                indices[n++] = bottomRight.toShort()
                indices[n++] = bottomLeft.toShort()

                indices[n++] = topRight.toShort()
                indices[n++] = bottomRight.toShort()
                indices[n++] = topLeft.toShort()
            }

            // Last quad: connect the band's end back to its beginning.
            indices[n++] = (topBandStart + NUM_STEPS_IN_BAND - 1).toShort()
            indices[n++] = bottomBandStart.toShort()
            indices[n++] = (bottomBandStart + NUM_STEPS_IN_BAND - 1).toShort()

            indices[n++] = topBandStart.toShort()
            indices[n++] = bottomBandStart.toShort()
            indices[n++] = (topBandStart + NUM_STEPS_IN_BAND - 1).toShort()

            topBandStart += NUM_STEPS_IN_BAND
            bottomBandStart += NUM_STEPS_IN_BAND
        }
        return indices
    }

    /**
     * The rotation carrying the dome's +y axis onto [sunDirection] as a `glRotatef`-shaped
     * (angle°, axis) pair. The axis degenerates when the sun is (anti)parallel to +y; any
     * perpendicular axis serves for the 180° flip, and the 0° identity ignores its axis.
     */
    fun rotationToSun(sunDirection: Vector3): Pair<Float, Vector3> {
        val axis = (Vector3.UNIT_Y cross sunDirection).normalized()
        val angleDeg = RADIANS_TO_DEGREES * acos(sunDirection.y.coerceIn(-1.0, 1.0))
        return Pair(angleDeg.toFloat(), if (axis == Vector3.ZERO) Vector3.UNIT_X else axis)
    }

    fun build(): SkyGradientBuffers {
        val (positions, colors) = buildGeometry()
        val indices = buildIndices()
        val vertexBuffer = directFloatBuffer(positions.size).put(positions)
        val colorBuffer = directFloatBuffer(colors.size).put(colors)
        val indexBuffer = directShortBuffer(indices.size).put(indices)
        vertexBuffer.rewind()
        colorBuffer.rewind()
        indexBuffer.rewind()
        return SkyGradientBuffers(vertexBuffer, colorBuffer, indexBuffer, indices.size)
    }

    fun draw(
        gl: GL10,
        buffers: SkyGradientBuffers,
        sunDirection: Vector3,
    ) {
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buffers.vertices)
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, buffers.colors)

        // Cull the dome's far side (v1): the eye is inside the sphere, so without culling the
        // back half would overdraw the front with reversed winding.
        gl.glEnable(GL10.GL_CULL_FACE)
        gl.glFrontFace(GL10.GL_CW)
        gl.glCullFace(GL10.GL_BACK)

        if (sunDirection != lastSunDirection) {
            lastSunDirection = sunDirection
            val (angleDeg, axis) = rotationToSun(sunDirection)
            cachedAngleDeg = angleDeg
            cachedAxis = axis
        }

        gl.glPushMatrix()
        gl.glRotatef(
            cachedAngleDeg,
            cachedAxis.x.toFloat(),
            cachedAxis.y.toFloat(),
            cachedAxis.z.toFloat(),
        )
        gl.glDrawElements(
            GL10.GL_TRIANGLES,
            buffers.indexCount,
            GL10.GL_UNSIGNED_SHORT,
            buffers.indices,
        )
        gl.glPopMatrix()

        gl.glDisable(GL10.GL_CULL_FACE)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
