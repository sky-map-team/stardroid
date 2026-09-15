# Detailed Design: Layer System and `:app` Decomposition

**Status: IMPLEMENTED** (through slice 23 / D58; comets still to come) — detailed
design increment 3. `SkyLayer` + `CatalogLayer` (three instances) are implemented per D35; the
grid, ecliptic, horizon, and solar-system layers per D37 (with `LayerStrings` where this doc
sketched a `nameRes`/`strings` dependency), restyled per D40 (upstream color scheme, graduated
ecliptic, horizon glow via the new `GlowPrimitive`; colors centralized in `SkyColors`); the
Compose map screen, `MapViewModel` + `LayersViewModel`, `LayerRegistry`/DataStore toggles, and
the `RenderBinder` (this doc's "RenderConnector" role) per D38, with the singleton graph on
Hilt since D59; the sky-gradient render state per D41; time travel and the shared clock bus per
D42; search per D43; location (`LocationViewModel` + the app-scoped `LocationController` bus)
per D44; object info (`ObjectInfoViewModel` + `IdentifyGeometry`) per D45; settings
(`SettingsViewModel` + the consumer wiring) per D46; the meteor-shower layer per D58 (slice
23). Comets are still to come.

Source baseline: v1 `DynamicStarMapActivity` (1,456 lines), `ControllerGroup`, `LayerManager`,
the ten active layers, `touch/`, `views/`, and the dialog fragments.

## The layer system

Layers live in `:app` initially (per the high-level design) as plain classes — no Android
types in their constructors beyond what interfaces hide — so they unit-test against fake
contexts and could move to a shared module later.

```kotlin
interface SkyLayer {
    val id: LayerId
    val depth: Int                            // v1 depth table carried over
    val nameRes: StringRef                    // for the toggle UI
    fun scenes(): Flow<LayerScene>
}
```

**Layers declare their dependencies via their constructors** — there is no shared
"LayerContext" bundle. A context object handing every layer everything (ephemeris included)
would be a service locator in disguise: static layers don't need an ephemeris, and hiding
which layer uses what defeats both testing and the constructor-injection principle the core
modules follow. Instead each layer takes exactly what it consumes:

```kotlin
class CatalogLayer(id, depth, kind: LayerKind, catalog: CatalogRepository,
                   locale: Flow<LocaleSpec>, mapping: PrimitiveMapping) : SkyLayer
class SolarSystemLayer(ephemeris: Ephemeris, clock: Flow<Instant>,
                       locale: Flow<LocaleSpec>, images: BodyImageMapper) : SkyLayer
class GridLayer(strings: LayerStrings) : SkyLayer
class HorizonLayer(clock: Flow<Instant>, location: Flow<LatLong>,
                   strings: LayerStrings) : SkyLayer
```

`LayerRegistry` owns the set; visibility toggles live in DataStore (new keys; v1
`source_provider.N` keys are not migrated, per D1). Toggling subscribes/unsubscribes the
layer's flow and `submit(id, null)`s on disable. The registry is constructed in DI but
designed so a future downloaded pack can contribute catalog-backed layers as data.

### The ten v1 layers in v2 terms

| v1 layer | v2 implementation |
|---|---|
| Stars, Deep-sky, Constellations | **One `CatalogLayer` class, three instances** — each with its own `LayerId`, depth, name, and DataStore toggle, so they remain independently toggleable exactly as in v1. Parameterized by `LayerKind` + a primitive mapping (stellar points / icon points / figure lines + labels). Re-emits on catalog or locale change; ignores the clock. |
| Solar System | `SolarSystemLayer`: per-body positions from `Ephemeris`; re-submits when any body moves past a threshold (derived from `maxAngularVelocityDegPerDay`, so the Moon updates often, Neptune almost never); images ordered by descending Earth-distance per D18; Moon image picked by phase bucket; Pluto included (D16). |
| Meteor Showers | `MeteorShowerLayer`: radiant icon points, active-window logic on the clock (date-dependent, not per-frame). Radiant data moves to the DB as catalog objects with an `active_from/to` sidecar table — it's static annual data, no reason to stay code. |
| Comets | `CometsLayer`: v1's time-interpolated entries, ported as-is with its hardcoded data (transient-object downloads are the future replacement; not worth a schema detour now). |
| Grid | `GridLayer`: computed RA/Dec graticule lines + coordinate labels; depends only on locale (label text); static otherwise. The equator label at RA 0h reads "0" — it doubles as the ecliptic's vernal-equinox degree label (D38). |
| Horizon | `HorizonLayer`: horizon great circle + zenith/nadir + cardinal labels from `SkyModel.localFrame`, plus the additive glow mesh just below the horizon (a `GlowPrimitive`, D38); line, glow, and labels share one green. Re-submits on location change and a slow clock tick (the horizon drifts ~0.25°/min in celestial coords; time travel accelerates the tick like everything else). |
| Ecliptic | `EclipticLayer`: static graduated Star Gold line — opaque, ticked every 10° with degree labels at the 30° zodiac boundaries — at depth 5 so it stays behind the catalog layers (D38). |
| Sky Gradient | **Not a layer.** See below. |

### Sky gradient: a render-state, not a scene

v1's `SkyGradientLayer` draws a sun-altitude-dependent gradient dome. When this section was
first written the primitive set (points/lines/images/labels) had no mesh primitive, and
inventing one for a single internal use would have been API surface without a producer. D38
has since added `GlowPrimitive` (concentric-ring gradient meshes, additive blending) for the
horizon glow — but the sky gradient remains a render-state, not a scene: it is a whole-sky
dome keyed to the sun's *horizontal* position, camera-independent per frame, and alpha-blended
rather than additive, so the backend still owns its geometry exactly as v1's renderer did.

```kotlin
data class RenderState(..., val skyGradient: SkyGradient? = null)
data class SkyGradient(val sunDirection: Vector3)   // unit, celestial coords
```

The app computes the sun's geocentric direction (ephemeris) on the slow tick; the backend
owns the dome geometry/colors exactly as v1's renderer did. The direction is a celestial
vector, not alt/az — the backend draws in celestial coordinates and has no local frame to
convert horizontal coordinates with (D41; an earlier sketch here said alt/az). The user's
show-sky-gradient preference simply leaves it null. If a future feature needs real meshes
(Milky Way extent, light-pollution dome — both on the roadmap), a `MeshPrimitive` gets added
*then*, with the gradient as a candidate migration.

### Search arrow and crosshair

The whole search overlay — dim, crosshair, and arrow — is *screen-space 2D UI*: one Compose
Canvas above the GL surface, recomputed per camera update (implemented per D43). It uses the
**same pure `SkyProjection` the backend uses** (D21, G2): `worldToScreen(target)` places the
crosshair and decides on-screen vs. off-screen, and the camera's right/up axes give the arrow
bearing — one projection implementation, so the overlay and the rendered sky never disagree.
No GL involvement (the projection is CPU-side), trivially previewable, and the night-mode
tint is applied directly. v1's `SearchArrow`/`CrosshairOverlay` GL specials are deleted, per
the render-api doc.

An earlier sketch here made the crosshair a sky-anchored one-icon overlay `LayerScene`; D43
rejected that — v1's 1 Hz crosshair pulse would need ~30 Hz scene resubmission and an icon
tint the render API deliberately lacks, while the Canvas already redraws with every camera
frame.

## `:app` decomposition

v1's `DynamicStarMapActivity` mixes ~16 responsibilities. v2 is a single-activity Compose
app; the map screen splits into focused ViewModels (Hilt-injected, no GL/sensor types):

| ViewModel | Owns | v1 origin |
|---|---|---|
| `MapViewModel` | Reference frame (D14: sensor/manual), camera resolution from orientation+frame+FOV, zoom, auto/manual toggle, pointing state for the UI | `ControllerGroup`, `RendererModelUpdateClosure`, `setAutoMode`, touch handlers |
| `SearchViewModel` | Query → ranked hits, target selection/resolution (own→parent position), search-mode state, arrow bearing derivation | `doSearchWithIntent`, search fields, dialogs |
| `TimeTravelViewModel` | Time state machine (normal ↔ transitioning ↔ travelling), rate stepping, preset events (incl. `nextLunarPhaseEvent`) | `setTimeTravelMode`, `TimeTravelClock` wiring, time player UI state |
| `LayersViewModel` | Toggle states ↔ DataStore, registry wiring | `LayerManager` + preference listening |
| `LocationViewModel` | Location flow, permission state machine, manual-entry, warnings | `wireLocationController`, permission launcher, location dialogs |
| `ObjectInfoViewModel` | Card content per id + locale, see-also links, tap-to-identify hit results | `education/`, `showObjectInfoDialog`, `onSeeAlsoClicked` |

Screens beyond the map (Settings, Gallery, Diagnostics, onboarding) are Navigation-Compose
destinations with their own ViewModels; all v1 dialog fragments become Compose dialogs or
bottom sheets owned by their feature's state.

### What remains outside ViewModels (thin, single-purpose)

- **`OrientationSource`** (sensors → `Flow<Matrix3>`, rotation-vector + legacy fusion +
  damping) and **`MagneticDeclinationSource`** — Android edges from the core design doc.
- **`RenderConnector`**: collects enabled layers' scenes into `SkyRenderer.submit`, camera
  flow into `setCamera`, and night mode/label scale/magnitude limit/sky gradient into
  `setRenderState`. The only class that touches both Flows and the renderer.
- **Gestures**: v1's `touch/` package (drag/fling/pinch interpreting, `Flinger` momentum)
  ports behind Compose `pointerInput`, emitting pan/zoom/rotate deltas to `MapViewModel`.
- **Effects**: wake-lock holder, screen-flash composable, immersive-mode/auto-hiding controls
  (v1 `FullscreenControlsManager` becomes Compose state + insets APIs), session analytics.
  (Time-travel's sound player was removed — D111.)

### Details that must not get lost in the port

- **Pole text-angle freeze**: v1's render closure freezes label orientation within ~20° of
  the celestial poles (unfreezing at 30° — deliberate hysteresis) to stop labels spinning
  wildly. This is per-frame, camera-derived → it moves into the backend's label drawing,
  recorded here so it survives.
- **Night mode** spans both worlds: Compose theme (UI chrome) + `RenderState.nightMode`
  (sky). One DataStore preference drives both.
- **Sensor-absence fallback**: no-sensor devices boot into manual mode with the v1 warning
  (now a dialog driven by `MapViewModel` capability state).
- **System search integration** (`SearchTermsProvider` content provider, `searchable.xml`)
  is retained: the provider queries `CatalogRepository.searchByPrefix` directly.

## Testing

- Layer tests: collect `scenes()` with fake context flows; assert primitive contents and
  re-submission cadence under accelerated clocks (time-travel behavior, D13).
- ViewModel tests: pure JVM with fake repositories/sources — the point of the decomposition.
- `RenderConnector` integration test against the recording fake renderer from the render-api
  doc.
- One Compose UI test per screen for the critical path (map controls, search flow, time
  travel), Espresso retired with the XML it tested.
