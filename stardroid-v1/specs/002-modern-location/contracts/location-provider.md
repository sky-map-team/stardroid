# Contract: LocationProvider Interface

**File**: `app/src/main/java/com/google/android/stardroid/control/LocationProvider.kt`  
**Scope**: Injected per Activity (via Hilt); one active instance per `LocationManagementActivity`
session and one per `DynamicStarMapActivity` session.

---

## Interface

```kotlin
interface LocationProvider {
    fun startUpdates(minDistanceMetres: Float, onUpdate: (LatLong, Float?) -> Unit)
    fun stopUpdates()
    fun isAvailable(): Boolean
}
```

---

## Behaviour Contract

### `isAvailable(): Boolean`

- Returns `true` if at least one location provider is enabled on the device.
- Returns `false` if all providers are disabled or the device has no location hardware.
- MUST NOT require permission to call; MUST NOT throw.
- Called before `startUpdates()` to determine whether to show the hardware-unavailable state.

### `startUpdates(minDistanceMetres, onUpdate)`

- Registers for continuous location updates.
- `onUpdate` fires on the **main thread**.
- The implementation MAY suppress callbacks for position changes smaller than
  `minDistanceMetres`; the caller MUST also apply its own distance gate (2,000 m) for robustness.
- MUST be idempotent — calling `startUpdates` when already active replaces the callback without
  creating duplicate listeners.
- MUST NOT be called without location permission; the caller (`LocationController`) is
  responsible for checking permission before calling.

### `stopUpdates()`

- Removes all active location listeners/callbacks.
- MUST be idempotent — safe to call when not active.
- MUST be called by `LocationController.stop()` (called from `onPause()`) to prevent battery
  drain when the app is backgrounded.

---

## GMS Implementation (`gms/control/FusedLocationProvider.kt`)

- Uses `FusedLocationProviderClient.requestLocationUpdates()` with:
  - Priority: `Priority.PRIORITY_BALANCED_POWER_ACCURACY`
  - Min update interval: none (OS-managed)
  - Min distance: `minDistanceMetres` passed to `LocationRequest`
- `isAvailable()`: Returns `true` if `LocationServices.getFusedLocationProviderClient` can
  return a result (always true on GMS devices with Play Services).

## fdroid Implementation (`fdroid/control/PlatformLocationProvider.kt`)

- The app only ever holds `ACCESS_COARSE_LOCATION`. `GPS_PROVIDER` unconditionally requires
  `ACCESS_FINE_LOCATION` at the OS level, so it MUST NOT be used — registering a listener on it
  without FINE throws an uncaught `SecurityException` at request time.
- Registers a `LocationListener` on a single OS-version-guarded provider: `FUSED_PROVIDER` on API
  31+ (blends all available sources, including GPS, and the platform auto-coarsens the output to
  match the app's granted permission), or `NETWORK_PROVIDER` below API 31 (no coarse-safe
  GPS-derived fix exists pre-31).
- `isAvailable()`: Returns `true` if that same provider is enabled.
- Requests to `requestLocationUpdates` also catch `SecurityException` defensively, in addition to
  `IllegalArgumentException`, so a future provider/permission mismatch can't crash the app.

---

## Hilt Bindings

```kotlin
// In gms/ Hilt module:
@Binds @ActivityScoped
abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

// In fdroid/ Hilt module:
@Binds @ActivityScoped
abstract fun bindLocationProvider(impl: PlatformLocationProvider): LocationProvider
```
