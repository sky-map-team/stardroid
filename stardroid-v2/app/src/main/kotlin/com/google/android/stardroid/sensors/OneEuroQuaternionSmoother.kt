/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.settings.OneEuroEaseOff
import com.google.android.stardroid.settings.OneEuroSteadiness
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A [1€ filter](https://gery.casiez.net/1euro/) (Casiez, Roussel & Vogel, CHI 2012) over the
 * orientation quaternion — an exponential low-pass whose cutoff frequency rises with how fast
 * the signal is actually moving. Held still, it filters hard and the view stops shaking; swung
 * about, it barely filters at all and the view keeps up.
 *
 * This replaces `QuaternionSlerpSmoother`, which offered a flat low-pass plus a hard angular
 * deadband on the fused path and an `alpha · angle^n` law on the legacy one (issue #1007). Both
 * were approximations of what this does properly:
 *
 *  - A flat low-pass can't tell jitter from movement, so it lags both equally. The deadband
 *    existed to paper over the resulting lag, at the cost of a discontinuity — below the
 *    threshold nothing moves at all, so a slow deliberate pan is ignored until the accumulated
 *    error crosses it and then jumps.
 *  - The `angle^n` law is continuous, but keys its gain off the instantaneous angle to the raw
 *    sample, which conflates real speed with noise magnitude: a single large noise spike opens
 *    the gate and lets itself through. Here the speed estimate is itself low-passed
 *    ([DERIVATIVE_CUTOFF_HZ]), so one spike doesn't.
 *
 * It is also formulated in real elapsed time rather than per sample, so its behaviour no longer
 * depends on the sensor's delivery rate.
 *
 * Tune in the order the paper prescribes: with [beta] at its lowest, lower [minCutoff] until
 * the view is still enough at rest; then raise [beta] until it keeps up when you move.
 */
class OneEuroQuaternionSmoother(
    private val minCutoff: Float,
    private val beta: Float,
    private val speedFloor: Float = 0f,
) {
    private var current: FloatArray? = null
    private var previousRaw: FloatArray? = null

    /**
     * The low-passed angular velocity, as a 3-vector in radians/second. A *vector*, not a
     * speed: sensor noise pushes it in a different direction each sample and so averages
     * toward zero, where the magnitude of each sample's rotation never can — it is always
     * positive, so low-passing it converges on the average size of the noise rather than on
     * zero. Getting that wrong makes the filter read a noisy-but-stationary phone as moving
     * fast, open the cutoff, and pass the noise straight through.
     */
    private val smoothedVelocity = FloatArray(3)
    private var lastTimestampNanos = 0L
    private var lastDtSeconds = 0f

    /**
     * Feeds one sample. [raw] is a unit quaternion as `(x, y, z, w)`; [timestampNanos] is the
     * sensor event's own timestamp. Returns the smoothed quaternion, same form.
     */
    fun update(
        raw: FloatArray,
        timestampNanos: Long,
    ): FloatArray {
        val prev = current
        val elapsed = (timestampNanos - lastTimestampNanos) / NANOS_PER_SECOND
        // A non-advancing timestamp must not restart the filter. Restarting snaps straight to
        // the unfiltered sample, so anything that makes it happen repeatedly reads as the view
        // shaking — and callers can hand over timestamps that stall or step backwards for
        // reasons that have nothing to do with the phone moving. Carry on with the last known
        // interval instead; it is only ever used as a divisor.
        val dtSeconds = if (elapsed > 0f) elapsed else lastDtSeconds
        // Restart on the first sample, and on a gap long enough to mean the app was
        // backgrounded — resuming from a stale orientation there would drag the view across
        // the sky, where starting fresh costs one frame of smoothing.
        if (prev == null || dtSeconds <= 0f || elapsed > MAX_GAP_SECONDS) {
            current = raw.copyOf()
            previousRaw = raw.copyOf()
            lastTimestampNanos = timestampNanos
            lastDtSeconds = 0f
            smoothedVelocity.fill(0f)
            // Safe to hand back the caller's own array only because it's consumed synchronously
            // before the next event can overwrite the shared scratch buffer behind it.
            return raw
        }
        lastTimestampNanos = timestampNanos
        lastDtSeconds = dtSeconds

        // Quaternions double-cover rotations: q and -q are the same rotation, but SLERPing
        // toward the "wrong" sign spins the long way around. Flip raw if it's closer to -prev.
        val dot = dot(prev, raw)
        val target = if (dot < 0f) negate(raw) else raw

        // Steer the cutoff by how fast the *raw* signal is actually turning, low-passed
        // component by component so noise cancels rather than accumulating (see
        // [smoothedVelocity]).
        val velocityFactor = smoothingFactor(DERIVATIVE_CUTOFF_HZ, dtSeconds)
        angularVelocity(previousRaw ?: raw, raw, dtSeconds, velocityScratch)
        for (axis in 0..2) {
            smoothedVelocity[axis] +=
                velocityFactor * (velocityScratch[axis] - smoothedVelocity[axis])
        }
        previousRaw = raw.copyOf()

        val speed =
            sqrt(
                smoothedVelocity[0] * smoothedVelocity[0] +
                    smoothedVelocity[1] * smoothedVelocity[1] +
                    smoothedVelocity[2] * smoothedVelocity[2],
            )
        // Subtract the noise floor before the speed is allowed to open the cutoff. Averaging
        // the velocity as a vector shrinks what noise leaves behind but cannot remove it: over
        // any finite window the residual wanders, and whenever it happens to align for a few
        // samples the cutoff opens, shakes, then closes as it decorrelates — a periodic
        // shake-freeze-shake that is worse to look at than steady jitter. Below the floor the
        // filter is simply a fixed low-pass at [minCutoff]; above it, genuine movement still
        // opens the cutoff on the very first sample, with no averaging delay.
        val cutoff = minCutoff + beta * max(0f, speed - speedFloor)
        val smoothed = slerp(prev, target, smoothingFactor(cutoff, dtSeconds))
        current = smoothed
        return smoothed
    }

    private val velocityScratch = FloatArray(3)

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000f

        /** Beyond this the app was almost certainly backgrounded; restart rather than catch up. */
        private const val MAX_GAP_SECONDS = 1f

        /**
         * Cutoff for the angular-speed estimate itself. The paper's default of 1 Hz, which it
         * notes rarely needs changing — it only has to reject single-sample noise without
         * delaying the filter's reaction to genuine movement.
         */
        private const val DERIVATIVE_CUTOFF_HZ = 1f

        /**
         * Writes the angular velocity carrying [from] to [to] over [dtSeconds] into [out], as
         * an axis-angle 3-vector in radians/second.
         *
         * Uses the small-angle form of the quaternion log map: for `dq = to ⊗ conj(from)` with
         * a positive scalar part, the rotation vector is `2 · dq.xyz` to well within a
         * rounding error at the fraction of a degree a single sample covers.
         */
        private fun angularVelocity(
            from: FloatArray,
            to: FloatArray,
            dtSeconds: Float,
            out: FloatArray,
        ) {
            // to ⊗ conj(from)
            var dx = -to[3] * from[0] + to[0] * from[3] - to[1] * from[2] + to[2] * from[1]
            var dy = -to[3] * from[1] + to[1] * from[3] - to[2] * from[0] + to[0] * from[2]
            var dz = -to[3] * from[2] + to[2] * from[3] - to[0] * from[1] + to[1] * from[0]
            val dw = to[3] * from[3] + to[0] * from[0] + to[1] * from[1] + to[2] * from[2]
            // Take the shorter of the two equivalent rotations, so a sign flip in the source
            // quaternions doesn't read as a near-360-degree lurch.
            if (dw < 0f) {
                dx = -dx
                dy = -dy
                dz = -dz
            }
            out[0] = 2f * dx / dtSeconds
            out[1] = 2f * dy / dtSeconds
            out[2] = 2f * dz / dtSeconds
        }

        /** The exponential-smoothing factor for a given [cutoffHz] over [dtSeconds]. */
        private fun smoothingFactor(
            cutoffHz: Float,
            dtSeconds: Float,
        ): Float {
            val tau = 1f / (2f * PI.toFloat() * cutoffHz)
            return 1f / (1f + tau / dtSeconds)
        }

        private fun dot(
            a: FloatArray,
            b: FloatArray,
        ): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

        private fun negate(q: FloatArray) = floatArrayOf(-q[0], -q[1], -q[2], -q[3])

        /** Spherical linear interpolation from [from] toward [to] by fraction [t]. */
        private fun slerp(
            from: FloatArray,
            to: FloatArray,
            t: Float,
        ): FloatArray {
            val cosHalfTheta = min(1f, abs(dot(from, to)))
            // Nearly identical inputs: linear interpolation avoids a division by ~0 in the
            // sin(theta) denominator below and is indistinguishable from SLERP at this scale.
            if (cosHalfTheta > 0.9995f) {
                return normalize(FloatArray(4) { from[it] + (to[it] - from[it]) * t })
            }
            val halfTheta = acos(cosHalfTheta)
            val sinHalfTheta = sin(halfTheta)
            val ratioFrom = sin((1 - t) * halfTheta) / sinHalfTheta
            val ratioTo = sin(t * halfTheta) / sinHalfTheta
            return FloatArray(4) { from[it] * ratioFrom + to[it] * ratioTo }
        }

        private fun normalize(q: FloatArray): FloatArray {
            val length = sqrt(dot(q, q))
            return FloatArray(4) { q[it] / length }
        }

        /**
         * Cutoff-at-rest ladder, in Hz. Steadier means a *lower* cutoff, so the ladder runs
         * downward — hence naming the setting for steadiness rather than for the frequency.
         *
         * Two tables, because the two sensor paths differ by orders of magnitude in how noisy
         * their orientation is (issue #1007). The fused sensor arrives already filtered by the
         * platform; the legacy path is raw accelerometer and magnetometer put through a
         * vector rejection, and its noise floor is far coarser. One shared table meant every
         * rung was either useless on one path or useless on the other.
         *
         * Provisional on both paths — these want field tuning, which is why steadiness stays
         * its own setting for now rather than folded in with [betaFor].
         */
        internal fun minCutoffFor(
            level: OneEuroSteadiness,
            legacyPath: Boolean,
        ): Float =
            if (legacyPath) {
                when (level) {
                    OneEuroSteadiness.LOW -> 0.6f
                    OneEuroSteadiness.MEDIUM -> 0.3f
                    OneEuroSteadiness.HIGH -> 0.1f
                    OneEuroSteadiness.VERY_HIGH -> 0.05f
                    OneEuroSteadiness.EXTREME -> 0.02f
                    OneEuroSteadiness.MAXIMUM -> 0.005f
                }
            } else {
                when (level) {
                    OneEuroSteadiness.LOW -> 2.5f
                    OneEuroSteadiness.MEDIUM -> 1.2f
                    OneEuroSteadiness.HIGH -> 0.6f
                    OneEuroSteadiness.VERY_HIGH -> 0.3f
                    OneEuroSteadiness.EXTREME -> 0.05f
                    OneEuroSteadiness.MAXIMUM -> 0.01f
                }
            }

        /**
         * The residual speed, in radians/second, that a *stationary* phone's sensor noise
         * leaves in the velocity estimate on each path — everything below this is treated as
         * standing still. The legacy path's figure is far larger because its orientation noise
         * is: a degree or two per sample at 50 Hz is already tens of degrees per second.
         *
         * Provisional, and the legacy figure is the one most worth measuring: set it too low
         * and the shake-freeze cycle survives, too high and slow deliberate pans are treated
         * as noise and lag badly.
         */
        internal fun speedFloorFor(legacyPath: Boolean): Float = if (legacyPath) 0.35f else 0.03f

        /**
         * Ease-off ladder, in Hz per radian/second: how fast the cutoff climbs as the phone
         * moves, trading stillness for keeping up. `NONE` never relaxes, leaving a plain
         * low-pass — the paper's starting point for tuning [minCutoffFor] on its own.
         *
         * The legacy table is an order of magnitude smaller, and that matters more than the
         * cutoff table does. Its noise leaves a residual speed estimate behind however
         * carefully that's computed, and `beta · speed` is *added* to the cutoff — so too
         * large a beta swamps the minimum entirely and drags the cutoff back up, making the
         * steadiness setting arithmetically irrelevant no matter how far down it goes.
         */
        internal fun betaFor(
            level: OneEuroEaseOff,
            legacyPath: Boolean,
        ): Float =
            if (legacyPath) {
                when (level) {
                    OneEuroEaseOff.NONE -> 0f
                    OneEuroEaseOff.LOW -> 0.02f
                    OneEuroEaseOff.MEDIUM -> 0.1f
                    OneEuroEaseOff.HIGH -> 0.4f
                }
            } else {
                when (level) {
                    OneEuroEaseOff.NONE -> 0f
                    OneEuroEaseOff.LOW -> 0.2f
                    OneEuroEaseOff.MEDIUM -> 1f
                    OneEuroEaseOff.HIGH -> 4f
                }
            }
    }
}
