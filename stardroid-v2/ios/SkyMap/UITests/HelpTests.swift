/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Help and What's New through the overflow menu: the document, its search, and the release
/// notes and credits, captured as test attachments.
final class HelpTests: XCTestCase {
    func testHelpSearchAndWhatsNew() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        openFromMenu(app, "Help")
        // The search box is a Compose text field: a text view labelled by its placeholder.
        let search = app.textViews.containing(.staticText, identifier: "Search help").firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        capture(self, "1-help")
        search.tap()
        search.typeText("symbols")
        XCTAssertTrue(app.staticTexts["Map symbols"].waitForExistence(timeout: 5))
        sleep(1)
        capture(self, "2-search")
        app.buttons["Back"].firstMatch.tap()

        openFromMenu(app, "What's new")
        XCTAssertTrue(app.staticTexts["What's New & Credits"].waitForExistence(timeout: 5))
        sleep(1)
        capture(self, "3-whats-new")
        app.buttons["Back"].firstMatch.tap()
        // Back on the map. Not "its controls are reachable": a location dialog may be up there
        // (a simulator has no fix), which hides them from accessibility.
        expectation(
            for: NSPredicate(format: "exists == false"),
            evaluatedWith: app.staticTexts["What's New & Credits"],
        )
        waitForExpectations(timeout: 5)
    }

    private func openFromMenu(_ app: XCUIApplication, _ row: String) {
        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let entry = app.descendants(matching: .any)[row].firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 5))
        entry.tap()
    }
}
