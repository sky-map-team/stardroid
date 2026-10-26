/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import SkyMapHarness
import SwiftUI

/// Hosts the Kotlin renderer harness full screen. Everything else — the Metal view, the draw
/// loop, the gestures, the test scene — is Kotlin (render/metal-harness).
@main
struct RendererHarnessApp: App {
    var body: some Scene {
        WindowGroup {
            HarnessView().ignoresSafeArea()
        }
    }
}

private struct HarnessView: UIViewControllerRepresentable {
    // The coordinator keeps the harness alive for as long as the view is shown: the Metal view's
    // delegate and the gesture targets are weak references, held by the harness.
    func makeCoordinator() -> RendererHarness { RendererHarness() }

    func makeUIViewController(context: Context) -> UIViewController {
        context.coordinator.viewController
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}
