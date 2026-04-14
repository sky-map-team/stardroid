# ISS Tracking

Sky Map tracks the International Space Station in real-time using orbital element data.

## Overview

The ISS layer displays the current position of the International Space Station, updated periodically from network sources.

## Data Source

### Two-Line Element Sets (TLE)

TLE data describes satellite orbits in a standard format:

```
ISS (ZARYA)
1 25544U 98067A   24001.50000000  .00016717  00000-0  10270-3 0  9994
2 25544  51.6416 247.4627 0006703  85.9719 274.1879 15.49815741431256
```

### TLE Format

**Line 1:**
| Field | Columns | Description |
|-------|---------|-------------|
| Satellite number | 3-7 | NORAD catalog ID |
| Classification | 8 | U = Unclassified |
| Launch year | 10-11 | Last 2 digits |
| Launch number | 12-14 | Launch of year |
| Piece | 15-17 | Piece of launch |
| Epoch year | 19-20 | Last 2 digits |
| Epoch day | 21-32 | Day + fraction |
| First derivative | 34-43 | Mean motion derivative |
| Second derivative | 45-52 | Mean motion 2nd derivative |
| BSTAR drag | 54-61 | Atmospheric drag |
| Element set | 65-68 | Element set number |
| Checksum | 69 | Modulo 10 checksum |

**Line 2:**
| Field | Columns | Description |
|-------|---------|-------------|
| Satellite number | 3-7 | NORAD catalog ID |
| Inclination | 9-16 | Degrees |
| RAAN | 18-25 | Right ascension of ascending node |
| Eccentricity | 27-33 | Decimal point assumed |
| Argument of perigee | 35-42 | Degrees |
| Mean anomaly | 44-51 | Degrees |
| Mean motion | 53-63 | Revolutions per day |
| Revolution number | 64-68 | At epoch |
| Checksum | 69 | Modulo 10 checksum |

## Data Sources

### Primary: wheretheiss.at

```java
private static final String TLE_URL =
    "https://api.wheretheiss.at/v1/satellites/25544/tles";
```

### Fallback: Celestrak

```java
private static final String CELESTRAK_URL =
    "https://celestrak.com/NORAD/elements/stations.txt";
```

## Implementation

### IssLayer

```java
public class IssLayer extends AbstractLayer {
    private static final long UPDATE_INTERVAL_MS = 60_000;  // 60 seconds

    private final ScheduledExecutorService executor;
    private final OkHttpClient httpClient;
    private TleData currentTle;
    private GeocentricCoordinates currentPosition;

    @Override
    public void initialize() {
        executor.scheduleAtFixedRate(
            this::updateTle,
            0,
            UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    private void updateTle() {
        try {
            String response = httpClient.get(TLE_URL);
            currentTle = TleParser.parse(response);
            updatePosition();
        } catch (IOException e) {
            Log.w(TAG, "Failed to fetch TLE data", e);
        }
    }
}
```

### TLE Parser

```java
public class TleParser {
    public static TleData parse(String tleString) {
        String[] lines = tleString.trim().split("\n");

        TleData tle = new TleData();
        tle.name = lines[0].trim();

        // Parse line 1
        tle.epochYear = Integer.parseInt(lines[1].substring(18, 20));
        tle.epochDay = Double.parseDouble(lines[1].substring(20, 32));
        tle.bstar = parseScientific(lines[1].substring(53, 61));

        // Parse line 2
        tle.inclination = Double.parseDouble(lines[2].substring(8, 16));
        tle.raan = Double.parseDouble(lines[2].substring(17, 25));
        tle.eccentricity = Double.parseDouble("0." + lines[2].substring(26, 33));
        tle.argPerigee = Double.parseDouble(lines[2].substring(34, 42));
        tle.meanAnomaly = Double.parseDouble(lines[2].substring(43, 51));
        tle.meanMotion = Double.parseDouble(lines[2].substring(52, 63));

        return tle;
    }
}
```

### SGP4 Propagator

Simplified General Perturbations model for orbit propagation:

```java
public class Sgp4Propagator {
    private final TleData tle;

    public Sgp4Propagator(TleData tle) {
        this.tle = tle;
        initialize();
    }

    /**
     * Propagate orbit to specified time.
     *
     * @param julianDate Target time
     * @return Position in TEME coordinates (km)
     */
    public Vector3 propagate(double julianDate) {
        // Minutes since TLE epoch
        double tsince = (julianDate - tle.getEpochJd()) * 1440.0;

        // SGP4 propagation (simplified)
        double n0 = tle.meanMotion * (2 * Math.PI / 1440.0);  // rad/min
        double a = Math.pow(MU_EARTH / (n0 * n0), 1.0/3.0);   // km

        // Mean anomaly at time
        double M = tle.meanAnomaly + n0 * tsince;

        // Solve Kepler's equation
        double E = solveKepler(M, tle.eccentricity);

        // Position in orbital plane
        double r = a * (1 - tle.eccentricity * Math.cos(E));
        double x = a * (Math.cos(E) - tle.eccentricity);
        double y = a * Math.sqrt(1 - tle.eccentricity * tle.eccentricity) * Math.sin(E);

        // Rotate to TEME frame
        return rotateToTeme(x, y, r, tsince);
    }
}
```

### Coordinate Conversion

```java
public class SatelliteCoordinates {
    /**
     * Convert TEME (True Equator Mean Equinox) to geocentric.
     */
    public static GeocentricCoordinates temeToGeocentric(
            Vector3 teme, double julianDate) {

        // Earth rotation angle
        double gmst = calculateGmst(julianDate);

        // Rotate from TEME to ECI
        double x = teme.x * Math.cos(gmst) + teme.y * Math.sin(gmst);
        double y = -teme.x * Math.sin(gmst) + teme.y * Math.cos(gmst);
        double z = teme.z;

        // Normalize to unit vector (direction only)
        double r = Math.sqrt(x*x + y*y + z*z);
        return new GeocentricCoordinates(
            (float)(x / r),
            (float)(y / r),
            (float)(z / r)
        );
    }
}
```

## Display

### ISS Icon

The ISS is displayed as:
- Custom icon image
- Label "ISS"
- Path prediction (optional)

### Visibility

| Condition | Display |
|-----------|---------|
| Above horizon | Full opacity |
| Below horizon | Hidden or dimmed |
| In Earth's shadow | Dimmed indicator |

## Update Cycle

```
┌─────────────────────────────────────────────────────────────────┐
│                      Every 60 seconds                           │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                  │
│  │  Fetch   │ ─► │  Parse   │ ─► │  Store   │                  │
│  │  TLE     │    │  TLE     │    │  TLE     │                  │
│  └──────────┘    └──────────┘    └──────────┘                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      Every frame                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                  │
│  │ Current  │ ─► │  SGP4    │ ─► │  Update  │                  │
│  │  Time    │    │ Propagate│    │ Position │                  │
│  └──────────┘    └──────────┘    └──────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

## Limitations

### TLE Validity

| Age | Accuracy |
|-----|----------|
| < 1 day | < 1 km |
| 1-7 days | < 10 km |
| 1-2 weeks | < 100 km |
| > 2 weeks | Poor, may fail |

### Time Travel Mode

ISS tracking disabled during time travel because:
- TLE data only valid for ~2 weeks
- Historical TLE data not available
- Future positions unpredictable

### Network Requirements

- Requires internet connection for TLE updates
- Uses cached TLE when offline (accuracy degrades)
- Graceful degradation: layer hidden if no data

## Error Handling

```java
private void updateTle() {
    try {
        String response = httpClient.get(TLE_URL);
        currentTle = TleParser.parse(response);
        lastSuccessfulUpdate = System.currentTimeMillis();
    } catch (IOException e) {
        Log.w(TAG, "TLE fetch failed, using cached data");
        if (isTleTooOld()) {
            setVisible(false);
        }
    }
}

private boolean isTleTooOld() {
    long age = System.currentTimeMillis() - lastSuccessfulUpdate;
    return age > MAX_TLE_AGE_MS;  // 2 weeks
}
```

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `IssLayer` | Layer implementation |
| `TleParser` | Parse TLE format |
| `Sgp4Propagator` | Orbit propagation |
| `SatelliteCoordinates` | Coordinate conversion |
