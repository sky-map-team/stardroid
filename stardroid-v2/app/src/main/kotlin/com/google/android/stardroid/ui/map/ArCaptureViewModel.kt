/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.stardroid.camera.SkyCameraPreview
import com.google.android.stardroid.share.ShareLayouts

/** State that must survive activity recreation; [SkyCameraPreview] itself does not. */
class ArCaptureViewModel : ViewModel() {
    var stillMode by mutableStateOf(SkyCameraPreview.StillMode.AUTO)

    // Never recycled here: the layout sheet may still be drawing them (see SkyShare).
    var shareLayouts by mutableStateOf<ShareLayouts?>(null)
}
