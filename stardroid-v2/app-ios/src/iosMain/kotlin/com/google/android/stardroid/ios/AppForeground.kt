/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Whether the app is in the foreground: what Android's activity `onStart`/`onStop` and the
 * process's STARTED state tell the app, read from UIKit's notifications. Lives as long as the
 * app, so the observers are never removed.
 */
class AppForeground {
    private val _isForeground =
        MutableStateFlow(
            UIApplication.sharedApplication.applicationState !=
                UIApplicationState.UIApplicationStateBackground,
        )

    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, queue) {
            _isForeground.value = true
        }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) {
            _isForeground.value = false
        }
    }
}
