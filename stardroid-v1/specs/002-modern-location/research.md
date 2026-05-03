# Research: Modern Location Handling

## Location Provider Strategy (GMS vs fdroid)

**Decision**: Interface-based `LocationProvider` with two flavor-specific implementations.

- **GMS**: `FusedLocationProviderClient` via `play-services-location:21.3.0` (already in GMS
  flavor `build.gradle`). Automatically selects the best provider (GPS, network, Wi-Fi) based on
  device state. No user-facing provider selection needed. Supports `getCurrentLocation()` for an
  immediate best-effort fix and `requestLocationUpdates()` for continuous updates.

- **fdroid**: `android.location.LocationManager` with two simultaneous listeners: `GPS_PROVIDER`
  and `NETWORK_PROVIDER`. The first update received wins per location event; the controller
  accepts whichever provider delivers a fix first, then compares subsequent updates by accuracy.

**Rationale**: Fused location is strictly better for GMS (lower battery, faster first fix). The
existing code already uses `LocationManager` with `Criteria`; the fdroid implementation is a
direct evolution of that approach. Abstracting behind `LocationProvider` keeps the rest of the
system flavor-agnostic.

**Alternatives considered**: A single `LocationManager` path for both flavors was considered but
rejected: it cannot use Fused on GMS, making GMS accuracy and battery worse than necessary. A
single Fused path was rejected: `play-services-location` cannot appear in `main/` or `fdroid/`
source sets (Constitution III).

---

## Distance Threshold

**Decision**: 2,000 metres (2 km) minimum distance before triggering a map update and location
toast.

**Rationale**: The existing `LocationController.java` constant
`MINIMUM_DISTANCE_BEFORE_UPDATE_METRES = 2000` already encodes this value and aligns with the
spec requirement of 1–5 km. A star map's visible sky changes meaningfully only when the observer
moves far enough for the horizon azimuth to shift noticeably (~city-scale). Below 2 km, the
displayed sky is effectively identical. This constant is surfaced as
`ApplicationConstants.LOCATION_UPDATE_MIN_DISTANCE_METRES` in the new system.

**Alternatives considered**: 5 km was considered (upper bound of spec range) but rejected as too
coarse — a user travelling between two cities 8 km apart would not get an update until well past
their destination. 500 m was considered but generates unnecessary recalculations and toasts for
users walking locally.

---

## Acquiring Timeout

**Decision**: 30 seconds. After 30 s with no fix, show `AcquiringLocationTimeoutDialogFragment`
offering "Keep waiting" or "Enter manually."

**Rationale**: 30 s is sufficient for GPS cold-start indoors to fail and network providers to
either succeed or time out. It avoids showing the North Pole placeholder for so long that the
user thinks the app is broken. Hardcoded as
`ApplicationConstants.LOCATION_ACQUIRING_TIMEOUT_MS = 30_000L`.

**Implementation note**: The timeout is a `Handler.postDelayed()` call started when
`LocationController` enters the `Acquiring` state. It is cancelled when any fix arrives. If the
timeout fires and the user taps "Keep waiting," the timer resets for another 30 s cycle.

---

## North Pole Placeholder

**Decision**: When automatic location is enabled but no prior location exists (no previous
session), `AstronomerModelImpl` initialises to `LatLong(90f, 0f)` (North Pole) rather than
`LatLong(0f, 0f)` (Gulf of Guinea).

**Rationale**: The Gulf of Guinea is a plausible mid-ocean location — users may not realise the
sky is wrong. The North Pole produces an obviously incorrect circumpolar sky for most users
(star trails centred on the zenith, no rising/setting stars), making it immediately apparent that
location is not yet set. This directly satisfies FR-005 and SC-003.

**Alternatives considered**: A blank/black sky was considered but rejected because it would mask
rendering bugs and confuse users about whether the app is working at all.

---

## Geocoding Strategy

**Decision**: Android platform `android.location.Geocoder`, no new dependency.

**Rationale**: `Geocoder` is available since API 1, requires no API key, and is available on all
GMS and most fdroid devices. It requires internet connectivity for most queries. When the geocoder
is unavailable (offline, no backend, `Geocoder.isPresent()` returns false), the manual entry UI
falls back to showing only the lat/lon fields with the place name field hidden or disabled.

**Ambiguous results**: When `getFromLocationName()` returns multiple results, the first result is
shown on the map/display for user confirmation before being applied. A "Try a more specific name"
hint is shown beneath the result.

**Alternatives considered**: Nominatim (OpenStreetMap geocoder) via HTTP was considered for
fdroid. Rejected: requires network; adds a new HTTP dependency; returns results in an
undocumented schema; introduces a third-party service dependency that could disappear.

---

## Permission Flow

**Decision**: Request `ACCESS_COARSE_LOCATION` only. Fine/precise location is not requested in
this revision.

Three distinct permission states are handled:

1. **Not-yet-requested**: Show `LocationPermissionRationaleDialogFragment` before the system
   dialog. Contains a human-readable explanation and "Grant" / "Enter manually" / "Later" buttons.
   "Grant" triggers `ActivityResultContracts.RequestPermission` for `ACCESS_COARSE_LOCATION`.

2. **Denied (re-askable)**: System dialog can still appear.
   `shouldShowRequestPermissionRationale()` returns `true`. Show the rationale dialog again on
   next attempt.

3. **Permanently denied**: `shouldShowRequestPermissionRationale()` returns `false` after a
   failed request attempt (Android 11+ "Don't ask again" or second denial).
   Show `LocationPermissionPermanentlyDeniedDialogFragment` with an "Open App Settings" button
   that fires `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`.

**Rationale**: This is the standard Android permission best-practice pattern. The `rationale →
request → result` cycle correctly handles all OS variants from API 26 to 36.

**Existing code relationship**: `AbstractGooglePlayServicesChecker.java` currently handles the
permission request; its location-permission logic is removed and replaced by the flow above.
The GMS availability check (non-location) in `GooglePlayServicesChecker.java` is retained.

### Known Limitation: Offline GPS on fdroid (Deferred)

A code review raised the following concern: on Android 12+ (API 31+),
`LocationManager.GPS_PROVIDER` requires `ACCESS_FINE_LOCATION`. With coarse-only permission,
the fdroid build (which uses `LocationManager` directly) cannot access GPS. In remote areas
with no cell or Wi-Fi coverage, offline location will therefore fail on the fdroid build on
Android 12+.

**GMS build**: `FusedLocationProviderClient` is believed to use GPS internally and return a
coarsened result even with coarse-only permission, but this has not been verified empirically.
The GMS path is assumed to work offline; this assumption should be confirmed in testing.

**Decision**: Proceed with coarse-only for now. A future revision should:
1. Empirically verify `FusedLocationProviderClient` offline behaviour under coarse-only permission.
2. Add `ACCESS_FINE_LOCATION` support to the fdroid build once the coarse path is validated,
   so that fdroid users in remote areas can also get offline GPS.

**Existing code relationship**: `AbstractGooglePlayServicesChecker.java` currently handles the
permission request; its location-permission logic is removed and replaced by the flow above.
The GMS availability check (non-location) in `GooglePlayServicesChecker.java` is retained.

---

## Maps SDK (GMS only)

**Decision**: Add `play-services-maps` to the `gms` flavor Gradle dependencies.

**Rationale**: FR-014 requires a map view in the location management screen. `play-services-maps`
is the only viable offline-capable interactive map solution for the GMS build. It is consistent
with the existing `play-services-location` and `firebase-*` dependencies already present in the
GMS flavor. The fdroid build shows text-only coordinates (per spec clarification).

**Integration approach**: `activity_location_management.xml` in `main/res/layout/` uses a
`FrameLayout` stub where the map container sits. The GMS overlay layout at
`app/src/gms/res/layout/activity_location_management.xml` replaces that stub with a `MapView`.
`LocationManagementActivity.kt` in `main/` initialises the map through a thin `LocationMapView`
interface; the GMS implementation wraps `MapView`, the fdroid implementation is a no-op that
leaves the stub empty (replaced by the text-coordinate display).

---

## Existing Code Reuse

| Component | Decision |
|---|---|
| `LatLong.kt` | Reused unchanged — existing data class |
| `LocationStatus` enum | Replaced by `LocationState` sealed class (richer states) |
| `MINIMUM_DISTANCE_BEFORE_UPDATE_METRES` | Extracted to `ApplicationConstants` as `LOCATION_UPDATE_MIN_DISTANCE_METRES`; value unchanged (2000f) |
| `setLocationInModel()` / toast logic | Ported to `LocationController.kt` |
| `no_auto_locate` / `latitude` / `longitude` / `location` prefs | All preserved; migrated to new controller on first read |
| `force_gps` pref | Removed; existing stored value is ignored on upgrade |
| `LocationController.LOCATION_UPDATE_TIME_MILLISECONDS` (10 min) | Removed; FusedLocation handles its own update batching; `LocationManager` path uses OS-default |
