/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Compass calibration from the overflow menu: the user's form of the screen (no opt-out), its
/// animation running, and OK back to the map. A simulator has no compass, so the readout says so.
final class CalibrationTests: XCTestCase {
    func testCalibrationFromMenu() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let entry = app.descendants(matching: .any)["Calibrate"].firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 5))
        entry.tap()

        let heading = app.staticTexts["Your Phone's Compass"]
        XCTAssertTrue(heading.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Absent"].exists)
        XCTAssertFalse(app.staticTexts["Do not show this again"].exists)
        // Two moments of the figure-eight, to see it move.
        sleep(1)
        capture(self, "1-calibration")
        usleep(700_000)
        capture(self, "2-calibration-later")

        app.buttons["OK"].tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: heading)
        // Not "the menu is reachable": a location dialog may be up there (a simulator has no
        // fix), which hides it from accessibility.
        waitForExpectations(timeout: 5)
    }
}
