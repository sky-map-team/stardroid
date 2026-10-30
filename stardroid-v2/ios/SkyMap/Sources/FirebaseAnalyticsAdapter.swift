/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import FirebaseAnalytics
import FirebaseCore
import SkyMapKit

/// The shared `Analytics` edge over Firebase, as Android's gms `FirebaseAnalyticsAdapter` is.
/// Without a bundled GoogleService-Info.plist (it is not checked in) Firebase stays unconfigured
/// and every call is a no-op, so the app still builds and runs for anyone without the file.
final class FirebaseAnalyticsAdapter: NSObject, SkyMapKit.Analytics {
    private let configured: Bool

    override init() {
        configured = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
        if configured {
            FirebaseApp.configure()
        }
        super.init()
    }

    func setEnabled(enabled: Bool) {
        guard configured else { return }
        FirebaseAnalytics.Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    func trackEvent(event: String, params: [String: Any]) {
        guard configured else { return }
        FirebaseAnalytics.Analytics.logEvent(event, parameters: params)
    }

    func setUserProperty(name: String, value: String) {
        guard configured else { return }
        FirebaseAnalytics.Analytics.setUserProperty(value, forName: name)
    }
}
