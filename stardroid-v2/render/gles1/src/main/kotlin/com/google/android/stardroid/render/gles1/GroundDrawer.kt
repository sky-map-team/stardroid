/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.Ground
import com.google.android.stardroid.render.api.GroundRamp
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Client-side arrays for the ground shell: one ring of vertices per [GroundDrawer.RING_ALTITUDES]. */
internal class GroundBuffers(
    val vertices: FloatBuffer,
    val colors: FloatBuffer,
    val indices: ShortBuffer,
    val indexCount: Int,
) {
    companion object {
        val EMPTY =
            GroundBuffers(
                directFloatBuffer(0),
                directFloatBuffer(0),
                directShortBuffer(0),
                indexCount = 0,
            )
    }
}

/**
 * Draws [Ground] as a Gouraud-shaded shell over the lower hemisphere — GLES1's realisation of a
 * render-state block whose GLES3 counterpart is a fragment shader, exactly as the two backends
 * already realise `SkyGradient` differently (an eight-band mesh here, an analytic model there).
 *
 * This replaces the additive eight-ring horizon glow, which `HorizonLayer` used to submit as a
 * `GlowPrimitive`. That glow was doing two jobs — marking the horizon and faking a sense of depth
 * — and its ring count existed purely to trace an exponential with piecewise-linear alpha
 * interpolation. Splitting the jobs leaves the horizon layer with a plain line plus its cardinal
 * labels, and puts all the shading here. The primitive type, its ring type, the `glows` slot in
 * `LayerScene` and both backends' glow drawers all went away with it; `HorizonLayer` was the only
 * producer of glows in the codebase.
 *
 * Two things differ from the glow beyond the ramp itself, and both follow from it being ground
 * rather than light:
 *
 * It blends **normally** rather than additively. Additive blending cannot darken, which is fine
 * for a glow and useless for something meant to obscure what is behind it.
 *
 * It therefore has to draw **after** the objects it occludes, at [LayerScene.GROUND_DEPTH],
 * instead of at the front of a layer's own primitives where the glow sat.
 */
internal object GroundDrawer {
    /**
     * The altitudes, in degrees below the horizon, at which the ramp is sampled.
     *
     * Spaced tightly near the horizon and loosely below it because that is where
     * [GroundRamp.depthProfile]'s exponential actually moves: with a seven-degree scale it has
     * lost most of its range in the first fifteen degrees and is nearly flat by the nadir.
     * Sampling uniformly instead would need four times the rings for the same fidelity.
     *
     * The first two entries deserve note: a ring exactly at the horizon has zero coverage and one
     * just below it has full coverage, so the pair reproduces the edge in a single narrow band.
     * That is as close to antialiasing as the fixed pipeline gets here, and it is why the boundary
     * lands on the horizon line rather than adrift of it.
     */
    val RING_ALTITUDES =
        doubleArrayOf(
            0.0,
            -GroundRamp.EDGE_RAMP_DEG,
            -1.0,
            -2.0,
            -3.5,
            -5.0,
            -7.0,
            -10.0,
            -14.0,
            -20.0,
            -28.0,
            -40.0,
            -55.0,
            -72.0,
            -90.0,
        )

    /** Vertices per ring; the last repeats the first so the loop closes exactly. */
    private const val NUM_SEGMENTS = 32

    private val COS_ANGLES =
        DoubleArray(NUM_SEGMENTS + 1) { cos((it % NUM_SEGMENTS) * 2.0 * Math.PI / NUM_SEGMENTS) }
    private val SIN_ANGLES =
        DoubleArray(NUM_SEGMENTS + 1) { sin((it % NUM_SEGMENTS) * 2.0 * Math.PI / NUM_SEGMENTS) }

    /**
     * Builds the shell around [zenith], shaded for a Sun at [sunDirection].
     *
     * Only the zenith is needed to place the geometry, even though the rings are circles that need
     * two axes to parameterise: a full circle is the same set of points whichever perpendicular
     * basis traces it, so any basis will do and the vertices merely start at a different place
     * along the loop. That is why the ground needs nothing from the producer that `SkyGradient`
     * was not already carrying.
     */
    fun build(
        ground: Ground,
        sunDirection: Vector3,
        zenith: Vector3,
    ): GroundBuffers {
        if (ground.opacity <= 0.0) return GroundBuffers.EMPTY
        val up = zenith.normalized()
        val nadir = up * -1.0
        val (axisA, axisB) = horizonBasis(up)
        val sunAltitudeDeg =
            asin((sunDirection.normalized() dot up).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES

        val numRings = RING_ALTITUDES.size
        val ringLength = NUM_SEGMENTS + 1
        val numVertices = numRings * ringLength
        val numQuads = (numRings - 1) * (ringLength - 1)

        val vertexBuffer = directFloatBuffer(numVertices * 3)
        val colorBuffer = directFloatBuffer(numVertices * 4)
        val indexBuffer = directShortBuffer(numQuads * 6)

        for (ringIdx in 0 until numRings) {
            val altitudeDeg = RING_ALTITUDES[ringIdx]
            val tilt = -altitudeDeg * DEGREES_TO_RADIANS
            val cosTilt = cos(tilt)
            val sinTilt = sin(tilt)
            val alpha =
                GroundRamp
                    .alpha(altitudeDeg, sunAltitudeDeg, ground.opacity)
                    .toFloat()
            for (i in 0..NUM_SEGMENTS) {
                val fa = COS_ANGLES[i] * cosTilt
                val fb = SIN_ANGLES[i] * cosTilt
                vertexBuffer
                    .put((axisA.x * fa + axisB.x * fb + nadir.x * sinTilt).toFloat())
                    .put((axisA.y * fa + axisB.y * fb + nadir.y * sinTilt).toFloat())
                    .put((axisA.z * fa + axisB.z * fb + nadir.z * sinTilt).toFloat())
                colorBuffer
                    .put(ground.color.r)
                    .put(ground.color.g)
                    .put(ground.color.b)
                    .put(alpha)
            }
        }

        for (ring in 0 until numRings - 1) {
            val topRowStart = ring * ringLength
            val bottomRowStart = topRowStart + ringLength
            for (i in 0 until ringLength - 1) {
                val topLeft = topRowStart + i
                val topRight = topLeft + 1
                val bottomLeft = bottomRowStart + i
                val bottomRight = bottomLeft + 1
                indexBuffer.put(topLeft.toShort())
                indexBuffer.put(bottomLeft.toShort())
                indexBuffer.put(bottomRight.toShort())
                indexBuffer.put(topLeft.toShort())
                indexBuffer.put(bottomRight.toShort())
                indexBuffer.put(topRight.toShort())
            }
        }

        vertexBuffer.rewind()
        colorBuffer.rewind()
        indexBuffer.rewind()
        return GroundBuffers(vertexBuffer, colorBuffer, indexBuffer, indexCount = numQuads * 6)
    }

    /**
     * Any two orthonormal vectors perpendicular to [up]. The seed axis is chosen to be the one
     * least aligned with [up], so the cross product never degenerates.
     */
    private fun horizonBasis(up: Vector3): Pair<Vector3, Vector3> {
        val seed =
            if (kotlin.math.abs(up.z) < 0.9) Vector3.UNIT_Z else Vector3.UNIT_X
        val axisA = (seed cross up).normalized()
        return Pair(axisA, (up cross axisA).normalized())
    }

    fun draw(
        gl: GL10,
        buffers: GroundBuffers,
    ) {
        if (buffers.indexCount == 0) return
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, buffers.vertices)
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, buffers.colors)
        // The gradient depends on Gouraud interpolation of the per-ring alphas across each band.
        gl.glShadeModel(GL10.GL_SMOOTH)
        gl.glDrawElements(
            GL10.GL_TRIANGLES,
            buffers.indexCount,
            GL10.GL_UNSIGNED_SHORT,
            buffers.indices,
        )
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
    }
}
