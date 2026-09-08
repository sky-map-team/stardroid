/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.RotationSmoothingLevel
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sin

/**
 * Spherical-linear-interpolation smoothing of the fused rotation-vector quaternion (issues
 * [#963](https://github.com/sky-map-team/stardroid/issues/963) /
 * [#1001](https://github.com/sky-map-team/stardroid/issues/1001)): some phones' `TYPE_ROTATION_VECTOR`
 * fusion is noisy enough to make labels hard to read even holding the phone still. Operates on
 * the raw quaternion — `TYPE_ROTATION_VECTOR`'s `event.values` already *are* one — rather than
 * the derived rotation matrix, since SLERP is the geometrically correct way to interpolate along
 * the rotation manifold.
 *
 * Each sample moves the smoothed quaternion a fraction [alpha] of the way toward the raw sample
 * (lower = more smoothing, more lag), except samples within [deadbandRadians] of the current
 * smoothed value, which are ignored outright — the "hold steady through micro-movements" half of
 * the fix.
 */
class QuaternionSlerpSmoother(private val alpha: Float, private val deadbandRadians: Float) {
    private var current: FloatArray? = null

    /** [raw] is a unit quaternion as `(x, y, z, w)`. Returns the smoothed quaternion, same form. */
    fun update(raw: FloatArray): FloatArray {
        val prev = current
        if (prev == null) {
            // Start at the first sample rather than decaying in from zero — see
            // ExponentiallyWeightedSmoother for the same reasoning. Returning the caller's own
            // [raw] array (rather than a copy) is only safe because the caller consumes it
            // synchronously before the next event can mutate the shared scratch buffer behind
            // it — don't buffer/retain this return value across events.
            current = raw.copyOf()
            return raw
        }

        // Quaternions double-cover rotations: q and -q represent the same rotation, but SLERPing
        // toward the "wrong" sign spins the long way around. Flip raw if it's closer to -prev.
        val dot = dot(prev, raw)
        val target = if (dot < 0f) negate(raw) else raw
        val angle = 2f * acos(min(1f, abs(dot)))
        if (angle < deadbandRadians) return prev

        val smoothed = slerp(prev, target, alpha)
        current = smoothed
        return smoothed
    }

    companion object {
        private fun dot(
            a: FloatArray,
            b: FloatArray,
        ): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

        private fun negate(q: FloatArray) = floatArrayOf(-q[0], -q[1], -q[2], -q[3])

        /** Spherical linear interpolation from [from] toward [to] by fraction [t], assuming [to]. */
        private fun slerp(
            from: FloatArray,
            to: FloatArray,
            t: Float,
        ): FloatArray {
            val cosHalfTheta = min(1f, abs(dot(from, to)))
            // Nearly identical inputs: linear interpolation avoids a division by ~0 in the
            // sin(theta) denominator below and is indistinguishable from SLERP at this scale.
            if (cosHalfTheta > 0.9995f) {
                val result = FloatArray(4) { from[it] + (to[it] - from[it]) * t }
                return normalize(result)
            }
            val halfTheta = acos(cosHalfTheta)
            val sinHalfTheta = sin(halfTheta)
            val ratioFrom = sin((1 - t) * halfTheta) / sinHalfTheta
            val ratioTo = sin(t * halfTheta) / sinHalfTheta
            return FloatArray(4) { from[it] * ratioFrom + to[it] * ratioTo }
        }

        private fun normalize(q: FloatArray): FloatArray {
            val length = kotlin.math.sqrt(dot(q, q))
            return FloatArray(4) { q[it] / length }
        }

        /**
         * Low-pass ladder: OFF passes samples through unsmoothed (`alpha = 1`); higher levels
         * move less of the way toward each new sample, i.e. damp harder.
         */
        internal fun alphaFor(level: RotationSmoothingLevel): Float =
            when (level) {
                RotationSmoothingLevel.OFF -> 1f
                RotationSmoothingLevel.LOW -> 0.5f
                RotationSmoothingLevel.MEDIUM -> 0.3f
                RotationSmoothingLevel.HIGH -> 0.15f
            }

        /** Deadband ladder: OFF suppresses nothing; higher levels ignore larger movements. */
        internal fun deadbandRadiansFor(level: RotationSmoothingLevel): Float =
            when (level) {
                RotationSmoothingLevel.OFF -> 0f
                RotationSmoothingLevel.LOW -> Math.toRadians(0.15).toFloat()
                RotationSmoothingLevel.MEDIUM -> Math.toRadians(0.3).toFloat()
                RotationSmoothingLevel.HIGH -> Math.toRadians(0.5).toFloat()
            }
    }
}
