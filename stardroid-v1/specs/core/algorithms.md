# Core Algorithms - Mathematical & Sensor Fusion

## Purpose

Defines the **mathematical algorithms** used throughout Stardroid: coordinate transformations, precession calculations, sensor fusion, and time-related astronomical calculations.

## Coordinate Transform: Device → Sky

### Problem

Convert device orientation (from Android sensors) to celestial coordinates (where is the phone pointing in the sky?).

### Input Data

**Device Rotation Matrix** (from Android RotationSensor):
```
RotationMatrix[9] = {
    [X_x, X_y, X_z],  // Phone's X axis (right) in world coords
    [Y_x, Y_y, Y_z],  // Phone's Y axis (up) in world coords
    [Z_x, Z_y, Z_z]   // Phone's Z axis (forward, out of screen) in world coords
}
```

**User Location:**
- Latitude: φ (degrees, -90 to +90)
- Longitude: λ (degrees, -180 to +180)
- Altitude: h (meters above sea level, optional)

**Time:**
- Unix timestamp (milliseconds since 1970-01-01 UTC)

### Output Data

**View Direction in Celestial Coordinates:**
- Right Ascension: α (hours, 0-24 or degrees, 0-360)
- Declination: δ (degrees, -90 to +90)

### Algorithm

**Step 1: Extract phone axes from rotation matrix**

```
X_p = [X_x, X_y, X_z]  // Right direction
Y_p = [Y_x, Y_y, Y_z]  // Up direction
Z_p = [Z_x, Z_y, Z_z]  // Forward direction (out of screen)
```

**Step 2: Calculate celestial axes at user's location**

```
// Local Sidereal Time (LST)
LST = (GMST0 + longitude + (UTC × 1.00273790935)) × 15°/hour

where:
- GMST0 = Greenwich Mean Sidereal Time at 0h UT (from astronomical algorithms)
- UTC = Universal Time in hours
- Factor 1.00273790935 = Earth's rotation rate adjustment

// Zenith vector (pointing straight up from user)
Z_c = [0, cos(φ), sin(φ)]

// North vector (projection of Earth's rotation axis)
N_c = [0, -sin(φ), cos(φ)]

// East vector (perpendicular to Zenith and North)
E_c = N_c × Z_c
```

**Step 3: Construct transformation matrix M**

Transform from phone coordinates to celestial coordinates:

```
M = [E_c; N_c; Z_c]  ×  inverse([X_p; Y_p; Z_p])
```

In code (3x3 matrix multiplication):
```kotlin
val phoneToWorld = Matrix3x3(
    X_p.x, Y_p.x, Z_p.x,
    X_p.y, Y_p.y, Z_p.y,
    X_p.z, Y_p.z, Z_p.z
).inverse()

val celestialAxes = Matrix3x3(
    E.x, N.x, Z.x,
    E.y, N.y, Z.y,
    E.z, N.z, Z.z
)

val M = celestialAxes.multiply(phoneToWorld)
```

**Step 4: Transform view direction**

```
v_p = [0, 0, -1]  // Forward direction in phone coords (negative Z is forward)
v_c = M × v_p      // Direction in celestial coords
```

**Step 5: Convert to RA/Dec**

```
// Convert Cartesian to spherical
r = sqrt(v_c.x² + v_c.y² + v_c.z²)
alt = asin(v_c.z / r)  // Altitude
az  = atan2(v_c.x, -v_c.y)  // Azimuth (from North through East)

// Convert horizontal to celestial
LST_rad = LST × π/180
hour_angle = atan2(sin(az)×cos(alt), cos(az)×cos(alt) - tan(φ)×sin(alt))

α = LST_rad - hour_angle
δ = asin(sin(φ)×sin(alt) - cos(φ)×cos(alt)×cos(az))
```

### Complexity

**Performance:** O(1) - fixed matrix operations per sensor update
- Matrix inverse: O(27) - constant time 3×3 matrix
- Matrix multiplication: O(27)
- Trigonometric functions: O(1) each

## Precession Calculation: J2000 → Apparent

### Problem

Catalog coordinates are in J2000 epoch (January 1, 2000). Need to calculate where stars are **now**.

### Algorithm: IAU 2006 Precession

**Input:**
- α₀, δ₀: J2000 RA/Dec
- t: Julian centuries since J2000

**Output:**
- α, δ: Apparent RA/Dec

**Steps:**

1. **Calculate precession angles** (based on t):
   ```
   ζ_A = 2.650545 + 0.000398t² × t
   z_A = -0.028369 - 0.000039t² × t
   θ_A = 0.005036 + 0.000039t² × t
   ```

2. **Apply three rotations:**
   - Rotate around z-axis by ζ_A
   - Rotate around new y-axis by θ_A
   - Rotate around z-axis by z_A

3. **Add proper motion** (if available):
   ```
   α = α₀ + (pm_α × t)
   δ = δ₀ + (pm_δ × t)
   ```

**Reference:** *Astronomical Algorithms* by Jean Meeus, Chapter 21

## Sensor Fusion: Combining Sensors

### Problem

Android provides multiple sensors. Need smooth, accurate device orientation.

### Available Sensors

**Accelerometer:**
- Measures: Gravity vector (which way is "down")
- Update rate: ~50-200 Hz
- Pros: Always available on Android
- Cons: Noisy, affected by motion

**Magnetometer:**
- Measures: Magnetic field (which way is "North")
- Update rate: ~10-50 Hz
- Pros: Provides heading
- Cons: Noisy, affected by metal, magnetic interference

**Gyroscope:**
- Measures: Angular velocity (rotation rate)
- Update rate: ~100-1000 Hz
- Pros: Accurate, fast
- Cons: Drifts over time, no absolute reference

**Rotation Sensor (Android 4.1+):**
- Type: Virtual sensor (fused output)
- Provides: 3×3 rotation matrix
- Update rate: ~50 Hz
- Implementation: Uses Kalman filter internally

### Fusion Algorithm: Android's Rotation Sensor

**Recommended:** Use Android's `Sensor.TYPE_ROTATION_VECTOR`

```kotlin
val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

sensorManager.registerListener(this, rotationSensor,
    SensorManager.SENSOR_DELAY_GAME,  // ~50 Hz
    SensorManager.SENSOR_DELAY_UI
)
```

**Sensor event handler:**
```kotlin
override fun onSensorChanged(event: SensorEvent?) {
    val rotationMatrix = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(
        rotationMatrix,
        event?.values
    )
    // Feed to AstronomerModel
    astronomerModel.setRotationMatrix(rotationMatrix)
}
```

**Benefits:**
- Google's Kalman filter implementation
- Fuses all three sensors optimally
- Handles magnetic interference
- Compensates for gyroscope drift

### Fallback: Manual Sensor Fusion

For devices without rotation sensor (Android 2.3-4.0):

**Complementary Filter:**
```kotlin
val alpha = 0.97f  // Weight for gyroscope
val dt = (now - lastTime) / 1000.0f

// Gyroscope integration (dead reckoning)
gyroMatrix = gyroMatrix × gyroRotation(dt)

// Accelerometer/magnetometer reference (independent measurement)
accelMagMatrix = calculateFromAccelerometerAndMagnetometer()

// Complementary filter
fusionMatrix = alpha × gyroMatrix + (1 - alpha) × accelMagMatrix
```

**Limitations:** Inferior to Google's Kalman filter

## Time Calculations

### UTC → Terrestrial Time (TT)

**Purpose:** Accurate planetary positions require uniform time scale.

**Formula:**
```
TT = UTC + ΔT

where ΔT = TT - UTC (leap seconds + historical variations)
```

**Current value (2024):** ΔT ≈ 69 seconds

**Data source:** IERS Bulletin A (published online)

### UTC → Local Sidereal Time

**Purpose:** Convert UTC to Earth rotation angle for coordinate transform.

**Algorithm:**

1. **Calculate Julian Day (JD):**
   ```
   JD = 367 × Y − int(7 × (Y + int((M + 9) / 12)) / 4) + int(275 × M / 9) + D + 1721013.5
   ```

2. **Calculate GMST0 (Greenwich Mean Sidereal Time at 0h UT):**
   ```
   T = (JD - 2451545.0) / 36525
   GMST0 = 280.46061837 + 360.98564736629 × T + 0.000387933 × T² - T³/38710000
   ```

3. **Calculate LST:**
   ```
   LST = GMST0 + (longitude × 15) + (UTC × 15.04106864)
   ```

### Apparent vs. Mean Solar Time

**Equation of Time (EoT):**
```
EoT = apparent solar time - mean solar time
    ≈ -4° × tan(λ)  // Where λ is ecliptic longitude
```

**Purpose:** Sun appears fastest/slowest relative to clock (analemma effect)

## Magnetic Declination

### Problem

Magnetic "North" ≠ true "North". Need correction for compass accuracy.

### Zero Declination (F-Droid)

**Assumption:** Magnetic North ≈ True North (sufficient for casual use)

**Implementation:**
```kotlin
object ZeroMagneticDeclinationCalculator : MagneticDeclinationCalculator {
    override fun getDeclination(lat: Double, lon: Double, time: Long): Double {
        return 0.0
    }
}
```

### Real Declination (GMS)

**Algorithm:** World Magnetic Model (WMM)

**Input:** Latitude, longitude, altitude, date

**Output:** Magnetic declination angle (variation)

**Implementation:** Use `GeomagneticField` API
```kotlin
val geomagneticField = GeomagneticField.getInstance()
val location = GeomagneticFieldLocation(lat, lon, altitude)
val field = geomagneticField.getField(location, time.toLong())

val declination = field.declination  // Radians, convert to degrees
```

**Accuracy:** ±0.5° typically, better away from magnetic poles

## Performance Optimizations

### Trigonometry Tables (Historical)

**Not used anymore:** Modern FPUs make trigonometry fast enough

**Alternative:** `Math.sin()` etc. are hardware-accelerated on ARM

### Lookup Tables (Ephemeris)

**Precompute:** Planet positions for fast access
```kotlin
val planetPositions = Array(24 × 60) { hour × minute → RA/Dec }
```

**Benefit:** Avoid repeated ephemeris calculations

## Testing

### Coordinate Transform Tests

**Test method:** Known positions → verify transformation

```kotlin
@Test
fun testCoordinateTransform() {
    // Zenith should map to (alt=90°, az=undefined)
    val zenith = calculateViewDirection(latitude = 45.0, longitude = 0.0, time = now)
    assertTrue(zenith.altitude > 89.9)
}
```

### Precession Tests

**Test method:** Compare against JPL Horizons

```kotlin
@Test
fun testPrecession() {
    val j2000 = RaDec(ra = 6.75h, dec = -16.71°)
    val apparent = applyPrecession(j2000, year = 2024)
    val jpl = queryJPLHorizons(star = "Sirius", date = "2024-06-15")
    assertEquals(jpl.ra, apparent.ra, tolerance = 0.01°)
}
```

## Related Specifications

- [README.md](README.md) - Core domain overview
- [../sensors/orientation.md](../sensors/orientation.md) - Sensor integration details
- [../sensors/coordinate-transform.md](../sensors/coordinate-transform.md) - Mathematical derivation
- [../data/ephemeris.md](../data/ephemeris.md) - Planetary calculations
