/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import java.io.File
import java.io.IOException

/** [TextFiles] as plain files in [directory], which is created on first write. */
class DirectoryTextFiles(
    private val directory: File,
) : TextFiles {
    override fun read(name: String): String? =
        File(directory, name).takeIf { it.isFile }?.readText()

    /**
     * Written to a temporary file and renamed into place. A failed rename deletes the temporary
     * file and leaves the previous text where it was.
     *
     * @throws IOException if the text could not be persisted: a full or failing filesystem.
     */
    @Throws(IOException::class)
    override fun replace(
        name: String,
        text: String,
    ) {
        directory.mkdirs()
        val target = File(directory, name)
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not replace $target")
        }
    }
}
