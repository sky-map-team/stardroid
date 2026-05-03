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

**Decision**: Both `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` are requested together.
On Android 12+ this shows a single system dialog offering "Precise" or "Approximate"; on
pre-12 it shows the standard location dialog granting full GPS access. The app accepts
whichever level the user grants and adapts accordingly.

Four distinct permission states are handled:

1. **Not-yet-requested**: Show `LocationPermissionRationaleDialogFragment` before the system
   dialog. Rationale text explains that precise location enables GPS for use in remote
   areas (no network); approximate location is sufficient for general use. Buttons:
   "Grant" / "Enter manually" / "Later".
   "Grant" triggers `ActivityResultContracts.RequestMultiplePermissions` for both permissions.

2. **Approximate granted** (coarse only, fine denied): System granted `ACCESS_COARSE_LOCATION`.
   Use network-based providers only. GPS is unavailable. App works normally in areas with
   cell/Wi-Fi; surfaces a clear status if offline with no network providers.

3. **Denied (re-askable)**: Neither permission granted; `shouldShowRequestPermissionRationale()`
   returns `true`. Show the rationale dialog again on next attempt.

4. **Permanently denied**: `shouldShowRequestPermissionRationale()` returns `false` after a
   failed request attempt. Show `LocationPermissionPermanentlyDeniedDialogFragment` with an
   "Open App Settings" button that fires `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)`.

**Rationale**: On Android 12+ (API 31+), `GPS_PROVIDER` is gated behind
`ACCESS_FINE_LOCATION`. Requesting coarse-only silently breaks offline use in remote areas — the
primary dark-sky observing context. Requesting both together lets users who care about offline GPS
grant precise, while users who prefer privacy can grant approximate; the app works in either case.
This resolves the contradiction between FR-001 and FR-016 identified in review.

**`LocationState` mapping for permission outcomes:**
- Precise granted → proceed to `Acquiring` (GPS + network available)
- Approximate only granted → proceed to `Acquiring` (network only; `LocationState` carries a
  `permissionLevel` flag so `LocationController` can surface a degraded-offline warning)
- Denied re-askable → `PermissionDenied`
- Permanently denied → `PermissionPermanentlyDenied`

The `rationale → request → result` cycle correctly handles all OS variants from API 26 to 36.

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
