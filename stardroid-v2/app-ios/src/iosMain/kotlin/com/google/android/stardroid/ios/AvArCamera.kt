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
import com.google.android.stardroid.ui.map.ArCameraSpecs
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/**
 * The through-camera mode's iOS edge (camera-ar-launch.md §4.2). Each bind gets its own
 * [CameraSession]; they share one serial queue, so one has stopped before the next starts.
 */
internal class AvArCamera : IosArCamera {
    // The virtual multi-lens camera where there is one, so zoom reaches the ultra-wide and the
    // system switches lens; otherwise the plain wide camera.
    private val device: AVCaptureDevice? by lazy {
        listOf(
            AVCaptureDeviceTypeBuiltInTripleCamera,
            AVCaptureDeviceTypeBuiltInDualWideCamera,
            AVCaptureDeviceTypeBuiltInWideAngleCamera,
        ).firstNotNullOfOrNull { type ->
            // One at a time: the discovery session's own order doesn't promise the preference.
            AVCaptureDeviceDiscoverySession
                .discoverySessionWithDeviceTypes(
                    listOf(type),
                    AVMediaTypeVideo,
                    AVCaptureDevicePositionBack,
                ).devices
                .firstOrNull() as? AVCaptureDevice
        }
    }

    private val queue = dispatch_queue_create("io.penterakt.skymap.camera", null)
    private var session: CameraSession? = null

    override val hasCamera: Boolean get() = device != null

    override val isAuthorized: Boolean
        get() =
            AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
                AVAuthorizationStatusAuthorized

    override fun requestAccess(onResult: (granted: Boolean) -> Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        // iOS asks only once; after that only Settings can change the answer.
        if (status != AVAuthorizationStatusNotDetermined) {
            onResult(status == AVAuthorizationStatusAuthorized)
            return
        }
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
        }
    }

    override fun bind(
        target: ArPreviewTarget,
        onPreviewLuma: (Double) -> Unit,
        onReady: (ArCameraSpecs) -> Unit,
    ) {
        unbind()
        val device = device ?: return
        session = CameraSession(device, queue, target, onPreviewLuma, onReady).also { it.open() }
    }

    override fun unbind() {
        session?.close()
        session = null
    }

    override fun setActive(active: Boolean) {
        session?.setActive(active)
    }

    override fun layoutChanged() {
        session?.layoutChanged()
    }

    override fun setZoomRatio(ratio: Double) {
        session?.setZoomRatio(ratio)
    }

    override fun setExposureIndex(index: Int) {
        session?.setExposureIndex(index)
    }

    override suspend fun takeStill(): ImageBitmap? = session?.takeStill()
}
