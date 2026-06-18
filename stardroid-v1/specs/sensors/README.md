# Sensor System Overview

This section documents Sky Map's sensor integration for device orientation tracking.

## Contents

| Specification | Description |
|--------------|-------------|
| [Orientation](orientation.md) | Device orientation detection |
| [Coordinate Transform](coordinate-transform.md) | Phone to celestial coordinate mapping |
| [Manual Control](manual-control.md) | Touch-based navigation without sensors |

## Sensor Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android Sensors                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │Accelerometer│  │Magnetometer │  │  Gyroscope  │             │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │
│         │                │                │                      │
│         └────────────────┴────────────────┘                      │
│                          │                                       │
│                          ▼                                       │
│         ┌────────────────────────────────┐                      │
│         │    Rotation Sensor (Fused)     │  ← Preferred         │
│         └───────────────┬────────────────┘                      │
│                         │                                        │
│                         │ ┌──────────────────────────────────┐  │
│                         │ │   Legacy Sensor Fusion           │  │
│                         │ │   (Fallback if no gyroscope)     │  │
│                         │ └──────────────┬───────────────────┘  │
│                         │                │                       │
│                         └────────┬───────┘                       │
│                                  │                               │
└──────────────────────────────────┼───────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SensorOrientationController                     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • Receives rotation matrix from sensors                  │  │
│  │  • Applies sensor damping (smoothing filter)              │  │
│  │  • Handles phone viewing direction preference              │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       AstronomerModel                            │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • Transforms phone orientation to celestial coordinates  │  │
│  │  • Applies magnetic declination correction                │  │
│  │  • Calculates pointing direction (RA/Dec)                 │  │
│  │  • Manages field of view                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                        SkyRenderer                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  • Uses pointing direction to construct view matrix       │  │
│  │  • Renders celestial objects visible in current view      │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Sensor Types Used

### Primary: Rotation Vector Sensor

Android's fused rotation sensor combining:
- Accelerometer (gravity direction)
- Magnetometer (magnetic north)
- Gyroscope (rotation rate)

Advantages:
- Pre-fused by Android system
- Drift compensation
- Smooth output

### Fallback: Raw Sensors

When rotation sensor unavailable:
- Accelerometer for up/down (gravity)
- Magnetometer for north direction

Disadvantages:
- Requires manual fusion
- More susceptible to noise
- No gyroscope drift compensation

## Coordinate Systems

### Phone Coordinates

Device-relative axes:
- **X**: Right (along screen width)
- **Y**: Up (along screen height)
- **Z**: Out of screen (toward user)

### Celestial Coordinates

Right Ascension (RA) and Declination (Dec):
- **RA**: 0-24 hours around celestial equator
- **Dec**: -90° (south pole) to +90° (north pole)

### Geocentric Coordinates

Unit vectors on celestial sphere:
- x, y, z components
- Used internally for calculations

## Controller Architecture

```java
interface OrientationController {
    void start();
    void stop();
    void setModel(AstronomerModel model);
}

class ControllerGroup {
    SensorOrientationController sensorController;
    ManualOrientationController manualController;
    OrientationController activeController;

    void setManualMode(boolean manual) {
        activeController = manual ? manualController : sensorController;
    }
}
```

## Sensor Settings

| Setting | Effect |
|---------|--------|
| Disable Gyroscope | Force legacy sensor fusion |
| Sensor Speed | Sample rate (SLOW/STANDARD/FAST) |
| Sensor Damping | Smoothing filter strength |
| Reverse Magnetic Z | Fix for some devices |
| Viewing Direction | Phone orientation mode |

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `SensorOrientationController` | Sensor event handling |
| `AstronomerModel` | Coordinate transformation |
| `ManualOrientationController` | Touch-based navigation |
| `ControllerGroup` | Controller switching |
| `MagneticDeclinationCalculator` | Magnetic correction |
