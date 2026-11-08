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
    private let flags: FirebaseRemoteConfigAdapter
    private let tipStore = StoreKitTipStore()
    @State private var flagsReady = false

    init() {
        // After the analytics adapter, which configures Firebase.
        flags = FirebaseRemoteConfigAdapter()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if flagsReady {
                    RootView(analytics: analytics, tipStore: tipStore, flags: flags)
                } else {
                    // The launch screen's navy, until the stored flags can be read.
                    Color("LaunchNavy").onAppear {
                        flags.prepare {
                            flagsReady = true
                            // Stored for the next launch: the flags are frozen for this one.
                            flags.fetch(ignoreThrottle: false) { _, _ in }
                        }
                    }
                }
            }
            .ignoresSafeArea()
        }
    }
}

private struct RootView: UIViewControllerRepresentable {
    let analytics: SkyMapKit.Analytics
    let tipStore: TipStore
    let flags: SkyMapKit.FlagSource

    func makeUIViewController(context: Context) -> UIViewController {
        SkyMapScreenKt.skyMapViewController(analytics: analytics, tipStore: tipStore, flags: flags)
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
