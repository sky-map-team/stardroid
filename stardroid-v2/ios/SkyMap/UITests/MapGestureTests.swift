/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Drives the map's gestures and captures the sky after each, as test attachments
/// (`xcrun xcresulttool export attachments`): launch, a drag that ends in a fling, and a pinch.
final class MapGestureTests: XCTestCase {
    func testDragFlingAndPinch() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)
        sleep(6)
        capture(self, "1-launch")

        let window = app.windows.firstMatch
        let from = window.coordinate(withNormalizedOffset: CGVector(dx: 0.7, dy: 0.5))
        let to = window.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.5))
        from.press(forDuration: 0.05, thenDragTo: to, withVelocity: .fast, thenHoldForDuration: 0)
        sleep(2)
        capture(self, "2-after-drag-and-fling")

        window.pinch(withScale: 2.5, velocity: 2.0)
        sleep(2)
        capture(self, "3-after-pinch")
    }
}
