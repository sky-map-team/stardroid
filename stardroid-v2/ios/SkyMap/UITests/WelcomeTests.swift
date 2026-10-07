/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// The warm welcome, replayed from the overflow menu (a first run shows the same screen, which
/// passStartup skips): the chrome tour, the sample info card and the sensor check, captured as
/// test attachments.
final class WelcomeTests: XCTestCase {
    func testTutorialReplay() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let tutorial = app.descendants(matching: .any)["Tutorial"].firstMatch
        XCTAssertTrue(tutorial.waitForExistence(timeout: 5))
        tutorial.tap()

        XCTAssertTrue(app.staticTexts["Explore the Cosmos"].waitForExistence(timeout: 5))
        sleep(4)
        capture(self, "1-tour")
        app.buttons["Next"].tap()
        XCTAssertTrue(app.staticTexts["Navigate the Sky"].waitForExistence(timeout: 5))
        sleep(1)
        capture(self, "2-card")
        app.buttons["Next"].tap()
        XCTAssertTrue(app.staticTexts["Sensors & Calibration"].waitForExistence(timeout: 5))
        // The check reveals one sensor every 0.8 s.
        sleep(4)
        capture(self, "3-sensors")
        app.buttons["Launch!"].tap()
        // Back to the map. Not "the map's controls are reachable": a location dialog may be up
        // there (a simulator has no fix), which hides them from accessibility.
        expectation(
            for: NSPredicate(format: "exists == false"),
            evaluatedWith: app.staticTexts["Sensors & Calibration"],
        )
        waitForExpectations(timeout: 5)
    }
}
