# Data Model: Modern Location Handling

## Overview

The location system introduces a `LocationState` sealed class to replace the existing
`LocationStatus` enum, a `LocationSource` enum, and a `LocationProvider` interface. All other
types (`LatLong`, SharedPreferences keys) are reused or migrated from the existing system.

---

## LocationState (new — `control/LocationState.kt`)

Represents the complete state of the location subsystem at any point in time. Replaces the
`LocationStatus` enum (`OK`, `PERMISSION_DENIED`, `NO_PROVIDER`, `MANUAL_NO_COORDS`).

```kotlin
sealed class LocationState {

    /** No location has ever been set and none is being acquired. */
    object Unset : LocationState()

    /**
     * Auto-location is active; waiting for first fix (or after location loss).
     *
     * @param preciseGranted True if ACCESS_FINE_LOCATION was granted (GPS available offline);
     *                       false if only ACCESS_COARSE_LOCATION granted (network providers only).
     */
    data class Acquiring(val preciseGranted: Boolean) : LocationState()

    /**
     * A valid location is available and active.
     *
     * @param location  The geographic coordinates.
     * @param source    Whether the location was obtained automatically or entered manually.
     * @param accuracy  Estimated accuracy in metres (null for manually entered locations).
     * @param timestamp System.currentTimeMillis() when this location was last confirmed.
     */
    data class Confirmed(
        val location: LatLong,
        val source: LocationSource,
        val accuracy: Float?,
        val timestamp: Long
    ) : LocationState()

    /**
     * Location permission was denied; the system dialog can still be shown again
     * (shouldShowRequestPermissionRationale() == true, or first denial).
     */
    object PermissionDenied : LocationState()

    /**
     * Location permission was permanently denied; the system dialog will not appear.
     * User must be directed to app system settings.
     */
    object PermissionPermanentlyDenied : LocationState()

    /** Device has no location providers available (no GPS, no network provider). */
    object HardwareUnavailable : LocationState()

    /**
     * Automatic location was active, 30 s elapsed with no fix, and the user has not
     * yet responded to the timeout prompt. Used to trigger the timeout dialog.
     */
    object AcquiringTimeout : LocationState()
}
```

**State transition rules:**

| From | Event | To |
|------|-------|----|
| `Unset` | Auto mode enabled, permission granted | `Acquiring` |
| `Unset` | Manual location entered | `Confirmed(source=MANUAL)` |
| `Unset` | Permission check fails (denied) | `PermissionDenied` |
| `Unset` | Permission check fails (permanent) | `PermissionPermanentlyDenied` |
| `Unset` | No providers available | `HardwareUnavailable` |
| `Acquiring` | Fix received | `Confirmed(source=AUTO)` |
| `Acquiring` | 30 s timeout with no fix | `AcquiringTimeout` |
| `Acquiring` | Permission revoked (foreground return) | `PermissionDenied` |
| `AcquiringTimeout` | User taps "Keep waiting" | `Acquiring` (timer reset) |
| `AcquiringTimeout` | User enters manual location | `Confirmed(source=MANUAL)` |
| `Confirmed(AUTO)` | New fix exceeds 2,000 m distance | `Confirmed(AUTO)` (updated) |
| `Confirmed(AUTO)` | Permission revoked on foreground return | `PermissionDenied` |
| `Confirmed(AUTO)` | User switches to manual | `Confirmed(MANUAL)` |
| `Confirmed(MANUAL)` | User switches to auto, permission granted | `Acquiring` |
| `PermissionDenied` | User re-grants permission | `Acquiring` |
| `PermissionPermanentlyDenied` | User opens app settings + grants | `Acquiring` (detected on resume) |

---

## LocationSource (new — `control/LocationState.kt`)

```kotlin
enum class LocationSource {
    /** Location obtained from device sensors/network via the platform location provider. */
    AUTO,
    /** Location entered manually by the user (coordinates or resolved place name). */
    MANUAL
}
```

---

## LocationProvider (new — `control/LocationProvider.kt`)

Interface injected by Hilt; flavor-specific implementations bound in `gms/` and `fdroid/` Hilt
modules.

```kotlin
interface LocationProvider {
    /**
     * Start requesting location updates. Callback fires on the main thread.
     *
     * @param minDistanceMetres Minimum distance change before the callback fires.
     * @param onUpdate          Called with (location, accuracyMetres?) when an update arrives.
     */
    fun startUpdates(
        minDistanceMetres: Float,
        onUpdate: (location: LatLong, accuracy: Float?) -> Unit
    )

    /** Stop all pending location update requests. */
    fun stopUpdates()

    /**
     * Returns true if any location provider is available on this device.
     * Returns false when no hardware or all providers are disabled.
     */
    fun isAvailable(): Boolean
}
```

**GMS implementation** (`gms/control/FusedLocationProvider.kt`):
Uses `FusedLocationProviderClient.requestLocationUpdates()` with a `LocationRequest` configured
for `PRIORITY_BALANCED_POWER_ACCURACY` (coarse). Converts Android `Location` to `LatLong`.

**fdroid implementation** (`fdroid/control/PlatformLocationProvider.kt`):
Registers `LocationListener` on both `GPS_PROVIDER` and `NETWORK_PROVIDER` (where available).
Accepts the first update per event; subsequent updates are accepted only if from a more accurate
provider. Returns `isAvailable() = false` if neither provider is enabled.

---

## SharedPreferences Schema

Existing keys are preserved for backward compatibility on upgrade. The `force_gps` key is
abandoned (any stored value is silently ignored).

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `no_auto_locate` | Boolean | `false` | `true` = manual mode active |
| `latitude` | String | `""` | Saved manual latitude (decimal degrees); empty = not set |
| `longitude` | String | `""` | Saved manual longitude (decimal degrees); empty = not set |
| `location` | String | `""` | Saved manual place name (display label only) |
| ~~`force_gps`~~ | ~~Boolean~~ | — | **Removed**; stored value ignored on upgrade |

**Migration on first read**: If `no_auto_locate = false` and `latitude`/`longitude` are `"0"` or
`""`, the location is treated as `Unset` (not as a valid (0,0) coordinate). The old default of
`"0"` is indistinguishable from a genuine equatorial location; the new system treats empty-or-zero
latitude defaults as unset. The first non-zero, non-empty stored value is treated as a valid
saved manual location.

---

## AstronomerModelImpl — Initial Location

Changed from `LatLong(0f, 0f)` to `LatLong(90f, 0f)` (North Pole). The North Pole produces an
obviously wrong circumpolar sky for most users until a real location is confirmed by
`LocationController` via `AstronomerModel.setLocation()`. This satisfies FR-005 and SC-003.

---

## Existing Types (unchanged)

### LatLong (`math/LatLong.kt`)

```kotlin
data class LatLong(val latitude: Float, val longitude: Float) {
    fun distanceFrom(other: LatLong): Float  // returns great-circle distance in degrees
}
```

Used as the coordinate carrier throughout. `LocationState.Confirmed.location` is of this type.
No changes required.
