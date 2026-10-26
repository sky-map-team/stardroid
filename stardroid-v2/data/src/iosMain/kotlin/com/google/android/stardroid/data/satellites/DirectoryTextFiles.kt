/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** [TextFiles] as plain files in [directory] (a path), which is created on first write. */
@OptIn(ExperimentalForeignApi::class)
class DirectoryTextFiles(
    private val directory: String,
) : TextFiles {
    /** Null for a missing file, and for one that cannot be read as UTF-8 text (a directory, say). */
    override fun read(name: String): String? =
        NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null)

    /**
     * Foundation's atomic write: to an auxiliary file, then renamed into place, so a failure
     * leaves the previous text where it was.
     */
    override fun replace(
        name: String,
        text: String,
    ) {
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) {
            "Could not create $directory"
        }
        @Suppress("CAST_NEVER_SUCCEEDS")
        val written = (text as NSString).writeToFile(path(name), true, NSUTF8StringEncoding, null)
        check(written) { "Could not replace ${path(name)}" }
    }

    private fun path(name: String) = "$directory/$name"
}
