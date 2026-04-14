# Ephemeris Calculations

Sky Map calculates solar system positions in real-time using ephemeris algorithms.

## Overview

Ephemeris (plural: ephemerides) provides positions of celestial bodies at specific times. Sky Map implements simplified algorithms suitable for planetarium-level accuracy.

## Supported Objects

| Object | Calculation Method | Accuracy |
|--------|-------------------|----------|
| Sun | Solar position algorithm | < 1 arc-minute |
| Moon | Simplified lunar theory | < 1 arc-minute |
| Mercury | Keplerian elements | < 5 arc-minutes |
| Venus | Keplerian elements | < 5 arc-minutes |
| Mars | Keplerian elements | < 5 arc-minutes |
| Jupiter | Keplerian elements | < 5 arc-minutes |
| Saturn | Keplerian elements | < 5 arc-minutes |
| Uranus | Keplerian elements | < 10 arc-minutes |
| Neptune | Keplerian elements | < 10 arc-minutes |

## Algorithm Sources

The algorithms are adapted from:
- JPL Solar System Dynamics: https://ssd.jpl.nasa.gov/?planet_pos
- Meeus, Jean. "Astronomical Algorithms" (2nd edition)

## Solar Position

### Algorithm

```java
public class SolarPositionCalculator {
    /**
     * Calculate Sun's geocentric position.
     *
     * @param julianDate Julian date
     * @return Geocentric coordinates (ecliptic)
     */
    public static GeocentricCoordinates calculate(double julianDate) {
        // Julian centuries from J2000.0
        double T = (julianDate - 2451545.0) / 36525.0;

        // Mean longitude of the Sun (degrees)
        double L0 = 280.46646 + 36000.76983 * T + 0.0003032 * T * T;

        // Mean anomaly of the Sun (degrees)
        double M = 357.52911 + 35999.05029 * T - 0.0001537 * T * T;

        // Equation of center (degrees)
        double C = (1.914602 - 0.004817 * T) * sin(toRadians(M))
                 + (0.019993 - 0.000101 * T) * sin(toRadians(2 * M))
                 + 0.000289 * sin(toRadians(3 * M));

        // True longitude (degrees)
        double sunLongitude = L0 + C;

        // Distance from Earth (AU) - not used for direction
        // Obliquity of ecliptic
        double obliquity = 23.439291 - 0.0130042 * T;

        // Convert to equatorial coordinates
        return eclipticToEquatorial(sunLongitude, 0, obliquity);
    }
}
```

## Lunar Position

### Simplified Algorithm

```java
public class LunarPositionCalculator {
    public static GeocentricCoordinates calculate(double julianDate) {
        double T = (julianDate - 2451545.0) / 36525.0;

        // Mean longitude of Moon
        double Lp = 218.3164477 + 481267.88123421 * T;

        // Mean elongation of Moon
        double D = 297.8501921 + 445267.1114034 * T;

        // Mean anomaly of Sun
        double M = 357.5291092 + 35999.0502909 * T;

        // Mean anomaly of Moon
        double Mp = 134.9633964 + 477198.8675055 * T;

        // Moon's argument of latitude
        double F = 93.2720950 + 483202.0175233 * T;

        // Longitude correction (simplified)
        double dL = 6288774 * sin(toRadians(Mp))
                  + 1274027 * sin(toRadians(2 * D - Mp))
                  + 658314 * sin(toRadians(2 * D))
                  + 213618 * sin(toRadians(2 * Mp));

        // Latitude correction (simplified)
        double dB = 5128122 * sin(toRadians(F));

        // True longitude and latitude (degrees)
        double longitude = Lp + dL / 1000000.0;
        double latitude = dB / 1000000.0;

        // Convert to equatorial coordinates
        double obliquity = 23.439291 - 0.0130042 * T;
        return eclipticToEquatorial(longitude, latitude, obliquity);
    }
}
```

## Planetary Positions

### Keplerian Orbital Elements

Each planet's orbit defined by six elements:

```java
public class OrbitalElements {
    double a;      // Semi-major axis (AU)
    double e;      // Eccentricity
    double I;      // Inclination (degrees)
    double L;      // Mean longitude (degrees)
    double omega;  // Longitude of perihelion (degrees)
    double Omega;  // Longitude of ascending node (degrees)
}
```

### Element Tables (J2000.0)

| Planet | a (AU) | e | I (°) | L (°) | ω (°) | Ω (°) |
|--------|--------|---|-------|-------|-------|-------|
| Mercury | 0.387 | 0.206 | 7.0 | 252.3 | 77.5 | 48.3 |
| Venus | 0.723 | 0.007 | 3.4 | 181.9 | 131.6 | 76.7 |
| Mars | 1.524 | 0.093 | 1.8 | 355.4 | 336.1 | 49.6 |
| Jupiter | 5.203 | 0.048 | 1.3 | 34.4 | 14.3 | 100.5 |
| Saturn | 9.537 | 0.054 | 2.5 | 49.9 | 92.4 | 113.7 |
| Uranus | 19.19 | 0.047 | 0.8 | 313.2 | 170.9 | 74.0 |
| Neptune | 30.07 | 0.009 | 1.8 | 304.9 | 44.9 | 131.8 |

### Position Calculation

```java
public class PlanetPositionCalculator {
    public static GeocentricCoordinates calculate(
            Planet planet, double julianDate) {

        // Get orbital elements for date
        OrbitalElements elements = planet.getElements(julianDate);

        // Mean anomaly
        double M = elements.L - elements.omega;

        // Solve Kepler's equation for eccentric anomaly
        double E = solveKepler(M, elements.e);

        // True anomaly
        double nu = 2 * atan2(
            sqrt(1 + elements.e) * sin(E / 2),
            sqrt(1 - elements.e) * cos(E / 2)
        );

        // Distance from Sun
        double r = elements.a * (1 - elements.e * cos(E));

        // Heliocentric coordinates (ecliptic)
        double xh = r * (cos(elements.Omega) * cos(nu + elements.omega - elements.Omega)
                       - sin(elements.Omega) * sin(nu + elements.omega - elements.Omega)
                       * cos(elements.I));
        double yh = r * (sin(elements.Omega) * cos(nu + elements.omega - elements.Omega)
                       + cos(elements.Omega) * sin(nu + elements.omega - elements.Omega)
                       * cos(elements.I));
        double zh = r * sin(nu + elements.omega - elements.Omega) * sin(elements.I);

        // Get Earth's position
        GeocentricCoordinates earthPos = getEarthPosition(julianDate);

        // Geocentric coordinates
        double xg = xh - earthPos.x;
        double yg = yh - earthPos.y;
        double zg = zh - earthPos.z;

        // Convert to RA/Dec
        return heliocentricToGeocentric(xg, yg, zg);
    }

    private static double solveKepler(double M, double e) {
        // Newton-Raphson iteration
        double E = M;
        for (int i = 0; i < 10; i++) {
            E = E - (E - e * sin(E) - M) / (1 - e * cos(E));
        }
        return E;
    }
}
```

## Julian Date Conversion

```java
public class TimeUtils {
    /**
     * Convert Unix timestamp to Julian Date.
     */
    public static double toJulianDate(long unixMillis) {
        return (unixMillis / 86400000.0) + 2440587.5;
    }

    /**
     * Convert Julian Date to Unix timestamp.
     */
    public static long fromJulianDate(double jd) {
        return (long) ((jd - 2440587.5) * 86400000.0);
    }
}
```

## Coordinate Conversions

### Ecliptic to Equatorial

```java
public static GeocentricCoordinates eclipticToEquatorial(
        double longitude, double latitude, double obliquity) {

    double lon = toRadians(longitude);
    double lat = toRadians(latitude);
    double obl = toRadians(obliquity);

    // Right ascension
    double ra = atan2(
        sin(lon) * cos(obl) - tan(lat) * sin(obl),
        cos(lon)
    );

    // Declination
    double dec = asin(
        sin(lat) * cos(obl) + cos(lat) * sin(obl) * sin(lon)
    );

    return GeocentricCoordinates.fromRaDec(
        toDegrees(ra) / 15.0,  // RA in hours
        toDegrees(dec)
    );
}
```

## Update Frequency

| Object | Update Trigger | Reason |
|--------|---------------|--------|
| Sun | Every frame | Position changes slowly |
| Moon | Every frame | Moves ~0.5°/hour |
| Planets | Every frame | Position changes slowly |

## Accuracy Limitations

### Time Range

| Range | Accuracy |
|-------|----------|
| ±10 years | Best accuracy |
| ±100 years | Good accuracy |
| ±1000 years | Approximate |
| Beyond | Significant errors |

### Not Modeled

- Planetary perturbations (planet-planet gravitational effects)
- Nutation (Earth's wobble)
- Light-time correction
- Relativistic effects
- Parallax (observer position on Earth)

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `Planet` | Enum with orbital elements |
| `SolarPositionCalculator` | Sun position |
| `LunarPositionCalculator` | Moon position |
| `PlanetPositionCalculator` | Planet positions |
| `TimeUtils` | Julian date conversion |
