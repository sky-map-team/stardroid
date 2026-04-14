# Sensor Data Flow Analysis: Sky Map Android App

## Executive Summary

The app has **two major code paths** for sensor data depending on available hardware:
1. **Modern Path**: Uses the Android Rotation Vector sensor (fused accelerometer + magnetometer + gyroscope)
2. **Classic Path**: Uses raw accelerometer + magnetometer with exponential smoothing

Both paths ultimately compute vectors that allow the phone's orientation to be transformed into celestial coordinates, and ensure text labels remain readable regardless of phone rotation.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          SENSOR DATA FLOW OVERVIEW                               │
└─────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────┐     ┌────────────────────────┐
│   ROTATION VECTOR      │     │   ACCELEROMETER +      │
│   SENSOR (TYPE 11)     │     │   MAGNETOMETER         │
│   (Fused: Acc+Mag+Gyro)│     │   (Raw sensors)        │
└──────────┬─────────────┘     └──────────┬─────────────┘
           │                              │
           │ PATH 1: Modern              │ PATH 2: Classic
           │ (gyro enabled)               │ (gyro disabled/unavailable)
           │                              │
           ▼                              ▼
┌────────────────────────┐     ┌────────────────────────┐
│ SensorOrientation      │     │ ExponentiallyWeighted  │
│ Controller             │     │ Smoother               │
│ .onSensorChanged()     │     │ (dampens jitter)       │
│ :95                    │     │ :124-131               │
└──────────┬─────────────┘     └──────────┬─────────────┘
           │                              │
           │ float[4] quaternion          │ PlainSmootherModelAdaptor
           │                              │ :50-64
           │                              │ Vector3 accel, Vector3 mag
           ▼                              ▼
       ┌───────────────────────────────────────┐
       │         AstronomerModelImpl           │
       │  setPhoneSensorValues(float[])  :174  │
       │  setPhoneSensorValues(V3, V3)   :162  │
       └───────────────────┬───────────────────┘
                           │
                           ▼
       ┌───────────────────────────────────────┐
       │    calculatePointing()  :232          │
       │    ┌─────────────────────────────┐    │
       │    │ 1. Get phone N,E,U vectors  │    │
       │    │    (from sensors)           │    │
       │    │ 2. Get celestial N,E,U      │    │
       │    │    (from lat/lon/time)      │    │
       │    │ 3. Build transform matrix   │    │
       │    │ 4. Transform pointing       │    │
       │    └─────────────────────────────┘    │
       └───────────────────┬───────────────────┘
                           │
                           │ Pointing (lineOfSight + perpendicular)
                           │ phoneUpDirection (for text angle)
                           ▼
       ┌───────────────────────────────────────┐
       │   RendererModelUpdateClosure          │
       │   (called every frame)                │
       │   DynamicStarMapActivity:109-142      │
       │                                       │
       │   • viewOrientation → OpenGL camera   │
       │   • textAngle = atan2(up.x, up.y)     │
       │   • zenith → for horizon display      │
       └───────────────────┬───────────────────┘
                           │
                           ▼
       ┌───────────────────────────────────────┐
       │         SkyRenderer                   │
       │   • setViewOrientation()  :287        │
       │   • setTextAngle()        :278        │
       │     (snaps to nearest 90°)            │
       └───────────────────┬───────────────────┘
                           │
                           │ upAngle (0°, 90°, 180°, or 270°)
                           ▼
       ┌───────────────────────────────────────┐
       │      LabelObjectManager               │
       │   drawLabel()  :299-341               │
       │                                       │
       │   gl.glRotatef(upAngle, 0, 0, -1)     │
       │   (rotates text to stay readable)    │
       └───────────────────────────────────────┘
```

---

## Sensor Detection Logic

**File:** `StardroidApplication.kt:160-225`

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SENSOR DETECTION DECISION TREE                    │
└─────────────────────────────────────────────────────────────────────┘

                      Has TYPE_ROTATION_VECTOR?
                               │
                ┌──────────────┴──────────────┐
                │ YES                         │ NO
                ▼                             ▼
        Has ACCELEROMETER                 DISABLE_GYRO = true
        AND MAGNETIC_FIELD               (Fall back to classic)
        AND GYROSCOPE?
                │
     ┌──────────┴──────────┐
     │ YES                 │ NO (gyro missing)
     ▼                     ▼
hasRotationSensor = true   hasRotationSensor = false
DISABLE_GYRO = false       DISABLE_GYRO = true
(Use rotation vector)      "Too many gyro-less phones lie"
```

**Rationale (lines 200-203):** Some phones report having a rotation vector sensor but lack a gyroscope. These phones produce poor results with the rotation vector sensor, so the app forces them to the classic path.

---

## The Two Code Paths in Detail

### Path 1: Modern (Rotation Vector)

**Condition:** `rotationSensor != null && !DISABLE_GYRO`

```kotlin
// SensorOrientationController.java:88-95
if (!sharedPreferences.getBoolean(SHARED_PREFERENCE_DISABLE_GYRO, false)
    && rotationSensor != null) {
    manager.registerListener(this, rotationSensor, SENSOR_DELAY_GAME);
}
```

**Data Flow:**
```
Rotation Vector Sensor
        │
        │ float[4]: [x*sin(θ/2), y*sin(θ/2), z*sin(θ/2), cos(θ/2)]
        │ (quaternion-like representation)
        ▼
SensorOrientationController.onSensorChanged() :161-166
        │
        │ model.setPhoneSensorValues(event.values)
        ▼
AstronomerModelImpl.setPhoneSensorValues(float[]) :174-186
        │
        │ Copies to rotationVector[4]
        │ Sets useRotationVector = true
        ▼
calculateLocalNorthAndUpInPhoneCoordsFromSensors() :288-309
        │
        │ // Convert quaternion to 3x3 rotation matrix
        │ SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        │
        │ // Extract basis vectors from matrix rows
        │ magneticEastPhone  = [m[0], m[1], m[2]]  // Row 0
        │ magneticNorthPhone = [m[3], m[4], m[5]]  // Row 1
        │ upPhone            = [m[6], m[7], m[8]]  // Row 2
        ▼
Build axesPhoneInverseMatrix from [N, U, E]
```

### Path 2: Classic (Accelerometer + Magnetometer)

**Condition:** `rotationSensor == null || DISABLE_GYRO`

```java
// SensorOrientationController.java:96-144
Log.w(TAG, "Rotation sensor not available, falling back to classic sensors");

accelerometerSmoother = new ExponentiallyWeightedSmoother(
    modelAdaptor, ACC_DAMPING, ACC_EXPONENT);
compassSmoother = new ExponentiallyWeightedSmoother(
    modelAdaptor, MAG_DAMPING, MAG_EXPONENT);

manager.registerListener(accelerometerSmoother, accelerometer, sensorSpeed);
manager.registerListener(compassSmoother, compass, sensorSpeed);
```

**Data Flow:**
```
Accelerometer          Magnetometer
    │                      │
    │ [ax, ay, az]         │ [mx, my, mz]
    ▼                      ▼
ExponentiallyWeighted  ExponentiallyWeighted
Smoother               Smoother
(reduces noise)        (reduces noise)
    │                      │
    └──────────┬───────────┘
               ▼
PlainSmootherModelAdaptor.onSensorChanged() :49-65
               │
               │ acceleration.assign(values)
               │ magneticValues.assign(values)
               │ model.setPhoneSensorValues(acceleration, magneticValues)
               ▼
AstronomerModelImpl.setPhoneSensorValues(Vector3, Vector3) :162-172
               │
               │ Sets useRotationVector = false
               ▼
calculateLocalNorthAndUpInPhoneCoordsFromSensors() :298-309
               │
               │ // Derive basis vectors from raw sensor data
               │ upPhone = normalize(acceleration)
               │
               │ // Vector rejection: project mag onto ground plane
               │ magneticNorthPhone = mag - up * (mag · up)
               │ magneticNorthPhone.normalize()
               │
               │ // East = North × Up
               │ magneticEastPhone = magneticNorthPhone × upPhone
               ▼
Build axesPhoneInverseMatrix from [N, U, E]
```

---

## Mathematical Transformations

### 1. Phone Coordinates → Celestial Coordinates

The core transformation is built in `calculatePointing()` at line 232-243:

```
┌────────────────────────────────────────────────────────────────────────┐
│              COORDINATE TRANSFORMATION MATHEMATICS                      │
└────────────────────────────────────────────────────────────────────────┘

Phone Coordinate Frame              Celestial Coordinate Frame
─────────────────────              ──────────────────────────
x: across short edge               x: towards RA=90°, Dec=0°
y: along long edge                 y: towards RA=0°, Dec=0°
z: out of screen                   z: towards Dec=90° (celestial north pole)

GOAL: Find matrix T such that:
    vector_celestial = T × vector_phone

ALGORITHM:
1. Express local reference frame in both coordinate systems
2. Build transformation from one to the other

Step 1a: Local frame in PHONE coordinates
─────────────────────────────────────────
From sensors, we get three orthonormal vectors in phone coords:
  N_p = North along ground (from magnetometer, projected)
  U_p = Up to zenith (from accelerometer)
  E_p = East (= N × U)

These form matrix: axesPhone = [N_p | U_p | E_p]  (columns)

Step 1b: Local frame in CELESTIAL coordinates
─────────────────────────────────────────────
From lat/lon/time, calculate:

  // Zenith in celestial coords
  (ra_z, dec_z) = calculateRADecOfZenith(time, location)  // line 259
  U_c = getGeocentricCoords(ra_z, dec_z)  // unit vector to zenith

  // North along ground = Earth's axis minus zenith component
  z = [0, 0, 1]  // Earth's rotation axis in celestial coords
  N_c = z - U_c × (z · U_c)   // Vector rejection
  N_c.normalize()

  // East
  E_c = N_c × U_c

These form matrix: axesCelestial = [N_c | U_c | E_c]

Step 2: Apply magnetic declination correction
─────────────────────────────────────────────
Magnetometer points to magnetic north, not true north.
Rotate celestial axes by declination angle about the Up axis:

  R = Rodrigues rotation matrix about U_c by angle θ
  N_magnetic = R × N_c
  E_magnetic = N_magnetic × U_c

  axesMagneticCelestial = [N_magnetic | U_c | E_magnetic]

Step 3: Build final transformation
─────────────────────────────────────────────
Since both matrices are orthonormal:

  T = axesMagneticCelestialMatrix × axesPhoneInverseMatrix

Where axesPhoneInverseMatrix = transpose(axesPhone) for orthogonal matrices

Step 4: Transform pointing direction
─────────────────────────────────────────────
  viewDir_celestial = T × viewDir_phone
  screenUp_celestial = T × screenUp_phone
```

### 2. Text Angle Calculation

**File:** `DynamicStarMapActivity.java:135-136`

```java
Vector3 up = model.getPhoneUpDirection();  // upPhone from sensor calcs
rendererController.queueTextAngle(MathUtils.atan2(up.x, up.y));
```

This calculates the angle of the phone's "up" vector projected onto the screen plane:
```
           y (phone long axis)
           ↑
           │   /
           │  / upPhone projected onto XY
           │ /
           │/θ
    ───────┼─────→ x (phone short axis)

    θ = atan2(up.x, up.y)

    When phone is portrait:  θ ≈ 0°
    When phone is landscape: θ ≈ ±90°
    When phone is upside down: θ ≈ 180°
```

### 3. Text Angle Snapping

**File:** `SkyRenderer.java:278-284`

```java
public void setTextAngle(float angleInRadians) {
    final float TWO_OVER_PI = 2.0f / (float)Math.PI;
    final float PI_OVER_TWO = (float)Math.PI / 2.0f;

    float newAngle = Math.round(angleInRadians * TWO_OVER_PI) * PI_OVER_TWO;

    mRenderState.setUpAngle(newAngle);
}
```

This snaps the angle to the nearest 90°:
```
Input angle:     Output (snapped):
-157° to -113°   → -180° (upside down)
-112° to -68°    → -90°  (landscape left)
-67° to -23°     → 0°    (portrait)
-22° to 22°      → 0°    (portrait)
23° to 67°       → 0°    (portrait)
68° to 112°      → 90°   (landscape right)
113° to 157°     → 180°  (upside down)
```

### 4. Label Rotation in OpenGL

**File:** `LabelObjectManager.java:299-340`

```java
private void drawLabel(GL10 gl, Label label) {
    // ... visibility check ...

    // Calculate screen position
    Vector3 screenPos = Matrix4x4.transformVector(
        getRenderState().getTransformToScreenMatrix(), v);

    gl.glPushMatrix();

    // Move to label position on screen
    gl.glTranslatef(screenPos.x, screenPos.y, 0);

    // ROTATE BY UP ANGLE - THIS KEEPS TEXT READABLE
    gl.glRotatef(RADIANS_TO_DEGREES * getRenderState().getUpAngle(),
                 0, 0, -1);  // Rotate around Z axis (into screen)

    // Scale and draw the label quad
    gl.glScalef(label.getWidthInPixels(), label.getHeightInPixels(), 1);
    gl.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    gl.glPopMatrix();
}
```

The rotation `glRotatef(angle, 0, 0, -1)` rotates the label quad around the Z-axis (perpendicular to screen), counter-rotating the phone's roll so text stays horizontal.

---

## Missing Sensor Fallback Matrix

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SENSOR AVAILABILITY HANDLING                          │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────┬──────────────┬──────────────┬──────────────┬─────────────┐
│ Rotation Vec │ Accelerometer│ Magnetometer │   Gyroscope  │   Behavior  │
├──────────────┼──────────────┼──────────────┼──────────────┼─────────────┤
│     Yes      │     Yes      │     Yes      │     Yes      │ PATH 1      │
│              │              │              │              │ Modern      │
│              │              │              │              │ (rotation)  │
├──────────────┼──────────────┼──────────────┼──────────────┼─────────────┤
│     Yes      │     Yes      │     Yes      │     NO       │ PATH 2      │
│              │              │              │              │ Classic     │
│              │              │              │              │ (forced)    │
├──────────────┼──────────────┼──────────────┼──────────────┼─────────────┤
│     NO       │     Yes      │     Yes      │   (any)      │ PATH 2      │
│              │              │              │              │ Classic     │
├──────────────┼──────────────┼──────────────┼──────────────┼─────────────┤
│    (any)     │     NO       │    (any)     │   (any)      │ FAIL        │
│              │              │              │              │ Error shown │
├──────────────┼──────────────┼──────────────┼──────────────┼─────────────┤
│    (any)     │    (any)     │     NO       │   (any)      │ FAIL        │
│              │              │              │              │ Error shown │
└──────────────┴──────────────┴──────────────┴──────────────┴─────────────┘
```

**Critical Sensors:**
- **Accelerometer** (TYPE_ACCELEROMETER): **REQUIRED** - determines "down" / gravity direction
- **Magnetometer** (TYPE_MAGNETIC_FIELD): **REQUIRED** - determines magnetic north

**Optional Sensors:**
- **Gyroscope** (TYPE_GYROSCOPE): Improves stability; without it, uses classic path with smoothing
- **Rotation Vector** (TYPE_ROTATION_VECTOR): Composite sensor; requires gyro to be trusted

**Error Handling (SensorOrientationController.java:134-136):**
```java
if (accelerometer == null || compass == null) {
    Log.e(TAG, "Missing accelerometer or compass! Aborting");
    return;  // Controller won't provide updates
}
```

When critical sensors are missing, the `NoSensorsDialogFragment` is shown to the user.

---

## Text Orientation: Complete Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│              HOW TEXT STAYS RIGHT-WAY-UP                                 │
└─────────────────────────────────────────────────────────────────────────┘

STEP 1: Sensor provides phone orientation
─────────────────────────────────────────
Either rotation vector or accelerometer gives us upPhone:

  Rotation Vector path:     upPhone = rotationMatrix[row 2]
  Classic path:             upPhone = normalize(acceleration)

STEP 2: Calculate phone "roll" angle
────────────────────────────────────
DynamicStarMapActivity.RendererModelUpdateClosure:135-136

  Vector3 up = model.getPhoneUpDirection();  // upPhone
  float textAngle = atan2(up.x, up.y);

  ┌─────────────────────────────────┐
  │      upPhone.y (portrait)       │
  │            ↑                    │
  │            │  ╱                 │
  │            │ ╱  upPhone         │
  │            │╱                   │
  │ ───────────┼──────────→         │
  │            │      upPhone.x     │
  │            │    (landscape)     │
  └─────────────────────────────────┘

  textAngle = angle of upPhone from Y axis

STEP 3: Snap to nearest 90°
───────────────────────────
SkyRenderer.setTextAngle():278-284

  snappedAngle = round(textAngle / (π/2)) × (π/2)

  This ensures text is always in one of 4 orientations:
    0°:    Portrait (normal)
    90°:   Landscape (phone rotated right)
    180°:  Upside-down portrait
    -90°:  Landscape (phone rotated left)

STEP 4: Counter-rotate labels in OpenGL
───────────────────────────────────────
LabelObjectManager.drawLabel():328

  gl.glRotatef(snappedAngle × 180/π, 0, 0, -1);

  This rotates each label quad by the snapped angle around the
  screen's Z axis (perpendicular to display), effectively
  canceling out the phone's roll.

RESULT:
───────
  ┌─────────────────┐     ┌─────────────────┐
  │    Phone at     │     │  Phone rotated  │
  │    0° roll      │     │  90° (landscape)│
  │                 │     │                 │
  │   ┌─────────┐   │     │  ╔═══════════╗  │
  │   │ Sirius  │   │     │  ║  Sirius   ║  │
  │   └─────────┘   │     │  ╚═══════════╝  │
  │   Star ★        │     │      ★ Star     │
  │                 │     │                 │
  │  Text readable  │     │ Text STILL      │
  │  in portrait    │     │ readable!       │
  └─────────────────┘     └─────────────────┘
```

---

## Math Verification

### Vector Rejection Formula (Classic Path)

The classic path computes North along the ground using vector rejection:

```
Given:
  a = acceleration (points down = opposite of Up)
  m = magnetic field (points towards magnetic north, with dip angle)

Goal: Find horizontal component of m

Math:
  upPhone = -normalize(a)

  # Project m onto Up direction
  m_parallel = upPhone × (m · upPhone)

  # Subtract to get horizontal component
  m_horizontal = m - m_parallel

  northPhone = normalize(m_horizontal)
```

This is mathematically correct. The magnetic field vector has two components:
1. Horizontal component pointing to magnetic north
2. Vertical component (the "dip" angle, typically 60-70° from horizontal)

By removing the component parallel to "up", we get the pure horizontal direction to magnetic north.

### Rotation Matrix from Rotation Vector

The Android rotation vector is a unit quaternion stored as `[x, y, z, w]` where:
- `x, y, z` = sin(θ/2) × axis
- `w` = cos(θ/2)

`SensorManager.getRotationMatrixFromVector()` converts this to a 3×3 rotation matrix where:
- Row 0: East direction in phone coordinates
- Row 1: North direction in phone coordinates
- Row 2: Up direction in phone coordinates

This is the standard Android coordinate system transformation.

---

## Potential Issues

### 1. Missing Gyroscope (Lines 196-203)
The code explicitly distrusts devices that report rotation vector but lack gyroscope:
```kotlin
} else if (hasDefaultSensor(TYPE_ACCELEROMETER) && hasDefaultSensor(TYPE_MAGNETIC_FIELD)) {
    // Even though it allegedly has the rotation vector sensor too many gyro-less phones
    // lie about this, so put these devices on the 'classic' sensor code for now.
    hasRotationSensor = false
}
```
This is a reasonable defensive measure.

### 2. Magnetic Z-Axis Reversal (PlainSmootherModelAdaptor:45-46, 60)
```java
reverseMagneticZaxis = sharedPreferences.getBoolean(
    ApplicationConstants.REVERSE_MAGNETIC_Z_PREFKEY, false);
// ...
magneticValues.z = reverseMagneticZaxis ? -values[2] : values[2];
```
Some devices have inverted magnetometer Z-axis. This is a user-configurable workaround.

### 3. Label Offset for Rolling Phone (LabelObjectManager:229-230)
```java
Matrix4x4 rotation = Matrix4x4.createRotation(rs.getUpAngle(), rs.getLookDir());
mLabelOffset = Matrix4x4.multiplyMV(rotation, rs.getUpDir());
```
Labels are offset "below" their associated objects. When the phone rolls, "below" changes, so the offset vector is rotated to match the current up angle.

---

## Summary

The sensor data flow correctly handles:
1. **Modern devices**: Uses fused rotation vector sensor for smooth, accurate orientation
2. **Legacy devices**: Falls back to accelerometer + magnetometer with exponential smoothing
3. **Missing gyroscope**: Automatically detected and forces classic path
4. **Text readability**: Roll angle is calculated, snapped to 90°, and applied as counter-rotation to labels

The math is sound and follows standard 3D graphics conventions for coordinate transformations.
