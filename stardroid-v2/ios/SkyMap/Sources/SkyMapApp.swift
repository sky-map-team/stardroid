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
    var body: some Scene {
        WindowGroup {
            RootView().ignoresSafeArea()
        }
    }
}

private struct RootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SkyMapScreenKt.skyMapViewController()
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
