/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Settings through the overflow menu: the rows iOS acts on (and none of Android's classic-sensor,
/// analytics or diagnostics rows), a choice that sticks, and the edge swipe back to the map.
final class SettingsTests: XCTestCase {
    func testSettingsRowsChoiceAndSwipeBack() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        openFromMenu(app, "Settings")
        XCTAssertTrue(app.staticTexts["Show info on tap"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Smooth sensor movement"].exists)
        XCTAssertFalse(app.staticTexts["Use legacy sensors"].exists)
        capture(self, "1-settings")
        // Compose exposes only the rows on screen, so the end of the list is checked from there:
        // its last row is View direction, with no Other section after it.
        app.swipeUp()
        app.swipeUp()
        XCTAssertTrue(app.staticTexts["View direction"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Use magnetic correction"].exists)
        XCTAssertFalse(app.staticTexts["Send usage statistics"].exists)
        XCTAssertFalse(app.staticTexts["Diagnostics"].exists)
        XCTAssertFalse(app.staticTexts["Other"].exists)
        capture(self, "1b-settings-end")
        app.swipeDown()
        app.swipeDown()
        // A tap during the fling only stops it, as Compose's scrolling does.
        sleep(2)

        // A choice: the label size, from its radio dialog. The row reads the stored setting, so
        // its new value has been written and read back. (Not reopened from the map: there, a
        // simulator's location dialog may cover the menu.)
        app.staticTexts["Sky label size"].tap()
        let large = app.descendants(matching: .any)["Large"].firstMatch
        XCTAssertTrue(large.waitForExistence(timeout: 5))
        capture(self, "2-choice")
        large.tap()
        XCTAssertTrue(app.staticTexts["Large"].waitForExistence(timeout: 5))
        capture(self, "3-kept")

        // Put it back, then leave by the edge swipe, as from any iOS page.
        app.staticTexts["Large"].tap()
        let medium = app.descendants(matching: .any)["Medium"].firstMatch
        XCTAssertTrue(medium.waitForExistence(timeout: 5))
        medium.tap()
        XCTAssertTrue(app.staticTexts["Medium"].waitForExistence(timeout: 5))
        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        edge.press(
            forDuration: 0.05,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)),
        )
        waitUntilGone(app.staticTexts["Show info on tap"])
    }

    private func openFromMenu(_ app: XCUIApplication, _ row: String) {
        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let entry = app.descendants(matching: .any)[row].firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 5))
        entry.tap()
    }

    // Back on the map. Not "its controls are reachable": a location dialog may be up there (a
    // simulator has no fix), which hides them from accessibility.
    private func waitUntilGone(_ element: XCUIElement) {
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: element)
        waitForExpectations(timeout: 5)
    }
}
