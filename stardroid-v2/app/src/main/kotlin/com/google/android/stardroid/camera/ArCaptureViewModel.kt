/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.stardroid.share.SkyShare

/**
 * Through-camera state that must outlive an activity recreation (rotation). [SkyCameraPreview]
 * is owned by the activity, so anything the user chose or produced there is kept here instead.
 */
class ArCaptureViewModel : ViewModel() {
    var stillMode by mutableStateOf(SkyCameraPreview.StillMode.AUTO)

    /** The composites awaiting a layout pick; held so a rotation doesn't drop the sheet. */
    var shareLayouts by mutableStateOf<SkyShare.ShareLayouts?>(null)
}
