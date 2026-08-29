# Detailed Design: Map HUD (pointing readout)

**Status: IMPLEMENTED (D66)** — the open questions were resolved on 2026-07-27, all as
proposed (see Decisions at the end; recorded as D65 in `decisions.md`), and the HUD landed
on `feature/map-hud`. Spawned by the AR-mode decisions (`camera-ar-mode.md`, D64): the
sensor-alignment correction surfaces in "a HUD which appears only while the on-screen
controls are visible", alongside other useful readouts (field of view, current RA/Dec).
This doc designs that HUD.

An interactive mockup accompanies this doc: open `mockups/map-hud.html` in a browser. It
simulates the HUD riding the chrome show/hide cycle, handheld sensor wobble, night mode,
the AR camera layer, time-travel coexistence, and the correction row with reset-and-undo.

## What it is

A compact, glanceable readout on the map showing where the map is pointed and how the view
is configured. It is **information chrome**: it lives inside the existing chrome
`AnimatedVisibility` container in `MapScreen`, so it appears on the entry flash and on
sky-tap exactly when the rail and action cluster do, hides with them (including during
search), and red-shifts in night mode through the theme (D46). No new visibility state.

## Contents (rows, top to bottom)

1. **RA / Dec** of the screen centre — `RaDec.fromGeocentricVector(camera.lineOfSight)`,
   exactly as `DiagnosticsViewModel` computes its `pointing` field today.
2. **Alt / Az** of the screen centre — the same vector expressed in the observer's local
   frame (`SkyModel.localFrame(time, location)`); azimuth with a cardinal suffix. Uses the
   map's clock, so it stays truthful during time travel.
3. **FOV** — `SkyCamera.fovDeg` (short-side FOV, D21). While the AR camera layer is on and
   the FOV is camera-locked (`camera-ar-mode.md`), a lock glyph marks it.
4. **Alignment correction** — shown **only when nonzero**: the az/alt pair set by AR
   drag-to-align (D64 keys `sensorAzimuthAdjustmentDeg` / `sensorAltitudeAdjustmentDeg`),
   with a small **Reset** icon button. Reset zeroes both preferences and posts a snackbar
   ("Alignment reset — UNDO"), mirroring the drag receipt. This row is the correction
   visibility surface decided in D64.

Deliberately excluded: location and sensor health (diagnostics owns those), the clock (the
time-travel player is the clock surface, top-centre), and constellation-under-crosshair
(needs IAU boundary data we don't ship; a future catalog pack could revisit).

## Placement

**Top-right corner**, padded by `WindowInsets.safeDrawing` (cutout-aware, R2.2/R2.4
lessons). Rationale by elimination:

- Top-centre belongs to the time-travel player.
- Left edge belongs to the layer rail, which is vertically centred and, on short landscape
  screens, consumes nearly the full height (the D57 rail budget) — a top-left HUD would
  collide there.
- Zone B occupies the bottom-right in portrait but moves to a bottom-anchored right-edge
  column in landscape, leaving the top-right corner free in **both** orientations.

## Visual treatment

Matches the rail's material: a translucent panel (~60% scrim over the sky, hairline
outline, same corner radius family), right-aligned text in tabular figures
(`FontFeature`/`tabular-nums`) so values tick without jitter, labels in small
`labelSmall`-ish caps. Day mode: dim neutral text with the theme primary reserved for the
correction row; night mode: everything through the red scheme like the rest of the chrome.
Target width ≈ 120–140 dp; four rows ≈ 88 dp tall — comparable footprint to the
time-travel player, but in a corner.

## Data plumbing

- `MapViewModel` already exposes `camera: StateFlow<SkyCamera>`. Add a derived, throttled
  `hudState: StateFlow<HudState?>` (RA/Dec, alt/az, FOV, correction, camera-locked flag),
  sampled at ~150 ms like the diagnostics flows — per-frame camera updates must not drive
  per-frame text recomposition.
- Alt/az: `localFrame` is already computed in `collectSensors()` for sensor mode; manual
  mode calls `SkyModel.localFrame` with the same location/time inputs. Degenerate case
  (no location fix yet): show the row as `—`.
- Correction: the two D64 preferences observed from `Settings`; reset is a `MapViewModel`
  function caching the prior pair for snackbar undo.

## Decisions (2026-07-27, recorded as D65)

1. **Corner**: top-right.
2. **RA format**: astronomy-conventional hours/minutes (`18h 37m`); Dec, Alt, and the
   correction stay signed decimal degrees.
3. **Alt/Az rows**: included.
4. **Visibility**: rides the chrome only — no Layers-sheet Display toggle unless feedback
   demands one.
5. **Correction row**: strictly hidden at zero; no "aligned" state.

## Slicing

Small enough for **one slice**: `MapHud` composable + `hudState` in `MapViewModel` +
reset-with-undo + unit tests (formatting, throttling, zero-correction row logic). It has
no dependency on the AR slices — RA/Dec, alt/az, and FOV are useful today — but the
correction row only becomes reachable once AR slice 2 (drag-to-align) lands.
