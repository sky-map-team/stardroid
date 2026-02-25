# Core Domain - Astronomical & Sensor Fusion

## Purpose

Defines the **stable intellectual core** of Stardroid - the astronomical calculations, coordinate transformations, and sensor fusion algorithms that make the app work. This domain is rendering-agnostic, UI-agnostic, and represents the app's essential value.

## Key Concepts

### Celestial Coordinate Systems
The app operates in multiple coordinate systems and transforms between them:

1. **J2000 Equatorial Coordinates** - The canonical astronomical reference frame
   - Right Ascension (RA): 0-360° around celestial equator
   - Declination (Dec): -90° to +90° from celestial pole
   - Epoch J2000.0 (January 1, 2000, 12:00 TT)

2. **Apparent Coordinates** - Precessed to current date
   - Accounts for Earth's axial precession (26,000 year cycle)
   - Used for display and user interaction

3. **Horizontal Coordinates** - Device/sky-relative reference frame
   - Azimuth: 0-360° compass direction (0° = North)
   - Altitude: -90° to +90° elevation angle
   - The user's actual view of the sky

4. **Device Coordinates** - Phone-relative frame
   - X, Y, Z axes relative to phone screen
   - Derived from Android sensor reports

### Sensor Fusion
Combining multiple sensors to determine device orientation:

- **Accelerometer** - Gravity vector (which way is "down")
- **Magnetometer** - Magnetic field (which way is "North")
- **Gyroscope** - Rotation rate (smooth fast movements)
- **Rotation Sensor** (Android 4+) - Fused sensor combining all three

### Time Representations
Multiple time systems are relevant for astronomy:

- **UTC** - Coordinated Universal Time (civil time)
- **TT** - Terrestrial Time (uniform time scale for ephemerides)
- **Local Sidereal Time** - Earth rotation relative to stars
- **Delta-T** - TT minus UTC (leap seconds + historical variations)

## Core Algorithms

### Coordinate Transformation: Phone → Sky

The fundamental algorithm: **Where is the phone pointing in the sky?**

**Input:**
- Device rotation matrix from Android `RotationSensor`
- User's geodetic location (latitude, longitude)
- Current time (UTC → Local Sidereal Time)

**Output:**
- View direction in RA/Dec coordinates
- Altitude/azimuth of view center

**Algorithm:**
1. Extract phone axes from rotation matrix:
   - Z_p: Phone's "forward" direction (out of screen)
   - Y_p: Phone's "up" direction (toward top of screen)
   - X_p: Phone's "right" direction (toward right of screen)

2. Calculate celestial axes at user's location:
   - Z_c: Zenith (straight up from user)
   - N_c: North (projection of Earth's rotation axis)
   - E_c: East (perpendicular to Zenith and North)

3. Construct transformation matrix M from {X_p, Y_p, Z_p} to {E_c, N_c, Z_c}

4. For any direction v_p in phone coordinates, v_c = M × v_p in celestial coordinates

5. Convert v_c to RA/Dec using standard spherical trigonometry

**Complexity:** O(1) - fixed matrix operations per sensor update

### Precession Calculation: J2000 → Apparent

**Purpose:** Convert J2000 catalog coordinates to current date for display

**Algorithm:** IAU 2006 precession model (simplified version)

**Input:**
- J2000 RA/Dec (α₀, δ₀)
- Target date (Julian centuries since J2000)

**Output:**
- Apparent RA/Dec (α, δ)

**Steps:**
1. Calculate precession angles (ζ_A, z_A, θ_A) based on time
2. Apply three rotations around different axes
3. Account for proper motion (if available in catalog)

**Reference:** *Astronomical Algorithms* by Jean Meeus

### Magnetic Declination Correction

**Purpose:** Align magnetic "North" with true North for accuracy

**Algorithm:**
- **Zero Declination** - F-Droid flavor, assumes magnetic North ≈ true North (fast, approximate)
- **Real Declination** - GMS flavor, uses World Magnetic Model for accuracy

**Input:** User's location, current date

**Output:** Magnetic declination angle (variation)

**Usage:** Rotate magnetic North by this angle to get true North

## Core Interfaces

### AstronomerModel

```kotlin
interface AstronomerModel {
    // Input from sensors
    fun setRotationMatrix(matrix: FloatArray)
    fun setLocation(latitude: Double, longitude: Double)
    fun setTime(timeInMillis: Long)

    // Output to rendering
    fun getViewDirection(): Vector3  // In celestial coordinates
    fun getZenith(): Vector3
    fun getNorth(): Vector3
    fun getEast(): Vector3

    // Queries
    fun getRAAndDec(x: Float, y: Float): RaDec
}
```

### MagneticDeclinationCalculator

```kotlin
interface MagneticDeclinationCalculator {
    fun getDeclination(latitude: Double, longitude: Double, time: Long): Double
}
```

**Implementations:**
- `ZeroMagneticDeclinationCalculator` - Returns 0.0
- `RealMagneticDeclinationCalculator` - World Magnetic Model

### CelestialObject

```kotlin
data class CelestialObject(
    val id: String,
    val name: String,
    val ra: Float,      // J2000 Right Ascension (hours)
    val dec: Float,    // J2000 Declination (degrees)
    val magnitude: Float,
    val color: Color,   // For rendering
    val size: Float,    // Angular size in degrees

    // Optional
    val properMotionRA: Float? = null,
    val properMotionDec: Float? = null,
    val epoch: Float = 2000f
)
```

## Data Dependencies

### Catalogs (Read-only at runtime)
- **Stars** - Hipparcos/Tycho-2 catalog (~250,000 stars)
- **Constellations** - 88 modern constellation boundaries
- **Messier Objects** - 110 deep-sky objects
- **Solar System** - Planets, Sun, Moon (ephemeris data)

### Static Datasets
- **Constellation lines** - Patterns connecting stars
- **Grid lines** - RA/Dec grid, Alt/Az grid, Ecliptic
- **Cultural data** - Constellation names, boundaries

## Performance Considerations

### Sensor Update Rate
- **Target:** 30-60 Hz for smooth UI
- **Current:** Android delivers ~50 Hz rotation sensor events
- **Optimization:** Throttle rendering, don't block sensor thread

### Coordinate Precision
- **Float precision sufficient** for display purposes
- **Double precision needed** for:
  - Precession calculations (accumulates error over time)
  - Planetary ephemerides (high precision requirements)
  - Sidereal time (hours of precision matter)

### Memory Footprint
- **Catalogs:** ~10MB uncompressed
- **Loaded layers:** ~50MB in memory
- **Streaming:** Load on demand, cache LRU for performance

## Testing Strategy

### Unit Tests
- Coordinate transformation: Known positions → verify transformations
- Precession: Compare against JPL Horizons for test dates
- Magnetic declination: Known locations → verify correction

### Integration Tests
- Sensor fusion: Feed simulated sensor data → verify final RA/Dec
- Time travel: Set time to 2050 → verify star positions shift

### Accuracy Requirements
- **Display:** ±0.1° sufficient for visual alignment
- **Search:** ±0.01° required for object matching
- **Planets:** ±0.001° required (planets move slowly)

## Dependencies on Other Layers

### Depends On:
- **Data layer** - Celestial catalogs, ephemeris data
- **Sensor layer** - Android sensor APIs

### Used By:
- **Rendering layer** - Gets coordinates to draw objects at
- **Search layer** - Queries objects by location
- **UI layer** - Displays coordinates to user

## Future Enhancements (AR Considerations)

### AR Coordinate Systems
For AR mode, need additional transformations:
- **ARCore "World" coordinates** → Celestial coordinates
- **GPS location** → Altitude correction for terrain
- **Camera pose** → Determine what celestial object is at pixel (x,y)

### Real-Time Ephemerides
Current: Planets calculated once per frame
Future: Cache for multiple frames, recalculate only when time changes significantly

## Related Specifications

- [../sensors/orientation.md](../sensors/orientation.md) - Sensor integration details
- [../sensors/coordinate-transform.md](../sensors/coordinate-transform.md) - Mathematical details
- [../data/catalogs.md](../data/catalogs.md) - Catalog formats
- [../data/ephemeris.md](../data/ephemeris.md) - Planetary calculations
