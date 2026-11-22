/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.stardroid.share.encodePng
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

/**
 * Writes [image] to a temporary PNG and presents the share sheet with it and [text]. The file
 * goes when the share is over. False if it couldn't be written or presented.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun shareImage(
    image: ImageBitmap,
    text: String,
): Boolean {
    val file = withContext(Dispatchers.IO) { writePng(image) } ?: return false
    val top = topViewController() ?: return false
    val sheet = UIActivityViewController(listOf(file, text), null)
    sheet.completionWithItemsHandler = { activity, completed, _, _ ->
        // Backing out of one activity returns to the sheet, which still needs the file.
        if (completed || activity == null) {
            NSFileManager.defaultManager.removeItemAtURL(file, null)
        }
    }
    // An iPad shows the sheet as a popover, which needs an anchor: the shutter's end of the screen.
    sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = top.view
        val (width, height) = top.view.bounds.useContents { size.width to size.height }
        popover.sourceRect = CGRectMake(width / 2, height - 1, 1.0, 1.0)
    }
    top.presentViewController(sheet, animated = true, completion = null)
    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun writePng(image: ImageBitmap): NSURL? {
    val png = image.encodePng() ?: return null
    val directory = NSTemporaryDirectory().trimEnd('/') + "/share"
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    // One well-known name: an abandoned share is overwritten by the next, not piled up.
    val url = NSURL.fileURLWithPath("$directory/sky_map.png")
    val data = png.usePinned { NSData.dataWithBytes(it.addressOf(0), png.size.toULong()) }
    return url.takeIf { data.writeToURL(it, atomically = true) }
}
