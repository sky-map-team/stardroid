# Implementation Plan: Modern Location Handling

**Branch**: `002-modern-location` | **Date**: 2026-05-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-modern-location/spec.md`

## Summary

Replace the current `LocationController.java` and its associated permission/settings flow with a
modern, state-driven Kotlin implementation. The new system introduces a `LocationState` sealed
class, a `LocationProvider` interface with separate GMS (`FusedLocationProviderClient`) and
fdroid (`LocationManager` multi-provider) implementations, and a `LocationManagementActivity`
that lets users view, enter, and switch their location.

The (0,0) silent default is eliminated: the initial placeholder is the North Pole (90°N, 0°E) so
the sky is obviously wrong until a real location is confirmed. A 30-second acquiring timeout
prompts the user to continue waiting or enter manually. The distance threshold for triggering a
map update and toast is kept at 2,000 m (matching existing code). The `force_gps` preference is
removed; the best provider is selected automatically per flavor.

## Technical Context

**Language/Version**: Kotlin (all new files), targeting Java 17 toolchain; existing Java files
  touched only where required by feature scope  
**Primary Dependencies**: `play-services-location:21.3.0` (GMS, already present) for
  `FusedLocationProviderClient`; `play-services-maps` (GMS, new — see Complexity Tracking);
  Android platform `LocationManager` (fdroid); Android platform `Geocoder` (no new dep)  
**Storage**: Android `SharedPreferences` — existing keys `no_auto_locate`, `latitude`,
  `longitude`, `location` (place name) preserved; `force_gps` key removed  
**Testing**: JUnit 4 + Truth + Mockito (unit); Robolectric (Android-framework unit); Espresso
  (UI integration)  
**Target Platform**: Android 8.0+ (minSdk 26), portrait + landscape; gms and fdroid flavors  
**Project Type**: Android mobile app — subsystem rewrite  
**Performance Goals**: Location updates delivered to `AstronomerModel` within 5 s of device
  detecting a threshold-crossing change; no blocking work on main thread  
**Constraints**: Offline-capable (GPS available when precise permission granted; network-only when
  approximate only); 2,000 m minimum distance before map update + toast; 30 s acquiring timeout;
  both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` requested together (user chooses
  precise or approximate on Android 12+; both required for GPS on pre-12)  
**Scale/Scope**: Single-user device app; one active location at a time

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked post-design below.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Sensor-First Architecture | ✅ Pass | Location feeds into `AstronomerModel.setLocation()` — unchanged pipeline |
| II. Layer Modularity | ✅ Pass | Not touching rendering layers |
| III. Flavor Purity | ✅ Pass | `FusedLocationProvider` in `gms/`; `PlatformLocationProvider` in `fdroid/`; `LocationManagementActivity` map view variant in `gms/` layout; no `com.google.*` in `main/` |
| IV. Test Discipline | ✅ Pass | Unit tests required for `LocationController`, `LocationState` transitions, coordinate validation, geocoder fallback — see Project Structure |
| V. Simplicity | ⚠️ See Complexity Tracking | `play-services-maps` added to GMS for map view (FR-014); justified below |
| VI. Performance | ✅ Pass | Location callbacks off main thread; threshold filter prevents unnecessary recalculation; 2,000 m gate already in existing code |
| VII. Feature-Scoped Changes | ✅ Pass | Plan explicitly restricts Java file touches to lines required by this feature |

*Post-design re-check: All gates pass. One justified complexity entry (maps library).*

## Project Structure

### Documentation (this feature)

```text
specs/002-modern-location/
├── plan.md              # This file
├── research.md          # Phase 0 findings
├── data-model.md        # Phase 1 data model
├── quickstart.md        # Phase 1 build + test guide
├── contracts/
│   └── location-provider.md   # LocationProvider interface contract
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

**Deleted** (replaced outright):

```text
app/src/main/java/com/google/android/stardroid/
└── control/
    └── LocationController.java                        # DELETE — replaced by Kotlin rewrite

app/src/main/java/com/google/android/stardroid/activities/dialogs/
└── LocationPermissionDeniedDialogFragment.java        # DELETE — flow replaced by new dialogs
```

**Created** (new files — all Kotlin unless noted):

```text
app/src/main/java/com/google/android/stardroid/control/
├── LocationController.kt           # NEW — Kotlin rewrite; Activity-scoped; manages state,
│                                   #       threshold filtering, timeout, toast, model updates
├── LocationProvider.kt             # NEW — interface: startUpdates / stopUpdates / isAvailable
└── LocationState.kt                # NEW — sealed class (Unset, Acquiring, Confirmed,
                                    #       PermissionDenied, PermissionPermanentlyDenied,
                                    #       HardwareUnavailable, AcquiringTimeout)

app/src/gms/java/com/google/android/stardroid/control/
└── FusedLocationProvider.kt        # NEW — LocationProvider impl via FusedLocationProviderClient

app/src/fdroid/java/com/google/android/stardroid/control/
└── PlatformLocationProvider.kt     # NEW — LocationProvider impl via LocationManager
                                    #       (GPS + network, accepts whichever arrives first)

app/src/main/java/com/google/android/stardroid/activities/
├── LocationManagementActivity.kt         # NEW — shows location source label + map (GMS) or
│                                         #       coordinates (fdroid); mode toggle; manual entry
└── LocationManagementActivityModule.kt   # NEW — Hilt @ActivityScoped bindings

app/src/main/java/com/google/android/stardroid/activities/dialogs/
├── LocationPermissionRationaleDialogFragment.kt        # NEW — shown before first permission request
├── LocationPermissionPermanentlyDeniedDialogFragment.kt # NEW — deep-link to app settings
├── ManualLocationEntryDialogFragment.kt                # NEW — place name + lat/lon fields
└── AcquiringLocationTimeoutDialogFragment.kt           # NEW — 30 s timeout prompt

app/src/main/res/layout/
├── activity_location_management.xml      # NEW — root layout (map stub + controls)
├── dialog_location_rationale.xml         # NEW
├── dialog_location_permanently_denied.xml # NEW
├── dialog_manual_location_entry.xml       # NEW
└── dialog_acquiring_timeout.xml           # NEW

app/src/gms/res/layout/
└── activity_location_management.xml      # NEW — GMS overlay: replaces stub with MapView

app/src/test/java/com/google/android/stardroid/control/
├── LocationControllerTest.kt             # NEW — threshold filter, timeout logic, state transitions
├── LocationStateTest.kt                  # NEW — sealed class coverage
└── ManualLocationValidationTest.kt       # NEW — coordinate range validation, geocoder fallback
```

**Modified** (minimal, feature-scoped changes only):

```text
app/src/main/java/com/google/android/stardroid/
├── control/AstronomerModelImpl.kt         # Change initial location LatLong(0f,0f) → LatLong(90f,0f)
│                                          # (North Pole placeholder — obviously wrong to user)
├── control/ControllerGroup.java           # Inject LocationController.kt instead of .java
├── ApplicationConstants.kt               # Add LOCATION_UPDATE_MIN_DISTANCE_METRES = 2000f,
│                                          # LOCATION_ACQUIRING_TIMEOUT_MS = 30_000L constants
├── activities/DynamicStarMapActivity.java # Replace old permission request + LocationController.start()
│                                          # calls with new flow; add LocationManagementActivity
│                                          # launch; add overflow menu entry
└── activities/DiagnosticActivity.java     # Update location rows to display LocationState,
                                           # source label, provider, coordinates, permission state

app/src/gms/java/com/google/android/stardroid/activities/util/
└── GooglePlayServicesChecker.java         # Remove location-permission delegation (now in
                                           # LocationController); retain GMS availability check

app/src/main/res/xml/preference_screen.xml # Remove force_gps CheckBoxPreference;
                                           # keep no_auto_locate, latitude, longitude, location

app/src/main/AndroidManifest.xml           # Register LocationManagementActivity;
                                           # add ACCESS_FINE_LOCATION permission declaration
                                           # (ACCESS_COARSE_LOCATION already declared)

app/build.gradle                           # Add play-services-maps to gms flavor dependencies
```

**Structure Decision**: Single Android app module. GMS/fdroid split uses existing source set
structure. No new Gradle modules.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New GMS dependency: `play-services-maps` | FR-014 requires a map view of the user's location in the GMS build | Static Maps API requires an API key and network; no existing map rendering is available in the app; `play-services-maps` is the canonical GMS-flavored solution and consistent with the existing `play-services-location` dependency already present in the gms flavor |
