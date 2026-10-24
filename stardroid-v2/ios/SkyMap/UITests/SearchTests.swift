/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// Search through the shared screens: the dialog, a result, then the overlay that guides the
/// way to it and its control bar, captured as test attachments.
final class SearchTests: XCTestCase {
    func testSearchForMars() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let search = app.buttons["Search"]
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        // Compose Multiplatform's text field reaches accessibility as a text view, focused.
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        typeSlowly(field, "Mars")
        sleep(2)
        capture(self, "1-search-dialog")

        app.buttons["Go"].tap()
        sleep(3)
        capture(self, "2-search-target")

        let cancel = app.buttons["Cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5))
        cancel.tap()
        sleep(1)
        XCTAssertTrue(search.waitForExistence(timeout: 5), "the chrome comes back after a search")
    }
}
