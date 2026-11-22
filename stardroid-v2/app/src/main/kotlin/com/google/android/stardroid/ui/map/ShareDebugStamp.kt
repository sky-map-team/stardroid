/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.google.android.stardroid.camera.SkyCameraPreview
import com.google.android.stardroid.share.centerCrop

// TODO(ar-debug-stamp): temporary, with the "Debug stamp" switch in the still controls.

/**
 * The text stamped under an AR share image while tuning how the camera still lines up with
 * the drawn sky. Sampled after the still, so it also records how far the phone moved.
 */
internal fun debugStampLines(
    context: Context,
    mapViewModel: MapViewModel,
    arCamera: SkyCameraPreview,
    pointingBefore: ArStampPointing,
    track: List<ArStampPointing>,
    map: Bitmap,
    still: Bitmap,
    appliedScrim: Double,
): List<String> {
    val report = arCamera.lastStillReport ?: return listOf("SKY MAP DEBUG: no still report")
    val region = centerCrop(still.width, still.height, map.width.toDouble() / map.height)
    val version =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    return ArStampFormat.lines(
        before = pointingBefore,
        after = mapViewModel.arStampPointing(),
        map = mapViewModel.arStampMap(map.width, map.height, appliedScrim),
        still = report,
        crop = ArStampCrop(region.width, region.height),
        appVersion = version,
        device = "${Build.MODEL} API${Build.VERSION.SDK_INT}",
        track = track,
    )
}
