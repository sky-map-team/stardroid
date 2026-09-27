/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import org.robovm.apple.foundation.NSAutoreleasePool
import org.robovm.apple.uikit.UIApplication
import org.robovm.apple.uikit.UIApplicationDelegateAdapter
import org.robovm.apple.uikit.UIApplicationLaunchOptions
import org.robovm.apple.uikit.UIScreen
import org.robovm.apple.uikit.UIWindow

/** The iOS entry point: RoboVM starts here (robovm.xml `mainClass`) and hands over to UIKit. */
class SkyMapApp : UIApplicationDelegateAdapter() {
    override fun didFinishLaunching(
        application: UIApplication,
        launchOptions: UIApplicationLaunchOptions?,
    ): Boolean {
        val window = UIWindow(UIScreen.getMainScreen().bounds)
        window.rootViewController = SkyViewController()
        window.makeKeyAndVisible()
        setWindow(window)
        return true
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val pool = NSAutoreleasePool()
            UIApplication.main<UIApplication, SkyMapApp>(args, null, SkyMapApp::class.java)
            pool.close()
        }
    }
}
