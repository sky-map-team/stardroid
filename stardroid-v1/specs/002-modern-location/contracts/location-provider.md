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

- Registers `LocationListener` on `GPS_PROVIDER` and `NETWORK_PROVIDER` independently.
- `isAvailable()`: Returns `LocationManager.isProviderEnabled(GPS_PROVIDER) ||
  LocationManager.isProviderEnabled(NETWORK_PROVIDER)`.
- On update: accepts the first arrival; ignores subsequent arrivals from the same event window
  unless they come from a provider with better stated accuracy.

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
