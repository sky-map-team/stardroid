/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// The sky gallery through the overflow menu: the photo grid, a tile's info card, and its Find,
/// which lands on the map with the object searched for.
final class GalleryTests: XCTestCase {
    func testGalleryCardAndFind() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let entry = app.descendants(matching: .any)["Sky gallery"].firstMatch
        XCTAssertTrue(entry.waitForExistence(timeout: 5))
        entry.tap()

        let tile = app.staticTexts["Aldebaran"]
        XCTAssertTrue(tile.waitForExistence(timeout: 10))
        // Let the thumbnails decode.
        sleep(2)
        capture(self, "1-grid")
        tile.tap()
        let find = app.buttons["Find in sky"]
        XCTAssertTrue(find.waitForExistence(timeout: 5))
        capture(self, "2-card")

        find.tap()
        // Back on the map, searching. Not "its search bar is reachable": a location dialog may
        // be up there (a simulator has no fix), which hides it from accessibility.
        expectation(
            for: NSPredicate(format: "exists == false"),
            evaluatedWith: app.staticTexts["Sky gallery"],
        )
        waitForExpectations(timeout: 5)
        sleep(2)
        capture(self, "3-found")
    }
}
