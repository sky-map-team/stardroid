# Location Feature Spec

## Overview

Sky Map needs the user's geographic location to show the correct night sky. This document describes how location is acquired, the failure modes, and the intended UX for each case.

## Location Acquisition Flow

```
DynamicStarMapActivity.onResume()
  └─ controller.start()
       └─ LocationController.start()
            ├─ noAutoLocate == true?
            │    └─ setLocationFromPrefs()
            │         ├─ prefs "latitude"/"longitude" both "0" → MANUAL_NO_COORDS
            │         └─ else → parse and set location
            ├─ locationManager == null → setLocationFromPrefs()
            ├─ getBestProvider(enabled=true) == null?
            │    ├─ getBestProvider(enabled=false) == null → setLocationFromPrefs()
            │    └─ else → show "Enable GPS" dialog
            ├─ requestLocationUpdates + getLastKnownLocation → setLocationInModel
            └─ SecurityException → PERMISSION_DENIED
```

## Failure Modes

### 1. Permission denied
**Trigger**: `SecurityException` thrown in `LocationController.start()` (line ~143), or permission not granted before `start()` runs.

**Existing handling**: `LocationPermissionDeniedDialogFragment` is shown the first time (via `GooglePlayServicesChecker`), offering Grant / Enter Manually / Later. If "Later" is chosen, no follow-up.

**New handling** (this PR): After `controller.start()`, `DynamicStarMapActivity` calls `maybeShowLocationWarning()`. If `locationController.isLocationUnset()` and `lastStatus == PERMISSION_DENIED`, a Snackbar is shown with a "Fix" button that re-opens `LocationPermissionDeniedDialogFragment`.

### 2. Location provider disabled (location off in system settings)
**Trigger**: `getBestProvider(criteria, true)` returns null but `getBestProvider(criteria, false)` is non-null.

**Existing handling**: `getSwitchOnGPSDialog()` is shown, offering to open Location Settings. If cancelled, `NO_AUTO_LOCATE` is set to true and `setLocationFromPrefs()` is called.

**New handling**: No additional Snackbar — the existing dialog is sufficient. `NO_PROVIDER` status is not checked in `maybeShowLocationWarning()` (the dialog already handles it).

### 3. Manual mode with default coordinates
**Trigger**: User enables "Set location manually" (`NO_AUTO_LOCATE = true`) in Settings without ever entering coordinates. SharedPreferences default value for "latitude"/"longitude" is the string `"0"`.

**Existing handling**: None — completely silent, map defaults to Null Island (0°N, 0°E).

**New handling** (this PR): `setLocationFromPrefs()` detects `"0"/"0"` prefs and sets `lastStatus = MANUAL_NO_COORDS`. Snackbar shown with "Fix" button that opens `EditSettingsActivity`.

### 4. No location provider available at all
**Trigger**: Both `getBestProvider(criteria, true)` and `getBestProvider(criteria, false)` return null.

**Existing handling**: Falls through to `setLocationFromPrefs()` silently.

**New handling**: This case is rare (device has no location hardware) and is not addressed in this PR.

## LocationStatus Enum

`LocationController.LocationStatus` records the outcome of the most recent `start()` call:

| Value | Meaning |
|-------|---------|
| `OK` | Location acquired successfully (or `start()` hasn't finished yet — default) |
| `PERMISSION_DENIED` | `SecurityException` in `start()` |
| `NO_PROVIDER` | Not currently used for Snackbar (existing dialog handles it) |
| `MANUAL_NO_COORDS` | Manual mode, prefs are both `"0"` |

`lastStatus` is reset to `OK` at the start of each `start()` call, so it always reflects the most recent attempt.

## Snackbar Behaviour

`DynamicStarMapActivity.maybeShowLocationWarning()` is called from `onResume()` after `controller.start()`. It shows a Snackbar only if:

1. `locationController.isLocationUnset()` — model location is still (0.0, 0.0), AND
2. `lastStatus` is `PERMISSION_DENIED` or `MANUAL_NO_COORDS`

The Snackbar has a "Fix" action button:
- Permission denied → shows `LocationPermissionDeniedDialogFragment`
- Manual no coords → opens `EditSettingsActivity`

## Key Constants and Files

| Item | Location |
|------|----------|
| `NO_AUTO_LOCATE` pref key | `LocationController.NO_AUTO_LOCATE = "no_auto_locate"` |
| Manual lat/long pref keys | `"latitude"`, `"longitude"` (default `"0"`) |
| Root view for Snackbar | `R.id.main_sky_view_root` in `skyrenderer.xml` |
| Permission dialog | `LocationPermissionDeniedDialogFragment` |
| Settings entry point | `EditSettingsActivity` |

## Out of Scope

- Migrating from deprecated `Criteria`/`LocationManager` to `FusedLocationProvider`
- Adding `ACCESS_FINE_LOCATION` permission
- Handling no-hardware case with a dedicated UI
