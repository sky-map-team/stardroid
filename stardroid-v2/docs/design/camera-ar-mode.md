# Detailed Design: Through-Camera (AR) Mode, Drag-to-Align, and Share

**Status: ALL SLICES IMPLEMENTED (D67–D71)** — all open questions from the exploration
round were resolved on 2026-07-26 (see Decisions at the end; recorded as D64 in
`decisions.md`); slice 1 (the camera underlay, plus D69's dev exposure controls), slice 2
(drag-to-align), slice 3 (map-only Share), and slice 4 (the camera share layouts) are
implemented. This expands the two stubs already reserved in `ux-polish.md`: the
"through-camera / AR mode" Display toggle (Layers sheet) and the "Share" action (overflow
sheet).

An interactive mockup accompanies this doc: open `mockups/camera-ar-mode.html` in a browser.
It simulates the stacked-plane compositing model (Part 1, Option A) with a misaligned sky to
drag into place, the scrim/exposure controls, night-mode behavior, and the share sheet, and
records the decisions inline.

## What we're building

1. **Camera passthrough** — a toggle that puts the live rear-camera image *under* the sky
   map, so the drawn sky overlays the real one.
2. **Drag-to-align** — the sensors will never line the two up exactly; the user drags the
   map until it matches the camera view, and the correction is remembered (as the manual
   compass adjustment is today).
3. **Share** (follow-on) — capture the moment as an image (map-over-camera overlay, or map
   and camera side by side), stamped with branding and an invitation to download Sky Map.

## How it plugs into what exists

The exploration below leans on these established seams (all verified in the current tree):

- The camera is resolved *outside* the renderer (D14): sensor mode computes a `Pointing`
  via `SkyModel.pointing(localFrame, orientation, viewDirection)` in
  `MapViewModel.collectSensors()`, and everything downstream just sees a `SkyCamera`.
- Zoom is `SkyCamera.fovDeg` — full field of view across the **shorter** viewport side
  (D21); the projection derives the long side from aspect. So matching the map to the
  physical camera needs exactly one number: the camera's short-side FOV.
- The remembered manual correction today is `Settings.manualCompassAdjustmentDeg`
  (DataStore, default 0.0), applied in `MapViewModel.declinationFor()` as extra magnetic
  declination — i.e. a **yaw-only rotation about the zenith** baked into `localFrame`.
- The GL stack is a `GLSurfaceView` (GLES1, `RENDERMODE_WHEN_DIRTY`, D23) mounted as the
  first child of `MapScreen`'s root `Box`; the EGL config already requests RGBA_8888, but
  the surface is opaque today (`glClearColor(0,0,0,1)`, no translucent holder format).
- Per-frame display globals (night mode, sky gradient) travel in `RenderState` — the
  natural vehicle for a "transparent background" flag.
- The Layers sheet's Display group already hosts a non-layer boolean (`showSkyGradient`);
  `ux-polish.md` says the AR toggle lives there. The location-permission plumbing in
  `MainActivity`/`MapScreen` is the template for the camera permission.
- There is **no** camera, capture, or share code anywhere in v1 or v2 — all greenfield.

## Part 1: Camera passthrough

### Architecture: two candidate compositing models

**Option A — stacked surfaces (recommended).** A CameraX `Preview` bound to a
`PreviewView` (or plain `SurfaceView`) sits under the `GLSurfaceView` in `MapScreen`'s root
`Box`. The GL surface becomes translucent: `holder.setFormat(TRANSLUCENT)` +
`setZOrderMediaOverlay(true)` in `MainActivity`, and the renderer clears to alpha 0 (new
`RenderState.transparentBackground`; the sky-gradient dome is suppressed while it's set).
Android's hardware composer blends the two planes; the renderer, render loop
(`RENDERMODE_WHEN_DIRTY`), and GLES1 backend are otherwise untouched.

**Option B — camera as a GL texture.** The camera feeds a `SurfaceTexture` bound as a
`GL_TEXTURE_EXTERNAL_OES` texture that the renderer draws as a background quad. Full
blending freedom (multiply, additive stars, shader exposure gain, night-mode red transform
of the *camera image itself*), and share capture gets the composite for free from one
`glReadPixels`. But: the external-texture extension on **ES 1.1 fixed-function** is
obscure and poorly exercised on real devices; every camera frame forces a render
(continuous render mode — a power regression against D23); and it puts hardware-camera
plumbing inside the renderer, crossing the D13/D14 boundary.

**Decided (2026-07-26): Option A**, with Option B noted as the upgrade path if/when a
GLES2/3 backend lands (`render/api` is backend-agnostic, so nothing in Part 1's UX or
persistence would change). What Option A gives up — pixel-level control of the camera
image — is partially recoverable: a full-screen translucent **black scrim quad** drawn
first by the GL layer dims the camera arbitrarily (a "camera brightness" control), and
CameraX exposure compensation covers the rest.

Note on exposure control: needing Camera2-level control does **not** push us toward
Option B or off CameraX. `Camera2Interop` exposes any `CaptureRequest` key (AE mode, ISO,
frame duration) on a CameraX use case, so all four exposure levers below are reachable from
the stacked-surface architecture. Compositing model and exposure control are orthogonal.

### Exposure — the hard physical problem

At night the camera sees *almost nothing*. Auto-exposure will crank gain to maximum,
producing a bright, noisy, smeared image — or hunt between extremes as the phone moves past
a streetlight. Realistically visible in a phone preview stream: the Moon, planets, bright
stars (in dark skies, on recent sensors), skyline/horizon, clouds, trees. That's actually
*enough* — the horizon and the Moon are what users align against — but expectations need
managing: this is "see the sky map in context," not "see the stars amplified."

Levers, in increasing order of effort:

1. **AE compensation** (`CameraControl.setExposureCompensationIndex`) — universally
   available; surface as a small exposure slider while the layer is on.
2. **Scrim dimming** (Option A's GL scrim) — decouples "how bright is the video" from
   "what did the sensor capture"; also the night-vision-preservation control.
3. **Low Light Boost AE mode** (`CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY`,
   Android 15+, supported devices) — opt in via Camera2Interop when available; big win on
   Pixels in the exact conditions this feature targets.
4. **Manual sensor control** (Camera2Interop: AE off, max frame duration, high ISO) —
   fragile across devices; defer unless 1–3 prove insufficient.

Start with 1+2, detect-and-enable 3, defer 4.

### Blending the two images

With Option A the composite is plain source-over: camera plane, then the translucent GL
plane. The tuning knobs are on the map side, and matter more than they sound:

- Stars/lines already render with alpha blending over a now-transparent background, which
  visually approximates additive ("screen") blending against the video — bright points on
  dark video read well.
- The **scrim** is the single most useful control: 0% = pure AR, ~60% = "map first, camera
  as ghost context." A default around 30–40% with the exposure/dimmer control nearby.
- **Night mode** — decided (2026-07-26): the two modes coexist; night mode red-shifts the
  **map and chrome** exactly as it does today, and the camera image is left un-reddened
  (red-shifting it is impossible under Option A anyway, and not required). Because a
  bright auto-exposed video feed is effectively a white-light torch, night mode also
  imposes a **scrim floor**: the camera dimmer is raised to a minimum of ~65% while night
  mode is on (the user can dim further, not brighter), protecting dark adaptation.

### Matching zoom to the camera

- Derive the camera's short-side FOV from `CameraCharacteristics`
  (`SENSOR_INFO_PHYSICAL_SIZE`, active focal length): `fov = 2·atan(shortSide / 2f)`,
  corrected for the preview's crop (a `FILL_CENTER` `PreviewView` crops to the view
  aspect; compute against the *cropped* sensor region). Typical main-camera short-side FOV
  is ≈ 45–50° — pleasingly close to the map's `INITIAL_FOV_DEG = 45`.
- While the layer is on, the map's FOV is **locked to the camera's**: pinch drives CameraX
  `zoomRatio` (digital zoom, possibly lens switching on multi-camera devices), and the map
  FOV follows `zoomState` (`fov ≈ baseFov / zoomRatio`, or exact per-lens recompute when
  the active lens changes). Restore the user's previous map FOV when the layer turns off.
- Consequence to accept: in AR mode the zoom *range* is the camera's (~0.5×–8× typical),
  far narrower than the map's 0.5°–90°. Fine — you can't point a camera at a 90° sky
  anyway.

### Latency and swim

The video pipeline lags the rotation-vector sensor by several frames, so during fast pans
the map will lead the video and snap back ("swim"). Mitigations are limited (we can't delay
the sensor without making the map feel laggy); note it, don't fight it in v1 of the
feature. The existing sensor damping already helps at the slow speeds people actually use
for alignment.

### Permissions, hardware, flavors

- `CAMERA` permission + `<uses-feature android:name="android.hardware.camera"
  android:required="false"/>`. Clone the location permission state machine
  (`MainActivity` launcher → `MapScreen` dialogs). Hide the toggle entirely on camera-less
  devices.
- CameraX is Jetpack (Apache-2.0) — no gms/fdroid split needed.
- The toggle is a display mode, **not** a `LayerId`: surfaced in the Layers sheet Display
  group per `ux-polish.md`. Decided (2026-07-26): **always starts off** — camera-on is a
  deliberate, permission-holding, power-hungry state, so it is session state, not a
  persisted `Settings` boolean. The camera is released whenever the app stops (background
  or close) and the layer comes back off on next launch.
- AR requires sensor mode: enabling the layer while in MANUAL switches to SENSOR (with a
  snackbar); devices without usable orientation sensors don't get AR (toggle disabled with
  an explanatory row subtitle).

## Part 2: Drag-to-align and the remembered correction

### What the correction *is*

Sensor error is dominated by compass yaw (often 5–20° near metal/magnets) plus a smaller
accelerometer pitch error; roll error is usually negligible. Today's remembered correction
(`manualCompassAdjustmentDeg`) captures only yaw. The AR drag should produce an
**azimuth offset + altitude offset** pair (skip roll in v1 of the feature; two-finger
rotate could add it later):

- **Azimuth** — a rotation about the zenith, which is *exactly* what
  `manualCompassAdjustmentDeg` already is mechanically (applied as extra declination in
  `localFrame` via `declinationFor()`; that application point is unchanged). Decided
  2026-07-26: the old preference and its UI are **removed outright** — the "Additional
  compass adjustment" row in `SettingsUi`, the `settings_compass_adjustment*` strings, the
  help-text sentence pointing at it, and the `manualCompassAdjustmentDeg` DataStore key
  itself. A fresh key (`sensorAzimuthAdjustmentDeg`) replaces it: everyone starts from
  zero in this version, and no stale v-alpha compass adjustment can silently carry over.
- **Altitude** — new sibling preference (`sensorAltitudeAdjustmentDeg`), applied as a
  rotation about the camera-right axis after `SkyModel.pointing` in `collectSensors()`.
  Never exposed in Settings; both axes live in the drag mechanism together.

Caveat to accept and document: a constant az/alt offset is only *locally* correct — compass
yaw error is roughly constant, but pitch error varies with device attitude. The correction
will be best near where it was made, which matches how people use it (align on the Moon,
observe that region).

### UX: how dragging enters the picture

Today, in sensor mode, drag does nothing (only MANUAL consumes it). Two candidate designs:

- **Direct**: while the camera layer is on (necessarily sensor mode), drag *is* the
  alignment adjustment. Zero ceremony, maximally discoverable, and it's the gesture users
  will instinctively try. Risk: accidental corrections that silently persist.
- **Explicit align mode**: an "Align" affordance (button/chip) arms a mode; drag adjusts;
  confirm/cancel. Safe but heavy — an extra mode, an extra chrome element, and most users
  will never find it.

Decided (2026-07-26): **direct drag**, with a clear visible receipt that a correction was
applied and an easy reset. The surfaces are:

- **On drag**: a snackbar receipt ("Map alignment adjusted — tap to undo").
- **HUD**: the correction (both axes) appears in a new map HUD that is shown **only while
  the on-screen controls are visible** (the layers/zone chrome). The HUD gets its own
  design pass — it will also carry field of view, current RA/Dec, and other useful
  read-outs — and is where the **Reset** affordance lives. (This replaces the earlier
  always-on "⇄ chip" idea; no persistent chrome is added.)
- **Diagnostics**: the diagnostics screen exposes the az/alt correction values (it already
  reads `manualCompassAdjustmentDeg`; altitude joins it).

Persistence: immediate write-through to DataStore (same as every other setting; no
commit/abandon state machine), undo via the snackbar. Correction applies in sensor mode
generally (it corrects the *sensors*, not the camera), AR just makes it visible and
settable.

Decided (2026-07-26): the correction feature is simply **unavailable without the camera**.
On camera-less devices, or when camera permission is denied, there is no way to set a
correction — and because the keys start afresh in this version, such users also carry no
correction (both axes are zero), so nothing needs a reset path outside AR. The diagnostics
read-out is read-only.

## Part 3: Share

Deferred to its own design pass once Part 1 exists, but the exploration so far:

- **Capture**: no capture code exists. `PixelCopy.request(glSurfaceView, …)` (fine at
  minSdk 29) grabs the GL plane with alpha; the camera frame comes from CameraX
  `ImageCapture` (a real still — better quality than scraping the preview). Composite on a
  `Canvas`: overlay layout (camera still under GL bitmap — matches what the user sees) and
  side-by-side layout (camera | map, each captioned). Compose chrome is naturally excluded
  since we composite planes ourselves.
- **Branding**: a footer strip — app icon + "Sky Map" wordmark + short invitation +
  Play/F-Droid link (QR?) — assets in `assets/branding/` (All Rights Reserved, per the
  split-license rules). `EXTRA_TEXT` carries the invitation + store link for apps that
  take text; the image goes via `FileProvider` + `ACTION_SEND` chooser.
- **UX home**: Share row in the Zone C overflow sheet (already reserved in `ux-polish.md`),
  plus likely a transient in-AR shutter affordance, since "photograph the moment" is most
  compelling while the camera layer is live. Layout choice (overlay vs side-by-side) at
  share time — a small two-option sheet with thumbnails.
- Works without the camera layer too (map-only capture + branding) — that's the cheap
  first slice of Share and useful on its own.

## Suggested slicing (each its own PR)

1. **Camera underlay**: permission flow, CameraX preview under a translucent GL surface,
   `RenderState.transparentBackground`, Layers-sheet toggle, FOV lock, scrim + exposure
   control. (The bulk of the risk; instrumented smoke test that the GL surface still
   passes the D19 perf gate with translucency on.)
2. **Drag-to-align**: the two new adjustment preferences + pointing hook, drag routing in
   AR, snackbar undo receipt, removal of `manualCompassAdjustmentDeg` and the Settings
   "Additional compass adjustment" row (+ strings and help-text sentence), read-only
   diagnostics read-out of both axes. The HUD ships separately once its design lands.
3. **Share, map-only**: capture + branding footer + `ACTION_SEND`.
4. **Share, camera layouts**: `ImageCapture` still, overlay + side-by-side composites.

## Decisions (2026-07-26, recorded as D64)

1. **Compositing**: Option A, stacked surfaces. Camera2-level exposure control (if
   needed) comes via CameraX `Camera2Interop` — no architecture change.
2. **Correction storage**: fresh DataStore keys (`sensorAzimuthAdjustmentDeg`,
   `sensorAltitudeAdjustmentDeg`); `manualCompassAdjustmentDeg` and the Settings
   "Additional compass adjustment" row are **removed** — everyone starts from zero.
   Both axes are set only by drag-to-align.
3. **Gesture**: direct drag, with a clear visible indication a correction was applied
   (snackbar receipt) and an easy reset (in the HUD).
4. **Night mode × camera**: coexist; night mode reds the map and chrome, camera image is
   not reddened but night mode imposes a **scrim floor** (~65% minimum camera dimming) to
   protect dark adaptation.
5. **Persistence of camera-on**: never — the camera layer always starts off; camera off
   when the app closes.
6. **Correction visibility**: no persistent chip. Exposed read-only in diagnostics, and
   in a HUD shown only while the on-screen controls are visible (HUD — with FOV, current
   RA/Dec, etc. — gets a separate design). Without a camera (no hardware or permission
   denied) the correction feature is simply unavailable.
