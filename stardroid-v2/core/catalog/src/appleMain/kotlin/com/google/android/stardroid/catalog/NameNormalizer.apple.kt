/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.catalog

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

// Foundation's NFD. Kotlin/Native bridges String to NSString, so the cast is a view, not a copy.
@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decomposeCanonically(text: String): String =
    (text as NSString).decomposedStringWithCanonicalMapping
