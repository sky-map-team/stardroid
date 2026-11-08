/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// The site, not a store page, as on Android: it links both stores.
internal const val SHARE_LINK = "https://getskymap.app"

// Apple's write-a-review page for Open Sky Map (App Store ID 6813336040).
private const val WRITE_REVIEW_URL = "https://apps.apple.com/app/id6813336040?action=write-review"

internal fun openReviewPage() {
    val url = NSURL.URLWithString(WRITE_REVIEW_URL) ?: return
    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
}
