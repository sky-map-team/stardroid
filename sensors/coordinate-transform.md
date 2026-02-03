# Coordinate Transformation

This document describes how Sky Map transforms phone orientation into celestial coordinates.

## Overview

The core challenge is mapping device orientation (where the phone is pointing) to celestial coordinates (what stars are visible in that direction).

```
Phone Orientation (sensors) ──► Transformation ──► Celestial Coordinates (RA/Dec)
```

## Coordinate Systems

### Phone Coordinate System

Device-relative axes (right-handed):

```
        Y (up)
        │
        │
        │
        └───────── X (right)
       /
      /
     Z (out of screen)
```

- **X**: Positive toward right edge of screen
- **Y**: Positive toward top of screen
- **Z**: Positive out of screen (toward user)

### Celestial Coordinate System

Right Ascension (RA) and Declination (Dec):

```
                    North Celestial Pole
                           │ Dec = +90°
                           │
                           │
    RA = 6h ───────────────┼─────────────── RA = 18h
                           │
                           │
                           │ Dec = -90°
                    South Celestial Pole

    RA increases eastward: 0h → 6h → 12h → 18h → 24h (= 0h)
```

### Geocentric Coordinate System

Unit vectors on celestial sphere:

```java
public class GeocentricCoordinates {
    float x, y, z;  // |v| = 1

    // x = cos(dec) * cos(ra)
    // y = cos(dec) * sin(ra)
    // z = sin(dec)
}
```

## Transformation Algorithm

The algorithm is documented in `designdocs/sensors.md` and implemented in `AstronomerModel`.

### Step 1: Define Reference Vectors

Three orthogonal vectors define orientation in each coordinate system.

#### Phone Reference Vectors (from sensors)

```java
// Up vector (gravity direction, inverted)
Vector3 upPhone = normalize(-accelerometer);

// North vector (magnetic field projected to horizontal)
Vector3 northPhone = normalize(magnetometer - project(magnetometer, upPhone));

// East vector (cross product)
Vector3 eastPhone = cross(northPhone, upPhone);
```

#### Celestial Reference Vectors (from location and time)

```java
// Up vector (zenith) - depends on latitude and local sidereal time
float lst = calculateLocalSiderealTime(longitude, time);
Vector3 upCelestial = new Vector3(
    cos(latitude) * cos(lst),
    cos(latitude) * sin(lst),
    sin(latitude)
);

// North vector (celestial pole projected to horizon)
Vector3 celestialPole = new Vector3(0, 0, 1);
Vector3 northCelestial = normalize(
    celestialPole - project(celestialPole, upCelestial)
);

// East vector
Vector3 eastCelestial = cross(northCelestial, upCelestial);
```

### Step 2: Construct Transformation Matrix

The transformation maps phone vectors to celestial vectors:

```
[N_c]   [N_p]
[U_c] = M × [U_p]
[E_c]   [E_p]
```

Where:
- `N_c, U_c, E_c` = North, Up, East in celestial coords
- `N_p, U_p, E_p` = North, Up, East in phone coords
- `M` = transformation matrix

```java
// Build matrix from column vectors
Matrix3x3 phoneBasis = new Matrix3x3(
    northPhone, upPhone, eastPhone  // columns
);

Matrix3x3 celestialBasis = new Matrix3x3(
    northCelestial, upCelestial, eastCelestial  // columns
);

// M = celestialBasis × phoneBasis^(-1)
// Since basis matrices are orthonormal: inverse = transpose
Matrix3x3 M = multiply(celestialBasis, transpose(phoneBasis));
```

### Step 3: Transform Pointing Direction

Transform the phone's -Z axis (looking direction) to celestial coordinates:

```java
// Phone looking direction (out of screen)
Vector3 lookPhone = new Vector3(0, 0, -1);

// Transform to celestial coordinates
Vector3 lookCelestial = multiply(M, lookPhone);

// Convert to RA/Dec
float dec = asin(lookCelestial.z);
float ra = atan2(lookCelestial.y, lookCelestial.x);
```

## Implementation

### AstronomerModel

```java
public class AstronomerModel {
    private GeocentricCoordinates pointing;
    private GeocentricCoordinates zenith;
    private GeocentricCoordinates celestialNorth;
    private float fieldOfView;

    // Phone orientation (from sensors)
    private Matrix3x3 phoneRotation;

    // User location
    private LatLong location;

    // Time (real or time-travel)
    private Clock clock;

    public void setPhoneOrientation(float[] rotationMatrix) {
        this.phoneRotation = new Matrix3x3(rotationMatrix);
        updatePointing();
    }

    private void updatePointing() {
        // Calculate celestial reference frame
        float lst = calculateLST(location.longitude, clock.getTime());
        zenith = calculateZenith(location.latitude, lst);
        celestialNorth = calculateCelestialNorth(zenith);

        // Apply magnetic declination correction (optional)
        if (useMagneticCorrection) {
            celestialNorth = applyMagneticDeclination(celestialNorth);
        }

        // Transform phone pointing to celestial
        Matrix3x3 transform = buildTransformMatrix();
        Vector3 phoneLook = new Vector3(0, 0, -1);
        pointing = multiply(transform, phoneLook);
    }

    public GeocentricCoordinates getPointing() {
        return pointing;
    }

    public float getRa() {
        return (float) Math.toDegrees(Math.atan2(pointing.y, pointing.x)) / 15f;
    }

    public float getDec() {
        return (float) Math.toDegrees(Math.asin(pointing.z));
    }
}
```

### Local Sidereal Time

```java
/**
 * Calculate Local Sidereal Time.
 *
 * @param longitude Observer's longitude in degrees
 * @param time Unix timestamp in milliseconds
 * @return LST in radians
 */
public static float calculateLST(float longitude, long time) {
    // Julian date
    double jd = (time / 86400000.0) + 2440587.5;

    // Julian centuries from J2000.0
    double T = (jd - 2451545.0) / 36525.0;

    // Greenwich Mean Sidereal Time (degrees)
    double gmst = 280.46061837
                + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * T * T
                - T * T * T / 38710000.0;

    // Local sidereal time
    double lst = gmst + longitude;

    // Normalize to 0-360
    lst = ((lst % 360) + 360) % 360;

    return (float) Math.toRadians(lst);
}
```

### Magnetic Declination Correction

```java
/**
 * Apply magnetic declination to convert magnetic north to true north.
 */
private GeocentricCoordinates applyMagneticDeclination(GeocentricCoordinates north) {
    float declination = getMagneticDeclination(
        location.latitude,
        location.longitude,
        0  // altitude (sea level approximation)
    );

    // Rotate north vector by declination angle
    Matrix3x3 rotation = Matrix3x3.rotateAroundAxis(zenith, declination);
    return multiply(rotation, north);
}
```

## View Matrix Construction

The renderer uses the transformation to build an OpenGL view matrix:

```java
public float[] getViewMatrix() {
    // Camera at origin, looking at pointing direction
    Vector3 eye = new Vector3(0, 0, 0);
    Vector3 center = pointing;
    Vector3 up = zenith;

    return Matrix.lookAt(eye, center, up);
}
```

## Precision Considerations

### Floating Point Accuracy

| Operation | Precision Notes |
|-----------|-----------------|
| Sidereal time | Double precision required |
| Rotation matrices | Float sufficient |
| Trigonometry | Use `Math` (double) then cast |

### Time Sensitivity

Position changes with time:
- Earth rotation: 15°/hour
- Update rate: ~60 Hz
- Position change per frame: ~0.07 arc-seconds

### Location Sensitivity

| Error | Effect |
|-------|--------|
| 1° latitude | ~1° zenith offset |
| 1° longitude | ~4 minutes LST error |

## Key Files

| File | Purpose |
|------|---------|
| `AstronomerModel.java` | Main transformation logic |
| `designdocs/sensors.md` | Algorithm documentation |
| `GeocentricCoordinates.java` | Coordinate representation |
| `Matrix3x3.java` | Matrix operations |
