# Detailed Design: Launching Through-Camera (AR) Mode on Android and iOS

**Status: PROPOSED.** Nothing here is built. Decision numbers are unassigned.

[camera-ar-mode.md](camera-ar-mode.md) designed AR mode, drag-to-align and share, and all four
slices are built on Android behind `CAMERA_AR` and `SHARE_SKY`, both off. This doc covers what
stands between that and a public launch **on Android and iOS together**: the iOS port, the
Android loose ends, and how the two releases are coordinated. Anything not restated here is
unchanged from the original design.

## 1. Where things stand

| Piece | Android | iOS |
|---|---|---|
| Transparent map, camera scrim | Built (GLES1 and GLES3) | **Built** in `MetalSkyRenderer` (`transparentBackground`, `cameraScrim`) |
| AR state, controls, alignment logic | Shared: `ArState`, `ArControls`, `MapViewModel`, `MapScreen` hooks (`hasArCamera`, `hasCameraPermission`, `onRequestCameraPermission`, `onShareSky`) | Shared, but no host supplies a camera, so the toggle never shows |
| Camera edge | `SkyCameraPreview` (CameraX): preview, zoom, exposure, still | **None** |
| Camera FOV | `cameraShortSideFovDeg`, from sensor geometry | **None** |
| Share | `SkyShare` (PixelCopy, Canvas, `FileProvider`) and `ShareLayoutSheet`, both in `app/` | **None** |
| Drag-to-align | Built; keys `sensorAzimuthAdjustmentDeg`, `sensorAltitudeAdjustmentDeg` | Works once a camera exists; the keys are shared |
| Flags | `CAMERA_AR`, `SHARE_SKY` default false | No Remote Config yet ([ios-platform-surfaces.md](ios-platform-surfaces.md) §3) |

So the iOS work is one platform edge (the camera), one share path, and the flag plumbing. The
rendering and the interaction are done.

## 2. Principles

- **One release moment.** Both apps carry the feature dark, then a Remote Config flip turns it
  on for both on the same day (§5).
- **Share the logic, not the capture.** Zoom lock, exposure mapping, alignment and layout
  geometry are common. AVFoundation and CameraX are each platform edges.
- **No camera data leaves the device.** Frames are never stored or uploaded. A still exists
  only when the user shares it, and only the system share sheet receives it.
- **Nothing temporary ships.** The developer controls go before launch (§3.1).

## 3. Android: what is left before launch

### 3.1 Remove the temporary exposure controls

`ArControls` shows ISO and shutter sliders ("Temporary dev controls (exposure lever 4) while the
night behavior is tuned") whenever the device reports manual-sensor support. Nothing gates them
to debug builds. Before launch:

- Decide the night behaviour from field testing, then **delete** the manual-exposure path:
  `ArManualExposure`, `ArExposureMath`, the `isoFraction` and `shutterFraction` state, the two
  sliders and their `ar_iso_*`, `ar_shutter_*` and `ar_exposure_auto` strings, and
  `SkyCameraPreview.setManualExposure`.
- If field testing shows lever 4 is needed, ship it as a designed control instead, with proper
  names. It must not go out as a developer slider.
- The exposure and dimmer sliders (levers 1 and 2) and Low Light Boost (lever 3) stay.

Removing the shared state also keeps iOS from inheriting it (§4.4).

### 3.2 Privacy and store forms

`PRIVACY_POLICY.md` and `TERMS.md` do not mention the camera. In the same change as the launch
flip, add: the camera is used only for the live background; frames are processed on the device
and are never stored or sent; a still is created only when the user shares, and goes only to the
app they choose. Check `eula.xml`, `TERMS.md` section 3, and the Play Data safety form (the
`CAMERA` permission is declared; no data is collected). Update the policy's `Last Updated`.

### 3.3 Analytics

The only AR-related event today is the `share_sky` label. Add, with no image or location data
and nothing typed by the user: AR turned on, a correction applied (not its value), and share
started with the chosen layout. Update the policy in the same change if any parameter is new
(see `AGENTS.md` → Privacy).

### 3.4 Launch copy and translations

D80 kept AR out of launch copy. Writing it is part of the launch: What's New, Help (the
`layer_help_camera` text exists), store listings, the getskymap.app page. The `ar_*`,
`layer_camera*` and share strings must be translated before the flip, which is a `tm translate`
the maintainer approves first.

### 3.5 Device coverage

Field-test a spread of devices: lens switching on multi-camera phones, Low Light Boost on
Android 15+, a camera-less device (the toggle must be absent), and a denied permission. The
FOV lock is only as good as the reported characteristics, so record the measured error on at
least three devices.

## 4. iOS: the port

### 4.1 Layering

`MapViewController` hosts the Metal view inside Compose Multiplatform. AR needs three layers,
bottom to top: **camera, Metal map, Compose chrome**.

- Put an `AVCaptureVideoPreviewLayer` view beneath the Metal view in the same UIKit
  container.
- Make the Metal layer non-opaque (`isOpaque = false`) while AR is on. The renderer already
  clears to alpha 0 and draws the scrim.
- The preview layer is composited by the system with no copy, the iOS equivalent of Android's
  stacked-surface "Option A", so the original compositing decision carries over unchanged.
- Spike this first: Compose chrome over a transparent Metal view over a camera layer is the
  one assumption in the port the codebase has not yet tested.

### 4.2 The camera edge

An `ArCamera` interface in `shared/viewmodels` mirrors what `SkyCameraPreview` offers: `bind`,
`unbind`, specs (`ArCameraSpecs`), `setZoomRatio`, `setExposureIndex`, `takeStill`. Android's
class implements it with CameraX, iOS's with AVFoundation, in Kotlin/Native in `iosMain` as the
Core Motion and Metal code already is. (Fall back to Swift only if an API is not reachable.)

- **Lens: the virtual multi-lens device, as on Android.** Use the dual-wide or triple camera
  where the phone has one (the iPhone 13 test device has the dual-wide) and the single wide
  camera otherwise. Zoom is `videoZoomFactor`, where 1.0 is the ultra-wide, and the system
  switches lens at `virtualDeviceSwitchOverVideoZoomFactors`. The map follows the switch (§4.3).
  This offers the ultra-wide in the first release; revisit after user feedback.
- **Stabilisation off.** Set preview video stabilisation to off: it crops the image, which
  changes the effective FOV and adds latency.
- **Exposure.** The slider maps to `exposureTargetBias`, quantised to 1/3 EV so the shared
  integer index and range work unchanged. Enable low-light boost where
  `isLowLightBoostSupported`, the counterpart to Android lever 3. Verify the effect on device.
- **Permission.** `AVCaptureDevice.requestAccess` when the user turns AR on, never at launch,
  through the existing `onRequestCameraPermission` hook. A denial gets the same snackbar as
  location, with a link to Settings. Add `NSCameraUsageDescription` to `Info.plist`, written
  for the benefit ("shows the real sky behind the map; nothing is recorded"), and localised
  with the other permission strings.
- **Lifecycle.** Stop the session when the app backgrounds or the layer turns off, and restart
  on return only if the layer was on. Follow `ProcessInfo.thermalState`: at `.serious`, stop
  AR with a snackbar explaining why.
- **Orientation.** iPhone landscape is supported (D143). Keep the preview's rotation in step
  with the interface orientation. The FOV is defined on the shorter view side, so it does not
  depend on orientation.

### 4.3 Matching the map zoom to the camera

`activeFormat.videoFieldOfView` gives the horizontal FOV of the unrotated sensor image. Turn it
into the short-side FOV for the visible region after the `resizeAspectFill` crop with the same
geometry as `cameraShortSideFovDeg`: `tan(h/2)` is the half-width on the image plane, scale by
the format's aspect ratio and the crop, then `2·atan` of the visible short half-side.

- Extract the crop-and-angle step into common code with two entry points (sensor geometry for
  Android, horizontal FOV for iOS), so one set of unit tests covers both.
- `videoFieldOfView` is reported per format and is not always exact. Measure it on the iPhone 13
  by aligning on the Moon or a known star pair, and record the error beside Android's (§3.5).
- **With lens switching, the FOV is computed per lens.** Take each constituent lens's own FOV
  (ultra-wide `U`, wide `W`) and the switch-over factor `s` (about 2.0 on a dual-wide). For zoom
  factor `z`, the half-angle's tangent is `tan(U/2) / z` below `s`, and `tan(W/2) / (z / s)` at
  and above it. This is pure maths, with unit tests at and either side of the switch-over, and
  it is the iOS counterpart of Android's exact per-lens recompute. Read the active lens
  (`activePrimaryConstituent`, iOS 15.4+) for the check on device; the deployment target is
  iOS 15.0, so guard it with `#available`.

### 4.4 Alignment and exposure controls

- **Drag-to-align** needs no new logic. `MapScreen` routes the drag, `MapViewModel` applies the
  azimuth and altitude correction, and the keys are in shared `Settings`. Verify two things on
  device: that the D143 landscape sensor remapping and the back-camera view direction agree, and
  that Core Motion's heading reference gives a yaw error of the same kind as Android's.
- **No manual ISO or shutter on iOS.** `setExposureModeCustom` exists, but the Android sliders
  are being deleted (§3.1), so the shared state is simply gone.

### 4.5 Share

The share path splits into shared geometry and platform capture.

| Step | Where |
|---|---|
| Output size and crop (`shareScaledSize`, `centerCrop`) | Move to common (pure) |
| Layout sheet (overlay or side by side, with thumbnails) | Move `ShareLayoutSheet` from `app/` to `shared/ui` |
| Capture the map with alpha | Android: `PixelCopy`. iOS: render the current scene offscreen with the Metal renderer (the offscreen path the render tests already use) and read it back |
| Capture the camera still | Android: `ImageCapture`. iOS: `AVCapturePhotoOutput` |
| Composite the layouts and footer | Common code drawing into a Compose `ImageBitmap`, one compositor for both platforms (decided; the alternative was a Canvas and a Core Graphics compositor) |
| Encode and send | Android: `FileProvider` and `ACTION_SEND`. iOS: write to a temporary file and present `UIActivityViewController` |

- **One compositor**, so the two apps cannot drift in how a shared image looks. Encoding stays
  per platform.
- **Footer:** the same brand strip and `getskymap.app` link as Android (D110). The brand icon is
  already classified in `ASSET-LICENSES.txt`; check the iOS bundle uses the same file.
- **Metadata:** re-encode from the pixel data so the file carries no EXIF and no location.
- **Photos permission:** none needed. The share sheet's Save Image action is handled by the
  system.
- The shutter sound on a still photo is legally required in some regions, so do not try to
  silence it.

### 4.6 Simulator and tests

The simulator has no camera. Provide a `FakeArCamera` that supplies a static night-sky image and
fixed specs, so the layering, scrim, zoom lock and share can be driven in the simulator and in
UI tests. Real-camera checks happen on the iPhone 13 only.

## 5. Coordinating the release

Both stores' builds ship with AR **dark**, then one Remote Config change turns it on for both.

1. **Prerequisite:** the iOS Remote Config adapter (ios-platform-surfaces.md §3), shipped in
   iOS 2.0.1 or earlier. Without it iOS cannot be switched on remotely.
2. **Android:** the build with the §3 changes ships dark. `CAMERA_AR` and `SHARE_SKY` stay false.
3. **iOS:** the AR build goes to App Review.
   - Apple guideline 2.3.1 forbids hidden or dormant features. **Turn the flags on for the
     review build** with a Remote Config condition on app version and build number, and say how
     to reach the feature in the review notes. Then narrow the condition after approval.
   - Submit an external TestFlight build for Beta App Review early (see the port plan's launch
     blockers).
4. **Launch day:** once the iOS build is approved and released, flip both flags for all users,
   publish the announcement (the `ANNOUNCEMENTS` flag and `skymap.push-announcement`), update
   the getskymap.app page and the store listings, and update the reply templates for reviews
   that ask for AR.
5. **After a stable fortnight,** delete the two flags and their gates, as is being done for the
   widget flags. Until then they are the kill switch if the camera misbehaves.

## 6. Slices

| # | Slice | Platform | Notes |
|---|---|---|---|
| 1 | Remove manual-exposure dev controls; AR analytics; privacy text | Android (shared UI) | Unblocks iOS inheriting clean state |
| 2 | `ArCamera` interface and common FOV math | Shared | Android implements it with no behaviour change |
| 3 | Layering spike: Compose over transparent Metal over `AVCaptureVideoPreviewLayer` | iOS | Highest risk; do first |
| 4 | iOS camera edge: permission, lifecycle, zoom lock, exposure | iOS | Needs a device |
| 5 | Device FOV measurement and sign-off | Both | §3.5, §4.3 |
| 6 | Share: common geometry, layout sheet and compositor, then iOS capture and send | Shared, iOS | |
| 7 | Copy, translations, listings, privacy forms | Both | `tm translate` is the maintainer's call |
| 8 | Launch day and flag removal | Both | §5 |

Slice 1 and 2 are Android-side refactors with no visible change and should merge first.

## 7. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `videoFieldOfView` is inexact for some formats, or the lens switch jumps the FOV | Map and camera visibly different sizes, or a jump at the switch | Measure each lens on device; pick a fixed format; allow a per-lens scale if needed; smooth the map's FOV across the switch |
| Compose, Metal and camera layers do not composite cleanly | The port stalls | Spike first (slice 3); SwiftUI chrome is the fallback |
| App Review rejects dormant features | The launch slips | Flags on for the review build, with notes (§5) |
| Latency makes the map swim against the video | Poor first impression | Stabilisation off; same damping as Android; accept and note |
| Thermal load or battery drain | Throttled device, bad reviews | Thermal-state stop; the layer is never persisted on |
| The Android refactors (slices 1, 2, 6) regress behaviour | A broken feature on the larger platform | Mechanical moves; existing tests kept unchanged; one slice per PR |
| The two platforms' share images differ | Inconsistent brand | One compositor |

## 8. Privacy checklist

Per `AGENTS.md`, in the launch change update `PRIVACY_POLICY.md`, `eula.xml`/`TERMS.md` section
3, the iOS `PrivacyInfo.xcprivacy` (confirm no new required-reason API is used), the App Store
privacy answers (no collection), and the Play Data safety form. Say in the PR that you checked.

## 9. Decisions and open questions

Decided 2026-11-04:

1. **Share compositing: one common compositor** drawing into a Compose `ImageBitmap`, with
   encoding per platform (§4.5).
2. **Android keeps lens switching.**
3. **iOS offers the ultra-wide in the first release**, through the virtual multi-lens device,
   and we revisit once there is feedback (§4.2, §4.3).

Open:

4. What is the first release's name and splash, if AR makes it a minor version bump?
