/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Diagnostics through Settings, as on Android: the iOS rows, the report going to the share sheet
/// (a simulator has no Mail), and back to Settings, then the map.
final class DiagnosticsTests: XCTestCase {
    func testDiagnosticsFromSettingsAndShare() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let settings = app.descendants(matching: .any)["Settings"].firstMatch
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
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
        XCTAssertTrue(app.staticTexts["iOS version"].exists)
        XCTAssertTrue(app.staticTexts["Metal"].exists)
        capture(self, "1-diagnostics")
        app.swipeUp()
        sleep(1)
        capture(self, "2-sensors")
        app.swipeUp()
        sleep(1)
        capture(self, "3-location-network")

        app.buttons["Send to developers"].tap()
        let copy = app.descendants(matching: .any)["Copy"].firstMatch
        XCTAssertTrue(copy.waitForExistence(timeout: 10))
        capture(self, "4-share-sheet")
        app.swipeDown(velocity: .fast)
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: copy)
        waitForExpectations(timeout: 10)

        // Back returns to Settings, which opened it, and from there to the map.
        app.buttons["Back"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Show info on tap"].waitForExistence(timeout: 5))
        app.buttons["Back"].firstMatch.tap()
        expectation(
            for: NSPredicate(format: "exists == false"),
            evaluatedWith: app.staticTexts["Show info on tap"],
        )
        waitForExpectations(timeout: 5)
    }
}
