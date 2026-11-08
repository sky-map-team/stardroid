/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import kotlin.experimental.ExperimentalNativeApi

/**
 * Keeps the last uncaught Kotlin exception's stack trace in [directory]/last-crash.txt (and on
 * stdout) before the runtime aborts. An iOS crash report shows only the abort, not which Kotlin
 * exception caused it, so without this a crash that won't reproduce leaves nothing to go on.
 * Copy it off a device with
 * `devicectl device copy from --domain-type appDataContainer --domain-identifier <bundle id>`.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class, BetaInteropApi::class)
fun recordUncaughtExceptions(directory: String) {
    val previous = getUnhandledExceptionHook()
    setUnhandledExceptionHook { throwable ->
        val trace = throwable.stackTraceToString()
        println("Uncaught: $trace")
        @Suppress("CAST_NEVER_SUCCEEDS")
        (trace as NSString).writeToFile(
            "$directory/last-crash.txt",
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        previous?.invoke(throwable)
    }
}
