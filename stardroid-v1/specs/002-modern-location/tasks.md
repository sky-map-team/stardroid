# Tasks: Modern Location Handling

**Input**: Design documents from `specs/002-modern-location/`
**Branch**: `locationstuffiscrap`

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no unresolved dependencies)
- **[Story]**: User story label (US1–US6) from spec.md
- All paths relative to `stardroid-v1/`

---

## Phase 1: Setup

**Purpose**: Gradle, constants, and the one model change needed before anything else.

- [X] T001 Add `play-services-maps` to the `gms` flavor dependencies in `app/build.gradle`
- [X] T002 [P] Add `LOCATION_UPDATE_MIN_DISTANCE_METRES = 2000f` and `LOCATION_ACQUIRING_TIMEOUT_MS = 30_000L` constants to `app/src/main/java/com/google/android/stardroid/ApplicationConstants.kt`
- [X] T003 [P] Change the initial location in `app/src/main/java/com/google/android/stardroid/control/AstronomerModelImpl.kt` from `LatLong(0f, 0f)` to `LatLong(90f, 0f)` (North Pole placeholder — satisfies FR-005 and SC-003)

**Checkpoint**: Build passes (`assembleGmsDebug` + `assembleFdroidDebug`). No (0°N, 0°E) default remains.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core types and provider implementations that every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 Create `app/src/main/java/com/google/android/stardroid/control/LocationState.kt` — sealed class with states: `Unset`, `Acquiring`, `Confirmed(location, source, accuracy, timestamp)`, `PermissionDenied`, `PermissionPermanentlyDenied`, `HardwareUnavailable`, `AcquiringTimeout`; plus `LocationSource` enum (`AUTO`, `MANUAL`) in the same file (see data-model.md for full definition)
- [X] T005 [P] Create `app/src/main/java/com/google/android/stardroid/control/LocationProvider.kt` — interface with `startUpdates(minDistanceMetres: Float, onUpdate: (LatLong, Float?) -> Unit)`, `stopUpdates()`, `isAvailable(): Boolean` (see contracts/location-provider.md for full behaviour contract)
- [X] T006 [P] Create `app/src/gms/java/com/google/android/stardroid/control/FusedLocationProvider.kt` — `LocationProvider` implementation using `FusedLocationProviderClient.requestLocationUpdates()` with `Priority.PRIORITY_BALANCED_POWER_ACCURACY`; `isAvailable()` returns `true` on any GMS device; converts `android.location.Location` to `LatLong`
- [X] T007 [P] Create `app/src/fdroid/java/com/google/android/stardroid/control/PlatformLocationProvider.kt` — `LocationProvider` implementation using `LocationManager`; registers `LocationListener` on both `GPS_PROVIDER` and `NETWORK_PROVIDER`; `isAvailable()` returns true if either provider is enabled; accepts first update per event window; ignores subsequent updates unless from a provider with better accuracy
- [X] T008 Create `app/src/main/java/com/google/android/stardroid/control/LocationController.kt` — Kotlin rewrite, `@ActivityScoped`; injects `LocationProvider`, `AstronomerModel`, `SharedPreferences`, `Activity`; exposes `start()` / `stop()` / `currentState(): LocationState`; skeleton only — state machine logic added in story phases
- [X] T009 Update `app/src/main/java/com/google/android/stardroid/control/ControllerGroup.java` to inject `LocationController` (Kotlin) instead of the Java version; add `LocationProvider` as an injected field passed through to the controller
- [X] T010 Delete `app/src/main/java/com/google/android/stardroid/control/LocationController.java` (replaced by T008)
- [X] T011 [P] Remove the `force_gps` `CheckBoxPreference` block from `app/src/main/res/xml/preference_screen.xml` and remove all `FORCE_GPS` / `"force_gps"` references from `app/src/main/java/com/google/android/stardroid/activities/EditSettingsActivity.java` (FR-011)
- [X] T012 [P] Create `app/src/test/java/com/google/android/stardroid/control/LocationStateTest.kt` — unit tests covering every state in the sealed class, the full state transition table from data-model.md, and equality/identity semantics of `Confirmed`
- [X] T013 [P] Create `app/src/test/java/com/google/android/stardroid/control/LocationControllerTest.kt` — unit tests for: distance threshold gate (≥2000 m triggers update, <2000 m does not), 30 s timeout fires `AcquiringTimeout`, permission-revoked transition to `PermissionDenied`, `isAvailable() = false` produces `HardwareUnavailable`

**Checkpoint**: All new types compile. Unit tests (T012, T013) pass. Both flavors build cleanly.

---

## Phase 3: User Story 1 — New User Permission Flow (Priority: P1) 🎯 MVP

**Goal**: Fresh install → rationale dialog → grant permission → acquiring state with North Pole sky → confirmed with toast → correct sky rendered.

**Independent Test**: Fresh install, no prior permissions. Launch app. Rationale appears before system dialog. Grant permission. Within 30 s a toast confirms the location and the star map shows the correct sky.

- [X] T014 [P] [US1] Create `app/src/main/res/layout/dialog_location_rationale.xml` — dialog layout with icon, explanation text ("Sky Map needs your location to show the night sky above you"), and three buttons: Grant / Enter Manually / Later
- [X] T015 [P] [US1] Create `app/src/main/java/com/google/android/stardroid/activities/dialogs/LocationPermissionRationaleDialogFragment.kt` — `@AndroidEntryPoint` `DialogFragment`; `newInstance()` factory; three button callbacks: `onGrant` fires permission request launcher, `onEnterManually` navigates to manual entry, `onLater` dismisses
- [X] T016 [P] [US1] Create `app/src/main/res/layout/dialog_acquiring_timeout.xml` — dialog layout with spinner/icon, "Still trying to find your location…" message, and two buttons: Keep Waiting / Enter Manually
- [X] T017 [P] [US1] Create `app/src/main/java/com/google/android/stardroid/activities/dialogs/AcquiringLocationTimeoutDialogFragment.kt` — `@AndroidEntryPoint` `DialogFragment`; `newInstance()` factory; `onKeepWaiting` resets the timer in `LocationController`; `onEnterManually` navigates to `ManualLocationEntryDialogFragment`
- [X] T018 [US1] Implement the permission request lifecycle in `app/src/main/java/com/google/android/stardroid/activities/DynamicStarMapActivity.java`: register `ActivityResultContracts.RequestPermission` launcher for `ACCESS_COARSE_LOCATION`; on first-run check (no prior grant, `no_auto_locate = false`), show `LocationPermissionRationaleDialogFragment` via `showDialog()`; on grant result call `locationController.start()`; on denial call `locationController.onPermissionDenied(shouldShowRequestPermissionRationale())`
- [X] T019 [US1] Implement `Acquiring` state logic in `LocationController.kt`: on `start()` with permission granted and `isAvailable() = true`, transition to `Acquiring`; start a `Handler.postDelayed` for `LOCATION_ACQUIRING_TIMEOUT_MS` (30 s) that transitions to `AcquiringTimeout` and notifies `DynamicStarMapActivity` to show `AcquiringLocationTimeoutDialogFragment`; call `locationProvider.startUpdates(LOCATION_UPDATE_MIN_DISTANCE_METRES, ...)`
- [X] T020 [US1] Implement `Confirmed(AUTO)` state logic in `LocationController.kt`: on location update callback, apply the 2,000 m distance gate against the previous confirmed location; if threshold exceeded (or first fix), call `AstronomerModel.setLocation()`, transition to `Confirmed(AUTO)`, cancel the timeout `Handler`, and show a Toast with the location coordinates (FR-009)
- [X] T021 [US1] Remove location-permission delegation from `app/src/gms/java/com/google/android/stardroid/activities/util/GooglePlayServicesChecker.java` — delete `checkLocationServicesEnabled()` and `requestLocationPermission()` methods; retain GMS availability check only

**Checkpoint**: US1 independently testable. Fresh install → rationale → grant → acquiring → confirmed sky + toast within 30 s.

---

## Phase 4: User Story 2 — Permission Denied, Manual Entry (Priority: P1)

**Goal**: Permission denied → immediate manual-entry invitation → lat/lon or place-name entry → correct sky.

**Independent Test**: Deny permission. Manual-entry invitation appears immediately. Enter "London" online → sky updates. Reset; enter `51.5 / -0.1` offline → sky updates with no internet.

- [X] T022 [P] [US2] Create `app/src/main/res/layout/dialog_manual_location_entry.xml` — layout with: place-name `EditText` (hint: "City or place name"), latitude `EditText` (signed decimal, hint: "Latitude"), longitude `EditText` (signed decimal, hint: "Longitude"), a "Resolve" button (online geocoding), a "Set Location" confirm button, and inline error text views for validation feedback
- [X] T023 [US2] Create `app/src/main/java/com/google/android/stardroid/activities/dialogs/ManualLocationEntryDialogFragment.kt` — `@AndroidEntryPoint` `DialogFragment`; `newInstance(prefillLat, prefillLon, prefillName)` factory (pre-fills from saved prefs per FR-013); validates lat ∈ [−90, 90] and lon ∈ [−180, 180] with inline error on invalid input; "Resolve" button calls `Geocoder.getFromLocationName()` off the main thread — on success shows first result for confirmation, on no-result shows "Place not found" inline error and shows the lat/lon fields, on offline shows "No internet — enter coordinates directly"; on confirm calls `locationController.setManualLocation(LatLong)` and dismisses
- [X] T024 [P] [US2] Create `app/src/main/res/layout/dialog_location_permanently_denied.xml` — layout with warning icon, explanation text ("Location permission was permanently denied. Open App Settings to re-enable it."), and two buttons: Open Settings / Enter Manually
- [X] T025 [P] [US2] Create `app/src/main/java/com/google/android/stardroid/activities/dialogs/LocationPermissionPermanentlyDeniedDialogFragment.kt` — `@AndroidEntryPoint` `DialogFragment`; `onOpenSettings` fires `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`; `onEnterManually` shows `ManualLocationEntryDialogFragment`
- [X] T026 [US2] Implement `PermissionDenied` / `PermissionPermanentlyDenied` state handling in `DynamicStarMapActivity.java`: in `onPermissionDenied(canAsk: Boolean)`, if `canAsk = true` show `LocationPermissionRationaleDialogFragment`; if `canAsk = false` show `LocationPermissionPermanentlyDeniedDialogFragment`; when `LocationController` state is `PermissionDenied` and no location is confirmed, ensure the star map shows North Pole sky (already set by T003) and an invitation to set location is visible (FR-003, FR-015)
- [X] T027 [US2] Implement `Confirmed(MANUAL)` state in `LocationController.kt`: add `setManualLocation(location: LatLong)` — saves `latitude`, `longitude`, `no_auto_locate = true` to `SharedPreferences`; calls `AstronomerModel.setLocation()`; transitions state to `Confirmed(MANUAL, accuracy=null)`; shows Toast with coordinates (FR-009); cancels any active acquiring timeout
- [X] T028 [P] [US2] Write `app/src/test/java/com/google/android/stardroid/control/ManualLocationValidationTest.kt` — unit tests for: lat > 90 rejected, lat < −90 rejected, lon > 180 rejected, lon < −180 rejected, valid boundary values accepted, geocoder returning empty list falls back gracefully, geocoder throwing `IOException` (offline) does not crash

**Checkpoint**: US2 independently testable. Deny permission → invitation shown → manual entry works both online (geocoding) and offline (coordinates) → correct sky rendered.

---

## Phase 5: User Story 3 — No Location Hardware (Priority: P1)

**Goal**: Device with no/disabled location providers → immediate manual-entry invitation; auto-location option hidden.

**Independent Test**: Disable all location providers in device settings. Launch app. Manual-entry invitation appears immediately. No auto-location option is offered.

- [X] T029 [US3] Implement `HardwareUnavailable` state in `LocationController.kt`: in `start()`, before requesting permission, call `locationProvider.isAvailable()`; if `false`, transition to `HardwareUnavailable` and notify activity (do not request permission); add `onProviderEnabled()` callback via `LocationManager.addProviderChangeListener()` (API 29+) or `BroadcastReceiver` for `LocationManager.PROVIDERS_CHANGED_ACTION` (API 26–28) to re-evaluate when providers change
- [X] T030 [US3] Handle `HardwareUnavailable` in `DynamicStarMapActivity.java`: when `LocationController` state is `HardwareUnavailable`, show `ManualLocationEntryDialogFragment` immediately on first launch; in `LocationManagementActivity.kt` (created in US5) disable or hide the "Use automatic location" toggle with an explanatory label ("This device cannot detect location automatically")

**Checkpoint**: US3 independently testable. All-providers-disabled → invitation shown, auto toggle absent.

---

## Phase 6: User Story 4 — Auto-Location with Live Updates (Priority: P2)

**Goal**: Location provider detects a change > 2,000 m → star map updates + toast. Provider upgrade (network → GPS) happens silently.

**Independent Test**: With permission granted, simulate a >2 km location change via mock location. Star map repositions. Toast appears. Simulate a <2 km change — no toast, no repositioning.

- [X] T031 [US4] Implement continuous update lifecycle in `LocationController.kt`: call `locationProvider.startUpdates()` in `start()` and `locationProvider.stopUpdates()` in `stop()`; ensure `stop()` is called from `onPause()` and `start()` from `onResume()` via `ControllerGroup`; re-check permission state on each `onResume()` — if permission was revoked while backgrounded, transition to `PermissionDenied` and notify activity (FR-017)
- [X] T032 [US4] Implement the 2,000 m distance gate in `LocationController.kt`: in the `onUpdate` callback from `locationProvider`, compute great-circle distance between the new `LatLong` and the current `Confirmed.location` using `LatLong.distanceFrom()` (convert `LOCATION_UPDATE_MIN_DISTANCE_METRES` to degrees: 2000 m ≈ 0.018°); if distance exceeds threshold, update `Confirmed` state, call `AstronomerModel.setLocation()`, and show Toast; if below threshold, discard silently (FR-008)
- [X] T033 [US4] Handle foreground-return permission re-check in `DynamicStarMapActivity.java`: in `onResume()`, if `LocationController.currentState()` is `Confirmed(AUTO)` but `checkSelfPermission(ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED`, call `locationController.onPermissionRevoked()` to transition to `PermissionDenied` and surface the re-grant invitation

**Checkpoint**: US4 independently testable. Mock location >2 km → sky update + toast. Mock <2 km → no change.

---

## Phase 7: User Story 5 — Switching Between Auto and Manual (Priority: P2)

**Goal**: `LocationManagementActivity` with mode toggle; switching to manual pre-fills saved coordinates; switching to auto re-requests permission if needed.

**Independent Test**: Set manual location → open management screen → switch to auto → sky uses device location. Switch back to manual → pre-filled saved coords → sky uses manual location.

- [X] T034 [P] [US5] Create `app/src/main/res/layout/activity_location_management.xml` — layout with: source label `TextView` ("Automatic" / "Manual"), current coordinates `TextView`, a `FrameLayout` stub (`@id/map_container`) for the map or coordinate display, a mode-toggle `Button` ("Switch to automatic" / "Switch to manual"), and a "Change location" `Button` (shown in manual mode only)
- [X] T035 [P] [US5] Create `app/src/gms/res/layout/activity_location_management.xml` — GMS resource overlay replacing the `map_container` stub with a `com.google.android.gms.maps.MapView` at the same id, so `LocationManagementActivity` can use `MapView` on GMS without a flavor branch in Kotlin code
- [X] T036 [US5] Create `app/src/main/java/com/google/android/stardroid/activities/LocationManagementActivity.kt` — `@AndroidEntryPoint` activity; reads `LocationController.currentState()` to set source label and coordinates; "Switch to automatic" calls `locationController.switchToAuto()` (which requests permission if not held, or transitions to `Acquiring`); "Switch to manual" calls `locationController.switchToManual()` which shows `ManualLocationEntryDialogFragment` pre-filled with saved coordinates; hide mode toggle when state is `HardwareUnavailable`
- [X] T037 [US5] Create `app/src/main/java/com/google/android/stardroid/activities/LocationManagementActivityModule.kt` — Hilt `@Module`, `@InstallIn(ActivityComponent::class)` for any `LocationManagementActivity`-scoped bindings; register the activity in `app/src/main/AndroidManifest.xml`
- [X] T038 [US5] Add "Location" item to the overflow menu in `DynamicStarMapActivity.java` that launches `LocationManagementActivity` via `startActivity()`
- [X] T039 [US5] Implement mode-switch logic in `LocationController.kt`: add `switchToAuto()` — if permission held, transition to `Acquiring` and start updates; if not held, trigger permission request; add `switchToManual()` — set `no_auto_locate = true` in `SharedPreferences`, stop updates, retain existing manual coords in prefs but do not change state until user confirms new coords; retained coords are passed to `ManualLocationEntryDialogFragment` via `newInstance(prefillLat, prefillLon, prefillName)` (FR-012, FR-013)

**Checkpoint**: US5 independently testable. Mode toggle works in both directions. Saved coords pre-fill manual entry.

---

## Phase 8: User Story 6 — Location Map View (Priority: P3)

**Goal**: `LocationManagementActivity` shows GMS map with pin at current location; fdroid shows text coordinates. Both show source label.

**Independent Test**: GMS — set auto + manual locations; open management screen; verify pin moves to correct coordinates and label reads "Automatic" / "Manual". fdroid — same, but verify text coordinates display and no map tiles.

- [X] T040 [P] [US6] Wire `MapView` in `LocationManagementActivity.kt` for GMS: check at runtime whether `map_container` contains a `MapView` (GMS layout overlay); if so, call `mapView.getMapAsync {}` to add/move a `MarkerOptions` pin to `Confirmed.location`; forward `MapView` lifecycle calls (`onCreate`, `onResume`, `onPause`, `onDestroy`, `onSaveInstanceState`) from the activity; update pin whenever `LocationController` state changes to a new `Confirmed`
- [X] T041 [P] [US6] Wire text-coordinate display in `LocationManagementActivity.kt` for fdroid: if `map_container` does not contain a `MapView` (fdroid layout), show a `TextView` inside `map_container` with `"%.4f°, %.4f°".format(lat, lon)` and the source label; update on state change
- [X] T042 [US6] Handle no-location state in `LocationManagementActivity.kt`: when `LocationController.currentState()` is `Unset`, `Acquiring`, or `HardwareUnavailable`, show appropriate message in `map_container` ("No location set — tap below to add one" / "Acquiring location…" / "Location unavailable on this device") and hide the coordinates `TextView`

**Checkpoint**: US6 independently testable. Map/text display correct for both modes and both flavors.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T043 Update `app/src/main/java/com/google/android/stardroid/activities/DiagnosticActivity.java` — replace existing GPS/location rows with: current `LocationState` name, `LocationSource` (auto/manual), coordinates from `locationController.currentState()`, permission grant status (`ACCESS_COARSE_LOCATION` granted/denied), active provider name (FR-018)
- [X] T044 Delete `app/src/main/java/com/google/android/stardroid/activities/dialogs/LocationPermissionDeniedDialogFragment.java` (replaced by `LocationPermissionRationaleDialogFragment` + `LocationPermissionPermanentlyDeniedDialogFragment`)
- [X] T045 [P] Verify flavor purity: run `grep -r "com.google.android.gms" app/src/main/java/` — must produce no output; run `grep -r "com.google.android.gms" app/src/fdroid/java/` — must produce no output
- [X] T046 [P] Run full build and unit tests for both flavors: `./gradlew assembleGmsDebug assembleFdroidDebug :app:testGmsDebugUnitTest :app:testFdroidDebugUnitTest` — all must pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — **blocks all user stories**
- **US1 (Phase 3)**: Depends on Phase 2 — no dependency on US2/US3
- **US2 (Phase 4)**: Depends on Phase 2 — no dependency on US1 (but `ManualLocationEntryDialogFragment` is reused by US5)
- **US3 (Phase 5)**: Depends on Phase 2 and `ManualLocationEntryDialogFragment` from US2 (T023)
- **US4 (Phase 6)**: Depends on US1 (continuous update loop extends the Confirmed/Acquiring state machine from T019/T020)
- **US5 (Phase 7)**: Depends on US1, US2, US3 (LocationManagementActivity integrates all states and the manual entry dialog)
- **US6 (Phase 8)**: Depends on US5 (`LocationManagementActivity` must exist)
- **Polish (Phase 9)**: Depends on all user stories

### Within Each Phase

- Tasks marked `[P]` within a phase can be executed in parallel
- Layout XML tasks (T014, T016, T022, T024, T034, T035) can all run in parallel with each other
- Dialog fragment tasks depend on their layout XML task being done first
- `LocationController.kt` state machine tasks (T019, T020, T027, T029, T031, T032, T039) must be done sequentially as each adds state to the same file

---

## Parallel Execution Examples

### Phase 2 Parallel Batch
```
T005 LocationProvider.kt interface
T006 FusedLocationProvider.kt (gms)      ← parallel with T007
T007 PlatformLocationProvider.kt (fdroid) ← parallel with T006
T011 Remove force_gps preference
T012 LocationStateTest.kt
T013 LocationControllerTest.kt
```

### Phase 3 (US1) Parallel Batch
```
T014 dialog_location_rationale.xml layout  ← parallel with T016
T015 LocationPermissionRationaleDialogFragment.kt
T016 dialog_acquiring_timeout.xml layout   ← parallel with T014
T017 AcquiringLocationTimeoutDialogFragment.kt
```
Then sequentially: T018 → T019 → T020 → T021

### Phase 4 (US2) Parallel Batch
```
T022 dialog_manual_location_entry.xml
T024 dialog_location_permanently_denied.xml ← parallel with T022
T025 LocationPermissionPermanentlyDeniedDialogFragment.kt
T028 ManualLocationValidationTest.kt
```
Then sequentially: T023 → T026 → T027

---

## Implementation Strategy

### MVP (User Stories 1, 2, 3 — all P1)

1. Complete Phase 1: Setup (T001–T003)
2. Complete Phase 2: Foundational (T004–T013) — **critical blocker**
3. Complete Phase 3: US1 (T014–T021) — permission-granted happy path
4. Complete Phase 4: US2 (T022–T028) — denial + manual entry
5. Complete Phase 5: US3 (T029–T030) — no hardware
6. **STOP and validate**: all three P1 stories pass their independent tests; SC-003 (no (0,0) default) confirmed

### Full Delivery

7. Phase 6: US4 — live updates
8. Phase 7: US5 — mode switching + LocationManagementActivity
9. Phase 8: US6 — map/coordinate view
10. Phase 9: Polish, diagnostics, flavor purity check

### Key Constraint

`LocationController.kt` is the central file for Phases 2–7. Tasks that touch it (T008, T019, T020, T027, T029, T031, T032, T039) must be done sequentially within their respective phases to avoid conflicts. All other files in each phase can proceed in parallel.
