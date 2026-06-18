# Time Travel Feature

Time Travel allows users to view the sky at any date and time, past or future.

## Overview

Users can "travel" through time to see how the sky appeared on historical dates or will appear in the future. This is useful for:

- Planning observations of celestial events
- Understanding how the sky changes over time
- Viewing historical astronomical events
- Educational demonstrations

## User Interface

### Time Travel Dialog

```
┌─────────────────────────────────────────┐
│           Time Travel                    │
│  ─────────────────────────────────────  │
│                                         │
│  Date:  [January ▼] [15 ▼] [2024 ▼]    │
│                                         │
│  Time:  [21 ▼] : [30 ▼]                │
│                                         │
│  Speed: [◄◄] [◄] [▶] [▶▶]              │
│                                         │
│         -1 day/sec                      │
│                                         │
│  ─────────────────────────────────────  │
│  [Now]           [Cancel]    [Go]       │
└─────────────────────────────────────────┘
```

### Speed Controls

| Button | Speed | Description |
|--------|-------|-------------|
| ◄◄ | -1 week/sec | Fast backward |
| ◄ | -1 day/sec | Backward |
| ▶ | +1 day/sec | Forward |
| ▶▶ | +1 week/sec | Fast forward |
| ‖ | Paused | Frozen time |

### On-Screen Indicator

When time travel is active, the current simulated time is displayed:

```
┌────────────────────────────────────────┐
│                                        │
│  Time: July 4, 1776 12:00 PM          │  ← Time indicator
│                                        │
│         [Star map view]                │
│                                        │
└────────────────────────────────────────┘
```

## Implementation

### TimeTravelClock

`TimeTravelClock` manages simulated time:

```java
public class TimeTravelClock implements Clock {
    private long simulatedTimeMs;
    private float speedMultiplier = 0;  // 0 = paused
    private long lastUpdateMs;

    public long getTimeInMillisSinceEpoch() {
        long realElapsed = System.currentTimeMillis() - lastUpdateMs;
        long simulatedElapsed = (long)(realElapsed * speedMultiplier);
        return simulatedTimeMs + simulatedElapsed;
    }

    public void setSpeed(float multiplier) {
        // Commit current time before changing speed
        simulatedTimeMs = getTimeInMillisSinceEpoch();
        lastUpdateMs = System.currentTimeMillis();
        speedMultiplier = multiplier;
    }
}
```

### Speed Levels

13 discrete speed levels plus stopped:

| Level | Multiplier | Description |
|-------|------------|-------------|
| -6 | -604800 | 1 week per second backward |
| -5 | -86400 | 1 day per second backward |
| -4 | -3600 | 1 hour per second backward |
| -3 | -600 | 10 minutes per second backward |
| -2 | -60 | 1 minute per second backward |
| -1 | -1 | Real-time backward |
| 0 | 0 | Stopped (paused) |
| 1 | 1 | Real-time forward |
| 2 | 60 | 1 minute per second forward |
| 3 | 600 | 10 minutes per second forward |
| 4 | 3600 | 1 hour per second forward |
| 5 | 86400 | 1 day per second forward |
| 6 | 604800 | 1 week per second forward |

## Affected Components

### Solar System Layer

Planet positions recalculated using ephemeris:

```java
// Planet position at simulated time
public GeocentricCoordinates getPosition(long timeMs) {
    double julianDate = timeToJulianDate(timeMs);
    OrbitalElements elements = getOrbitalElements(julianDate);
    return calculatePosition(elements);
}
```

### Horizon Layer

Horizon adjusts for simulated time and location:
- Cardinal directions remain fixed
- Zenith/nadir positions unchanged
- Twilight calculations use simulated time

### Grid Layer

Celestial grid lines unchanged (fixed to celestial sphere).

### Star Layers

Stars are essentially fixed:
- Positions unchanged (proper motion negligible)
- Visibility based on simulated time of day

### ISS Layer

ISS tracking disabled during time travel:
- Orbital elements only valid for near-present
- Layer hidden or shows warning

## Use Cases

### Planning Observations

"When will Jupiter be visible tonight?"

1. Set time to tonight
2. Enable forward time flow
3. Watch Jupiter rise over horizon
4. Note optimal viewing time

### Historical Events

"What did the sky look like on July 20, 1969?"

1. Enter date/time of moon landing
2. Set to Apollo 11 landing site location
3. View where Moon appeared in sky

### Eclipse Prediction

"When is the next solar eclipse?"

1. Enable fast forward
2. Watch Sun and Moon positions
3. Stop when they align

### Seasonal Changes

"How do constellations change through the year?"

1. Set speed to 1 day per second
2. Watch zodiac constellations cycle
3. Understand seasonal visibility

## Limitations

### Accuracy Range

| Time Range | Accuracy |
|------------|----------|
| ±100 years | High precision |
| ±1000 years | Good precision |
| ±10000 years | Approximate |

### Not Modeled

- Stellar proper motion (star movement)
- Precession effects at extreme ranges
- Historical calendar changes
- Comet appearances (except current catalogs)

### ISS and Satellites

Satellite tracking disabled during time travel because:
- TLE data only valid for ~2 weeks
- Historical orbital elements not available
- Future orbits unpredictable

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `TimeTravelClock` | Simulated time management |
| `TimeTravelDialogFragment` | Date/time picker UI |
| `Clock` | Interface for time sources |
| `RealClock` | Normal system time |
