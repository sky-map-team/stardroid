/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// The location sheet through the overflow menu: manual entry by place name (Apple's geocoder,
/// so the simulator needs the network), the "Location set to" snackbar, and back to automatic,
/// captured as test attachments.
final class LocationTests: XCTestCase {
    func testManualEntryByPlaceAndBack() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)

        openLocationSheet(app)
        capture(self, "1-sheet")
        let manual = app.buttons["Enter Location Manually"]
        let change = app.buttons["Change Location"]
        XCTAssertTrue(manual.waitForExistence(timeout: 5) || change.exists)
        (manual.exists ? manual : change).tap()

        // Compose text fields are text views to accessibility, labelled by their floating label.
        let place = app.textViews.containing(.staticText, identifier: "City or place name")
            .firstMatch
        XCTAssertTrue(place.waitForExistence(timeout: 5))
        place.tap()
        typeSlowly(place, "Paris")
        capture(self, "2-entry")
        // The magnifier resolves the name into the coordinate fields without applying it. Compose
        // doesn't expose a field's text to accessibility, so the fields are checked by eye.
        app.buttons["Resolve"].tap()
        sleep(3)
        capture(self, "2b-resolved")
        app.buttons["Set Location"].tap()
        let toast = app.staticTexts["Location set to Paris"]
        XCTAssertTrue(toast.waitForExistence(timeout: 15), "the geocoded place is announced")
        capture(self, "3-set")

        openLocationSheet(app)
        let auto = app.buttons["Use Automatic Location"]
        XCTAssertTrue(auto.waitForExistence(timeout: 5))
        capture(self, "4-manual-sheet")
        auto.tap()
        sleep(2)
        capture(self, "5-auto")
    }

    private func openLocationSheet(_ app: XCUIApplication) {
        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tap()
        let location = app.descendants(matching: .any)["Location"].firstMatch
        XCTAssertTrue(location.waitForExistence(timeout: 5))
        location.tap()
        XCTAssertTrue(app.staticTexts["Your Location"].waitForExistence(timeout: 5))
        sleep(1)
    }
}
