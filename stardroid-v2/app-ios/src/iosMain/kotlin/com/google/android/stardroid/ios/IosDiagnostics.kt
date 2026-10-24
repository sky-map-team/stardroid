/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.ui.diagnostics.DiagnosticsPlatform
import com.google.android.stardroid.ui.diagnostics.GpsStatus
import com.google.android.stardroid.ui.diagnostics.NetworkStatus
import com.google.android.stardroid.ui.diagnostics.mailtoUrl
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.diagnostics_ios_version
import com.google.android.stardroid.ui.resources.diagnostics_metal_limits
import com.google.android.stardroid.ui.resources.diagnostics_metal_version
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_get_main_queue
import platform.posix.uname
import platform.posix.utsname
import kotlin.concurrent.AtomicReference

/**
 * The device and build facts for the diagnostics screen: the marketing model ("iPhone") with the
 * machine identifier ("iPhone14,5") as Android's model and hardware, and the bundle's version and
 * build number as its versionName and versionCode.
 */
@OptIn(ExperimentalForeignApi::class)
fun iosDiagnosticsPlatform(): DiagnosticsPlatform {
    val machine =
        memScoped {
            val info = alloc<utsname>()
            uname(info.ptr)
            info.machine.toKString()
        }
    val bundle = NSBundle.mainBundle
    return DiagnosticsPlatform(
        model = UIDevice.currentDevice.model,
        hardware = machine,
        osVersionLabel = Res.string.diagnostics_ios_version,
        osVersion = UIDevice.currentDevice.systemVersion,
        appVersionName =
            bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "",
        appVersionCode =
            (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull()
                ?: 0L,
        buildLabel = "iOS",
        graphicsVersionLabel = Res.string.diagnostics_metal_version,
        graphicsLimitsLabel = Res.string.diagnostics_metal_limits,
    )
}

/**
 * Android's GPS row from Location Services' system switch. Apple warns this can block the main
 * thread, and the diagnostics view model polls it off it. The permission has its own row.
 */
fun iosGpsStatus(): GpsStatus =
    if (CLLocationManager.locationServicesEnabled()) GpsStatus.ENABLED else GpsStatus.DISABLED

/**
 * The network as the diagnostics screen reports it, kept current by Network's path monitor:
 * Android asks `ConnectivityManager` at each poll, iOS has only the monitor's updates. It starts
 * on first use and runs for the app's life, which costs a callback per network change.
 */
@OptIn(ExperimentalForeignApi::class)
class NetworkMonitor {
    private val current = AtomicReference(NetworkStatus.DISCONNECTED)

    val status: NetworkStatus get() = current.value

    init {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            current.value =
                when {
                    nw_path_get_status(path) != nw_path_status_satisfied ->
                        NetworkStatus.DISCONNECTED
                    nw_path_uses_interface_type(path, nw_interface_type_wifi) ->
                        NetworkStatus.CONNECTED_WIFI
                    nw_path_uses_interface_type(path, nw_interface_type_cellular) ->
                        NetworkStatus.CONNECTED_CELL
                    else -> NetworkStatus.CONNECTED
                }
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}

/**
 * Android's `DiagnosticsShare` on iOS: a mail draft to [address] with the report as its editable
 * body, so the user reads and sends it themselves, or the share sheet when no mail app takes
 * `mailto:` (the simulator has none).
 */
fun sendDiagnosticsReport(
    address: String,
    subject: String,
    body: String,
) {
    val url = NSURL.URLWithString(mailtoUrl(address, subject, body))
    if (url == null) {
        shareText(body)
        return
    }
    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { opened ->
        if (!opened) shareText(body)
    }
}

private fun shareText(text: String) {
    val top = topViewController() ?: return
    val sheet = UIActivityViewController(listOf(text), null)
    // An iPad shows the sheet as a popover, which needs an anchor.
    sheet.popoverPresentationController?.sourceView = top.view
    top.presentViewController(sheet, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    val window =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
