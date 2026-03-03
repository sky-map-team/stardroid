# Location Feature Spec

## Overview

Sky Map needs the user's geographic location to show the correct night sky. This document describes how location is acquired, the failure modes, and the intended UX for each case.

## Location Acquisition Flow

```
DynamicStarMapActivity.onResume()
  └─ controller.start()
       └─ LocationController.start()
            ├─ lastStatus = OK  (reset every call)
            ├─ noAutoLocate == true?
            │    └─ setLocationFromPrefs()
            │         ├─ parsed lat == 0.0 && lon == 0.0 → lastStatus = MANUAL_NO_COORDS
            │         └─ else → parse and set location
            ├─ locationManager == null → setLocationFromPrefs()
            ├─ getBestProvider(enabled=true) == null?
            │    ├─ getBestProvider(enabled=false) == null → setLocationFromPrefs()
            │    └─ else → show "Enable GPS" dialog
            ├─ requestLocationUpdates + getLastKnownLocation → setLocationInModel
            └─ SecurityException → lastStatus = PERMISSION_DENIED
  └─ maybeShowLocationWarning()
```

## Failure Modes

### 1. Permission denied
**Trigger**: `SecurityException` in `LocationController.start()`.

**Existing handling**: `LocationPermissionDeniedDialogFragment` is shown the first time (via `GooglePlayServicesChecker`), offering Grant / Enter Manually / Later.

**Current handling**: `maybeShowLocationWarning()` shows a `Toast` and re-opens `LocationPermissionDeniedDialogFragment` on every `onResume()` where location is still unset.

### 2. Location provider disabled (location off in system settings)
**Trigger**: `getBestProvider(criteria, true)` returns null but `getBestProvider(criteria, false)` is non-null.

**Existing handling**: `getSwitchOnGPSDialog()` shown, offering to open Location Settings. If cancelled, `NO_AUTO_LOCATE` is set to true and `setLocationFromPrefs()` is called.

**Current handling**: No additional Toast — the existing dialog is sufficient.

### 3. Manual mode with default coordinates
**Trigger**: User enables "Set location manually" (`NO_AUTO_LOCATE = true`) in Settings without entering coordinates. Parsed lat/lon both equal `0.0f`.

**Existing handling**: None — completely silent, map shows Null Island (0°N, 0°E).

**Current handling**: `setLocationFromPrefs()` detects parsed `0.0f`/`0.0f` and sets `lastStatus = MANUAL_NO_COORDS`. `maybeShowLocationWarning()` shows a Toast telling the user to configure in Settings.

### 4. No location provider available at all
**Trigger**: Both `getBestProvider(criteria, true)` and `getBestProvider(criteria, false)` return null.

**Existing handling**: Falls through to `setLocationFromPrefs()` silently.

**Current handling**: Not addressed (rare — device has no location hardware).

## LocationStatus Enum

`LocationController.LocationStatus` records the outcome of the most recent `start()` call:

| Value | Meaning |
|-------|---------|
| `OK` | Location acquired (or `start()` not yet called — default) |
| `PERMISSION_DENIED` | `SecurityException` in `start()` |
| `NO_PROVIDER` | Not currently used for Toast (existing dialog handles it) |
| `MANUAL_NO_COORDS` | Manual mode, parsed lat == 0.0 && lon == 0.0 |

`lastStatus` is reset to `OK` at the start of each `start()` call.

## Toast Warning Behaviour

`DynamicStarMapActivity.maybeShowLocationWarning()` is called from `onResume()` after
`controller.start()`. Uses `controller.getLocationController()` to read the same instance
that `ControllerGroup` started (direct injection creates a separate never-started instance).

Shows a Toast only if:
1. `locationController.isLocationUnset()` — model location is (0.0, 0.0), AND
2. `lastStatus` is `PERMISSION_DENIED` or `MANUAL_NO_COORDS`

For `PERMISSION_DENIED`, also re-shows `LocationPermissionDeniedDialogFragment`.

**Note**: `Snackbar` (Material) was tried and rejected — it crashes on the app's AppCompat-only
`FullscreenTheme` because Material layouts reference `?attr/colorOnSurface` which isn't defined
in `ThemeOverlay.AppCompat.Dark`.

## Diagnostics

`DiagnosticActivity` shows a "Location Permission" row (first row in the Location & Time section):
- **Granted** — `R.color.status_good` (day) / `R.color.night_status_good` (night)
- **Denied** — `R.color.status_bad` (day) / `R.color.night_status_bad` (night)

## Key Constants and Files

| Item | Location |
|------|----------|
| `NO_AUTO_LOCATE` pref key | `LocationController.NO_AUTO_LOCATE = "no_auto_locate"` |
| Manual lat/long pref keys | `"latitude"`, `"longitude"` (default `"0"`) |
| Permission dialog | `LocationPermissionDeniedDialogFragment` |
| Settings entry point | `EditSettingsActivity` |
| Status colours | `R.color.status_good/bad`, `R.color.night_status_good/bad` |

## Out of Scope

- Migrating from deprecated `Criteria`/`LocationManager` to `FusedLocationProvider`
- Adding `ACCESS_FINE_LOCATION` permission
- Handling no-hardware case with a dedicated UI
