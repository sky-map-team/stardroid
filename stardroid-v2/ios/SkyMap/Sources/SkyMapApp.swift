/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import SkyMapKit
import SwiftUI

/// Sky Map for iOS. The app is Kotlin (app-ios, over the shared modules); Swift only hosts its
/// root view controller, edge to edge.
@main
struct SkyMapApp: App {
    private let analytics = FirebaseAnalyticsAdapter()
    private let tipStore = StoreKitTipStore()

    var body: some Scene {
        WindowGroup {
            RootView(analytics: analytics, tipStore: tipStore).ignoresSafeArea()
        }
    }
}

private struct RootView: UIViewControllerRepresentable {
    let analytics: SkyMapKit.Analytics
    let tipStore: TipStore

    func makeUIViewController(context: Context) -> UIViewController {
        SkyMapScreenKt.skyMapViewController(analytics: analytics, tipStore: tipStore)
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
