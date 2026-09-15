# Sensor Correction Model — Error Physics and How Drag-to-Align Maps onto Them

**Status: REFERENCE** — companion to [camera-ar-mode.md](camera-ar-mode.md) (Part 2,
drag-to-align, D68). Explains what physically causes the sensor pointing to be wrong, how
the stored az/alt correction is applied in code, and why that model matches (and where it
approximates) the underlying error mechanisms.

## Where the errors come from

### Azimuth (magnetometer)

The fused orientation comes from `TYPE_ROTATION_VECTOR` — gyro + accelerometer +
magnetometer (`SensorOrientationSource.kt`). Gravity (from the accelerometer) pins pitch
and roll; the magnetometer contributes *only* the yaw-about-gravity component. That is a
key structural fact: **every magnetic error, whatever its physical origin, surfaces as an
azimuth error about the local vertical.** The origins, roughly in order of real-world
importance:

1. **Local environment** — rebar in a balcony, a car roof, a steel tripod or telescope
   tube near the phone. Can be many degrees, and is the error users most often see.
2. **Residual hard-iron** — the phone's own magnets (speakers, OIS, haptics, MagSafe
   cases). Android's calibration removes most of it, but the residue produces an error
   that varies roughly *sinusoidally with heading* — see the caveat below.
3. **Soft-iron distortion** — ferromagnetic material reshaping the field;
   heading-dependent and poorly calibrated.
4. **Declination model error** — WMM is already applied; its own error is usually under
   half a degree.
5. **Magnetometer die mounted askew** — real but tiny; factory boresight calibration
   handles it to well under a degree on any name-brand phone. This one, uniquely, is a
   fixed rotation in the *device* frame rather than the world frame.

### Altitude (accelerometer)

Gravity sensing is far better behaved: typical accelerometer bias gives 0.1–0.3° of tilt
error. The larger practical altitude discrepancy in AR mode is probably not the
accelerometer at all but **camera boresight skew** — the camera's optical axis not being
exactly where the sensor frame says the back of the phone points. That, like a skewed
compass die, lives in the device frame.

## How the correction is applied in code

- **Azimuth**: `declinationFor` returns `WMM declination + azimuthAdjustment` and feeds
  it into `SkyModel.localFrame` (`MapViewModel.kt`) — the adjustment is applied
  *identically to magnetic declination*: a rotation of the local frame about the zenith.
- **Altitude**: after `SkyModel.pointing`, the pointing is rotated by `−altDeg` about
  `lineOfSight × up` (`MapViewModel.resolveSensorCamera`) — a world-frame tilt, up or
  down, perpendicular to wherever the user is looking.

So the stored correction is a simple az/alt rotation in the local horizontal frame, and a
drag captures the *total* discrepancy at the current pointing regardless of cause —
environment + hard-iron + WMM error + boresight, all lumped together.

## Is that the right model?

### Azimuth: yes, and not by accident

Because the fusion algorithm only lets magnetic errors into the yaw-about-gravity axis, a
rotation about the zenith is exactly the axis the error actually occupies. Correcting
anywhere else (e.g. in the sensor frame) would be modeling error the magnetometer cannot
cause.

**Caveat:** hard/soft-iron residuals are heading-dependent, so a single offset is exactly
right only near the heading where the user aligned, and degrades as they swing around the
horizon. No static offset can fix that — it would take a fresh magnetometer calibration
(the figure-8 dance) — and it is the same limitation v1's compass adjustment always had.

### "Compass installed askew" specifically

A device-frame yaw skew and a world-frame azimuth offset coincide whenever the phone is
held roughly upright — which is how people hold it while aligning and while stargazing —
so the correction absorbs it fine in practice. The two models only diverge as the phone
rolls toward pointing at the zenith, where azimuth is degenerate anyway. Empirically this
mechanism is a sub-degree effect on modern phones; it is dominated by the environmental
terms.

### Altitude: an approximation, and the one place a purist could object

If the true cause is device-frame (camera boresight, accelerometer bias), the "correct"
fix is a fixed rotation composed with the sensor matrix on the device side. The
observable difference: a boresight error rotates with the phone, so the world-frame
offset would be right in portrait but slightly wrong after rotating to landscape. At the
magnitudes involved (a degree or two), world-frame is the right trade — it is directly
displayable ("Alt +1.3°"), understandable, and matches how the user perceives the error
("the sky is too high").

## Upgrade path (not planned)

If field testing ever shows the alignment visibly drifting when the phone rolls between
portrait and landscape, that is the signature of a device-frame boresight error. The
upgrade would be storing a full correction rotation (aligned from drags in two different
phone orientations) applied on the device side of the sensor matrix. This should not be
built speculatively — the current model is the standard one for exactly these error
physics.
