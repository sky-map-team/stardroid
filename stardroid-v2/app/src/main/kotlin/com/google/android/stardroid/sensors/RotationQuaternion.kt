/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.sensors

import com.google.android.stardroid.astronomy.orientationFromSensors
import com.google.android.stardroid.math.Matrix3
import com.google.android.stardroid.math.Vector3
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Converts an orthonormal, right-handed rotation matrix to a unit quaternion, written into
 * [out] as `(x, y, z, w)` — the form `TYPE_ROTATION_VECTOR` and [OneEuroQuaternionSmoother] both
 * speak.
 *
 * This and [toRotationMatrix3] live here rather than in `core/math` because the `Float` array
 * layout is dictated by the sensor API, not by the `Double`-precision matrix algebra
 * `core/math` otherwise deals in; the legacy accelerometer+magnetometer path is the only
 * caller. They exist so that path can smooth the *orientation* `orientationFromSensors`
 * produces rather than the two raw vectors feeding it — see [SensorOrientationSource]
 * (issue #1007).
 *
 * Uses Shepperd's method: pick whichever of the four components the trace shows to be largest
 * and derive the rest from it, so the square root is never taken of a near-zero quantity.
 */
internal fun Matrix3.writeQuaternion(out: FloatArray): FloatArray {
    val trace = xx + yy + zz
    when {
        trace > 0 -> {
            val s = sqrt(trace + 1.0) * 2
            out[0] = ((zy - yz) / s).toFloat()
            out[1] = ((xz - zx) / s).toFloat()
            out[2] = ((yx - xy) / s).toFloat()
            out[3] = (0.25 * s).toFloat()
        }
        xx > yy && xx > zz -> {
            val s = sqrt(1.0 + xx - yy - zz) * 2
            out[0] = (0.25 * s).toFloat()
            out[1] = ((xy + yx) / s).toFloat()
            out[2] = ((xz + zx) / s).toFloat()
            out[3] = ((zy - yz) / s).toFloat()
        }
        yy > zz -> {
            val s = sqrt(1.0 + yy - xx - zz) * 2
            out[0] = ((xy + yx) / s).toFloat()
            out[1] = (0.25 * s).toFloat()
            out[2] = ((yz + zy) / s).toFloat()
            out[3] = ((xz - zx) / s).toFloat()
        }
        else -> {
            val s = sqrt(1.0 + zz - xx - yy) * 2
            out[0] = ((xz + zx) / s).toFloat()
            out[1] = ((yz + zy) / s).toFloat()
            out[2] = (0.25 * s).toFloat()
            out[3] = ((yx - xy) / s).toFloat()
        }
    }
    return out
}

/** Converts a unit quaternion `(x, y, z, w)` back to the equivalent rotation matrix. */
internal fun FloatArray.toRotationMatrix3(): Matrix3 {
    val x = this[0].toDouble()
    val y = this[1].toDouble()
    val z = this[2].toDouble()
    val w = this[3].toDouble()
    return Matrix3(
        1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
        2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
        2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y),
    )
}

/**
 * Whether this `(x, y, z, w)` sample is close enough to unit length to be a rotation. Rejects
 * NaN, infinities and the all-zero sample some sensor stacks emit before they have a fix — any
 * of which, let through, would leave the map pointing nowhere (issue #1109).
 */
internal fun FloatArray.isUsableQuaternion(): Boolean {
    val norm2 = this[0] * this[0] + this[1] * this[1] + this[2] * this[2] + this[3] * this[3]
    // A NaN norm fails both comparisons, so this one range check also covers non-finite input.
    return norm2 in MIN_USABLE_NORM2..MAX_USABLE_NORM2
}

/**
 * The fused path's per-event step: a `TYPE_ROTATION_VECTOR` sample's [values] as a usable
 * `(x, y, z, w)` quaternion in [out], or `null` if the sample is not a rotation.
 *
 * Some devices (e.g. Galaxy S4) report more than four values, others only three (no scalar
 * component); the scalar is derived where it's missing.
 */
internal fun rotationVectorQuaternion(
    values: FloatArray,
    out: FloatArray,
): FloatArray? {
    out[0] = values[0]
    out[1] = values[1]
    out[2] = values[2]
    out[3] =
        if (values.size >= 4) {
            values[3]
        } else {
            sqrt(max(0f, 1f - (out[0] * out[0] + out[1] * out[1] + out[2] * out[2])))
        }
    return out.takeIf { it.isUsableQuaternion() }
}

/**
 * The legacy path's per-event step: the natural-frame orientation fused from one
 * accelerometer and magnetometer reading, with its quaternion written to [quaternionOut], or
 * `null` if the readings are degenerate or don't make a usable rotation.
 */
internal fun accelMagOrientation(
    acceleration: Vector3,
    magneticField: Vector3,
    quaternionOut: FloatArray,
): Matrix3? {
    val fused = orientationFromSensors(acceleration, magneticField) ?: return null
    return fused.takeIf { it.writeQuaternion(quaternionOut).isUsableQuaternion() }
}

private const val MIN_USABLE_NORM2 = 0.5f
private const val MAX_USABLE_NORM2 = 1.5f
