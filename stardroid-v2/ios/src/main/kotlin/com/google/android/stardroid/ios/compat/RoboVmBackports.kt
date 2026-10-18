/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package com.google.android.stardroid.ios.compat

/**
 * Java 8 static methods RoboVM's class library lacks. `RoboVmBackportTransform` (build-logic)
 * rewrites calls to `java.lang.Owner.name` on the iOS runtime classpath into calls to
 * `owner_name` here, with the same descriptor and the same semantics as the JDK.
 */
object RoboVmBackports {
    @JvmStatic
    fun boolean_hashCode(value: Boolean): Int = if (value) 1231 else 1237

    @JvmStatic
    fun math_addExact(
        x: Long,
        y: Long,
    ): Long {
        val r = x + y
        // Overflow iff both operands have the opposite sign of the result (Math.addExact).
        if ((x xor r) and (y xor r) < 0) throw ArithmeticException("long overflow")
        return r
    }

    @JvmStatic
    fun math_multiplyExact(
        x: Long,
        y: Long,
    ): Long {
        val r = x * y
        val ax = kotlin.math.abs(x)
        val ay = kotlin.math.abs(y)
        // The JDK's own check: only large operands can overflow; verify by division.
        if ((ax or ay) ushr 31 != 0L) {
            if ((y != 0L && r / y != x) || (x == Long.MIN_VALUE && y == -1L)) {
                throw ArithmeticException("long overflow")
            }
        }
        return r
    }
}
