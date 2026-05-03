# Feature Specification: Modern Location Handling

**Feature Branch**: `002-modern-location`
**Created**: 2026-05-03
**Status**: Draft
**Input**: User description: "Complete reimplementation of location handling in Sky Map to bring it up to modern standards, with automatic detection, manual override, graceful fallback, and transparent location status."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - New User Permission Flow (Priority: P1)

A user opens Sky Map for the first time. Before the system permission dialog appears, they see a clear rationale explaining why location is needed (to correctly show the night sky above them). They grant coarse location permission. The app immediately acquires their position, shows a brief notification confirming the location, and the star map displays the correct sky for their location.

**Why this priority**: The first-launch experience is the most critical moment for user retention. A confusing or broken permission flow causes users to abandon the app. This story also validates the fundamental auto-location pipeline.

**Independent Test**: Fresh install with no prior permissions. Launch the app. Verify the rationale appears before the system dialog, the map uses the real device location after permission is granted, and a toast confirms the location.

**Acceptance Scenarios**:

1. **Given** a fresh install with no prior location permission, **When** the app needs location for the first time, **Then** a rationale screen explaining the need for location is shown before the system permission dialog.
2. **Given** the rationale is shown and the user grants permission, **When** permission is confirmed, **Then** the app acquires the device location and shows a notification with the detected location name or coordinates.
3. **Given** auto-location is active, **When** the device detects a location change, **Then** the star map updates immediately and a notification confirms the new location.

---

### User Story 2 - Permission Denied — Manual Entry Path (Priority: P1)

A user declines the location permission. Instead of silently showing an incorrect sky, the app immediately presents a clear invitation to enter their location manually. The user can type a place name (resolved to coordinates when online) or type latitude and longitude directly. Once confirmed, the star map shows the correct sky.

**Why this priority**: Silent failure at (0°N, 0°E) is the core defect being fixed. Handling denial gracefully is as critical as the happy path.

**Independent Test**: Deny location permission when prompted. Verify the manual-entry invitation appears immediately with no incorrect sky visible. Enter a known city name (online) and verify correct sky rendering. Reset, then enter raw coordinates offline and verify the same result.

**Acceptance Scenarios**:

1. **Given** the user denies location permission, **When** the denial is registered, **Then** the app immediately presents an invitation to enter location manually rather than silently showing an incorrect sky.
2. **Given** the manual entry screen is open and the device is online, **When** the user types a place name and confirms, **Then** the system resolves it to coordinates, notifies the user of the resolved location, and updates the star map.
3. **Given** the manual entry screen is open and the device is offline, **When** the user types latitude and longitude directly and confirms, **Then** the star map updates to those coordinates without requiring internet.
4. **Given** the user enters a place name that cannot be resolved, **When** resolution fails, **Then** a clear message informs them the place was not found and they are invited to try again or enter coordinates directly.

---

### User Story 3 - No Location Hardware (Priority: P1)

On a device that cannot determine location automatically (e.g., a tablet with no GPS or mobile radio, or with all providers disabled), the app detects this situation and immediately offers manual location entry rather than failing silently.

**Why this priority**: This is another variant of the "no silent failure" requirement and is functionally equivalent in importance to the permission-denied path.

**Independent Test**: Disable all location providers in device settings. Launch the app. Verify the app detects the unavailability and presents the manual entry invitation rather than silently using (0°N, 0°E).

**Acceptance Scenarios**:

1. **Given** a device with no location providers available, **When** the app starts, **Then** the app detects no hardware or provider support and immediately invites the user to enter a location manually.
2. **Given** the user is in manual mode on a no-hardware device, **When** they view the location settings, **Then** the option to switch to automatic location is absent or disabled with a clear explanation.

---

### User Story 4 - Auto-Location with Live Updates (Priority: P2)

A user with location permission is actively using the star map and moves to a different location — from indoors to outdoors, or to a different city. The app detects the change automatically, updates the star map, and shows a brief notification confirming the new location. No manual action is required.

**Why this priority**: Continuous updates are the core value of automatic location mode; they make the app meaningfully better than manual entry.

**Independent Test**: With permission granted, simulate a location change via developer options or physical movement. Verify the star map repositions and a toast appears with the updated location.

**Acceptance Scenarios**:

1. **Given** automatic location is active, **When** the device detects a meaningful location change, **Then** the star map updates orientation and a notification shows the new location.
2. **Given** automatic location is active, **When** a better location provider becomes available (e.g., GPS signal acquired after being indoors), **Then** the app silently adopts the more accurate source without user action.

---

### User Story 5 - Switching Between Auto and Manual (Priority: P2)

A user who set a manual location at install time wants to switch to automatic detection. From the location management screen, they enable automatic mode, re-grant permission if needed, and the app begins using the real device location. Conversely, a user with automatic location can lock it to a specific fixed location — useful for planning an observing session at a future site.

**Why this priority**: Users' circumstances change; a one-way path locks them into a mode chosen under specific conditions (e.g., no permission at install time).

**Independent Test**: Set a manual location. Open location management. Switch to automatic. Verify real device location is used. Switch back to a manual location and verify the fixed coordinates are used.

**Acceptance Scenarios**:

1. **Given** the user is in manual mode and the device supports auto-location, **When** they switch to automatic in the location management screen, **Then** the app uses existing permission or re-requests it, then begins detecting location automatically.
2. **Given** the user is in automatic mode, **When** they switch to manual in the location management screen, **Then** they enter the manual entry flow and the star map uses the entered coordinates immediately.
3. **Given** the user attempts to switch to automatic on a device with no location hardware, **When** they make that selection, **Then** they are informed the device cannot detect location automatically and remain in manual mode.

---

### User Story 6 - Location Map View (Priority: P3)

A user wants to verify the app is using the correct location. They open the location management screen and see a map with a pin at their current position. The screen clearly labels whether the location is auto-detected or manually entered.

**Why this priority**: Transparency builds trust and helps users diagnose problems. It is valuable but not required for the core functionality to work.

**Independent Test**: Set both auto and manual locations. Open the location management screen in each mode. Verify the pin is at the correct coordinates and the source label is accurate.

**Acceptance Scenarios**:

1. **Given** a valid location is established, **When** the user opens the location management screen, **Then** a map is shown with a pin at the current location and a label indicating auto-detected or manually entered.
2. **Given** no valid location has been established, **When** the user opens the location management screen, **Then** the map shows a neutral/empty state and invites the user to set a location.

---

### Edge Cases

- What happens when the user has granted permission but the device cannot yet obtain a fix (e.g., indoors with no network)? The app must show an "acquiring location…" status indicator. If a location from a previous session exists, use it provisionally. If no prior location exists, use an obviously placeholder position (e.g., the North Pole) so the user can clearly see the sky is not their actual sky. After 30 seconds with no fix, the app must prompt the user to either continue waiting or switch to manual entry.
- What happens when the user revokes location permission while the app is backgrounded and then returns to the foreground? The app must detect the revocation, stop using automatic location, and invite the user to re-grant permission or switch to manual.
- What happens when the user enters latitude or longitude values outside valid ranges (e.g., latitude > 90)? Entry must be rejected with a clear explanation.
- What happens when geocoding returns ambiguous results for a place name (e.g., "Springfield")? The resolved location should be shown on the map for confirmation before being applied.
- What happens if the app is used in airplane mode when GPS is available? GPS works without network; if permission is granted and GPS is available, the app must still use it.
- What happens when the app has a cached location from a previous session but auto-location is not yet confirmed for the new session? The cached location should be used initially with a visual indicator that it may be stale.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST request both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` together. On Android 12 (API 31) and higher, this presents a single system dialog where the user may choose to grant either precise (GPS-capable) or approximate (network-only) location. The app MUST function correctly with either grant: when precise location is granted, GPS MUST be used (enabling offline location in areas with no network); when only approximate location is granted, the app MUST fall back to network-based providers only.
- **FR-002**: The app MUST display a rationale to the user explaining why location is needed before the system permission dialog is shown (on first request and after denial).
- **FR-003**: When the user denies location permission, the app MUST immediately invite them to enter a location manually; showing an incorrect sky silently is not acceptable.
- **FR-004**: When a device has no means of determining location automatically (no hardware, all providers disabled), the app MUST detect this and offer manual location entry.
- **FR-005**: The app MUST NEVER use (0°N, 0°E) as a silent default location. While acquiring a first fix with no prior location available, the app MUST use an obviously wrong placeholder position (e.g., the North Pole) accompanied by a visible "acquiring location…" indicator, so the user can see the sky displayed is not their actual sky.
- **FR-005a**: While automatic location is active but no fix has been obtained, the app MUST display an "acquiring location…" status indicator. If a location from a previous session is available, it MUST be used as a provisional sky view. After 30 seconds with no fix, the app MUST prompt the user to continue waiting or switch to manual entry.
- **FR-006**: Users MUST be able to enter a location by typing latitude and longitude values directly; this path MUST work without internet connectivity.
- **FR-007**: Users SHOULD be able to enter a place name and have it resolved to coordinates; this path requires internet connectivity and MUST degrade gracefully (falling back to direct coordinate entry) when offline.
- **FR-008**: When automatic location is active, the star map MUST update when the device detects a location change that exceeds a minimum distance threshold (~1–5 km); sub-threshold movements MUST NOT trigger a map recalculation or toast.
- **FR-009**: Whenever location is updated by a change exceeding the minimum threshold (auto) or by any manual entry, the app MUST show a brief notification to the user indicating the new location.
- **FR-010**: The app MUST automatically select the best available location provider; users MUST NOT be required to choose between GPS, network, or other providers.
- **FR-011**: The existing "force GPS" / "select GPS provider" user setting MUST be removed.
- **FR-012**: Users who have set a manual location MUST be able to switch to automatic location (where the device supports it), including re-requesting permission if it was previously denied. The previously entered manual location MUST be retained in storage but not used while in automatic mode. If permission has been permanently denied (system dialog will not appear again), the app MUST detect this state, explain it clearly to the user, and provide a direct link to the app's system settings page to re-enable it — rather than silently falling back to manual mode without explanation.
- **FR-013**: Users who have automatic location enabled MUST be able to switch to a fixed manual location. If a manual location was previously entered, the entry screen MUST pre-fill those saved coordinates.
- **FR-014**: A location management screen MUST exist where users can view their current location on a map and see whether it is auto-detected or manually entered.
- **FR-015**: When location status is degraded (no fix yet, permission revoked, no hardware, location unset), the app MUST display a clear human-readable status rather than silently operating with incorrect coordinates.
- **FR-016**: The location system MUST function correctly without internet connectivity. When precise location permission has been granted, GPS MUST be used as the location source offline. When only approximate permission has been granted, offline operation is limited to network-based providers (cell/Wi-Fi); if those are also unavailable, the app MUST surface this clearly rather than failing silently. Previously saved manual coordinates MUST always work offline regardless of permission level.
- **FR-017**: When the app is backgrounded and the device location changes, the app MUST reflect the updated location when returned to the foreground.
- **FR-018**: The diagnostics screen MUST be updated to reflect the new location model, displaying the current location source, location status, coordinates, and permission state in a way that is consistent with the new system.

### Key Entities

- **Location**: A geographic coordinate pair (latitude, longitude) representing the user's observing position. Carries provenance metadata (auto-detected vs. manually entered), accuracy, and a timestamp of last update.
- **Location Source**: The mechanism that produced the current location — automatic (best available device provider) or manual (user-entered by place name or direct coordinates).
- **Location Status**: The current availability state — one of: acquiring, confirmed-auto, confirmed-manual, permission-denied, hardware-unavailable, or unset.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user who denies location permission can successfully set a working manual location and see a correctly oriented star map within 60 seconds of the denial, without outside help.
- **SC-002**: A new user who grants location permission sees a correctly oriented star map with a location confirmation notification within 30 seconds of granting permission, without any additional configuration steps.
- **SC-003**: Zero users ever see the sky map positioned at the Gulf of Guinea (0°N, 0°E) unless they have explicitly and deliberately entered those coordinates as their location.
- **SC-004**: When the device's location changes, the star map reflects the new position within 5 seconds.
- **SC-005**: Manual location entry by direct coordinate input is completable end-to-end with no internet connection.
- **SC-006**: The location management screen correctly displays the current location and its source label in 100% of cases where a location has been established.
- **SC-007**: On any failure state (permission denied, hardware absent, no fix), the app surfaces a human-readable explanation to the user rather than showing a blank or incorrectly positioned sky.

## Clarifications

### Session 2026-05-03

- Q: What minimum distance change should trigger a location update and toast notification? → A: A minimum distance threshold (~1–5 km); sub-threshold movement must not trigger map recalculation or toast.
- Q: What should the user see while automatic location is enabled but no fix has been obtained yet? → A: Show an "acquiring…" indicator; use last known location provisionally if available, otherwise render an obviously wrong placeholder (e.g., North Pole) so the user knows the sky is not their actual sky. After a timeout with no fix, prompt the user to continue waiting or switch to manual entry.
- Q: When a user switches from manual to automatic mode, what happens to their saved manual location? → A: Retain but don't use — kept in storage so switching back to manual pre-fills it; auto mode ignores it entirely.
- Q: How should the app handle permanently-denied location permission (Android 11+, system dialog will not show again)? → A: Detect the permanently-denied state, show a specific explanation, and provide a direct deep-link to the app's system settings page.
- Q: How long should the app wait for an auto-location fix before prompting the user to continue waiting or switch to manual? → A: 30 seconds.

## Assumptions

- The app targets Android SDK 26–36; all platform location services are available.
- Both the GMS (Google Play Services) and fdroid builds are in scope. In the GMS build, the location management screen shows an interactive map. In the fdroid build, the map is replaced by a text-based coordinate display showing latitude, longitude, and the location source label — no map tile provider is required.
- Geocoding (place name → coordinates) is a best-effort convenience feature; the app falls back gracefully to direct coordinate entry when geocoding is unavailable or returns no results.
- Existing user location settings (manual/automatic mode, stored coordinates, stored place name) will be migrated or honoured on upgrade so that existing users do not lose their configured location.
- The star map's rendering layer already accepts a location and re-renders; this feature replaces the location acquisition and management layer only, not the rendering layer.
- All user-facing strings are in US English; translations are handled by a separate pipeline after implementation.
- There is no requirement to store a history of past locations.
- City-level (approximate) accuracy is sufficient for the star map's sky rendering. When the user grants precise location, GPS is used for better offline reliability in remote dark-sky sites; when only approximate is granted, network-based location provides city-level accuracy. Both modes produce a correct sky rendering.
- The "remove select GPS" requirement applies only to the user-visible setting, not to the internal provider selection logic (which should transparently prefer GPS when available).
