/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Time travel through the shared screens: the dialog, a popular event, the trip (flash, notice
/// and player), and the way home, captured as test attachments.
final class TimeTravelTests: XCTestCase {
    func testTravelToSunsetAndBack() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let open = app.buttons["Time travel"]
        XCTAssertTrue(open.waitForExistence(timeout: 10))
        open.tap()
        // The event picker is a read-only text field: a text view to accessibility.
        let picker = app.textViews["Select a popular event…"]
        XCTAssertTrue(picker.waitForExistence(timeout: 5))
        picker.tap()
        let sunset = app.descendants(matching: .any)["Next evening sunset"].firstMatch
        XCTAssertTrue(sunset.waitForExistence(timeout: 5))
        sunset.tap()
        sleep(1)
        capture(self, "1-dialog")

        app.buttons["Go"].tap()
        sleep(1)
        capture(self, "2-travelling")
        sleep(3)
        capture(self, "3-arrived")

        let home = app.buttons["Now"]
        XCTAssertTrue(home.waitForExistence(timeout: 5))
        home.tap()
        sleep(3)
        capture(self, "4-home")
        XCTAssertFalse(home.exists, "the player hides once home")
    }
}
