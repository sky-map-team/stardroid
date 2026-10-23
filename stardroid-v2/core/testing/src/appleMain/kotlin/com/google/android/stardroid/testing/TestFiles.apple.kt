/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testing

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

actual fun environmentVariable(name: String): String? =
    NSProcessInfo.processInfo.environment[name] as String?

@OptIn(ExperimentalForeignApi::class)
actual fun readTextFile(path: String): String =
    NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        ?: error("could not read $path as UTF-8")
