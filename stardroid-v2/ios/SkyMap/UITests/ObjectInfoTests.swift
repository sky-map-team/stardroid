/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Tap-to-identify: search centres Mars (the simulator is in manual mode), then a tap on the
/// centre of the sky opens its shared object card, and its photo opens full screen.
final class ObjectInfoTests: XCTestCase {
    func testTapMarsForItsCard() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let search = app.buttons["Search"]
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.typeText("Mars")
        sleep(1)
        app.buttons["Go"].tap()
        sleep(3)
        app.buttons["Cancel"].tap()
        sleep(1)

        app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let close = app.buttons["Close"]
        XCTAssertTrue(close.waitForExistence(timeout: 5), "tapping Mars opens its card")
        sleep(1)
        capture(self, "1-card")

        // Mars's photo heads the card's body; Compose doesn't expose it as an image element,
        // so it is tapped where it sits.
        app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.31)).tap()
        sleep(2)
        capture(self, "2-photo")
    }
}
