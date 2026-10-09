/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Compass calibration, which an iPhone reaches from Diagnostics rather than the overflow menu:
/// the user's form of the screen (no opt-out), its animation running, and OK back to Diagnostics.
/// A simulator has no compass, so the readout says so.
final class CalibrationTests: XCTestCase {
    func testCalibrationFromDiagnostics() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.descendants(matching: .any)["Settings"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        XCTAssertFalse(app.descendants(matching: .any)["Calibrate"].firstMatch.exists)
        settings.tap()
        XCTAssertTrue(app.staticTexts["Show info on tap"].waitForExistence(timeout: 5))
        app.swipeUp()
        app.swipeUp()
        // A tap during the fling only stops it, as Compose's scrolling does.
        sleep(2)
        let row = app.staticTexts["Diagnostics"]
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()

        XCTAssertTrue(app.staticTexts["General"].waitForExistence(timeout: 5))
        // Short drags, not swipes: a fling carries the button past the top of the screen.
        let calibrate = app.buttons["Calibrate"]
        for _ in 0..<6 where !(calibrate.exists && calibrate.isHittable) {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
                .press(
                    forDuration: 0.1,
                    thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)),
                )
            sleep(1)
        }
        XCTAssertTrue(calibrate.isHittable)
        capture(self, "0-diagnostics")
        calibrate.tap()

        let heading = app.staticTexts["Your Phone's Compass"]
        XCTAssertTrue(heading.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Absent"].exists)
        XCTAssertFalse(app.staticTexts["Do not show this again"].exists)
        // Two moments of the figure-eight, to see it move.
        sleep(1)
        capture(self, "1-calibration")
        usleep(700_000)
        capture(self, "2-calibration-later")

        // OK returns to Diagnostics, which opened it.
        app.buttons["OK"].tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: heading)
        waitForExpectations(timeout: 5)
        XCTAssertTrue(calibrate.waitForExistence(timeout: 5))
    }
}
