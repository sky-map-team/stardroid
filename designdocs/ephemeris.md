# How are the current object positions calculated?

Some notes on how object positions are calculated in Sky Map. The code is old and crusty and
was thrown together quite quickly by several folks so a roadmap is useful. At this point the
code is also half-converted into Kotlin.

In most cases the problem boils down to calculating the object's Right Ascension and Declination.
Once Ra and Dec are established and converted into
 Geocentric Coordinates (a 3-vector) the `AstronomerModel` class calculates
 the direction of the phone in the same coordinate system and the 
 rendering code takes it from there.

## Useful classes
### `Vector3`
A 3-vector of floats (without any particular semantic meaning) used to represent directions
in various different coordinate frames.

### `Matrix33`

A 3X3 matrix class with the usual determinant, inverse, transpose methods.

### `Matrix4x4`

A 4X4 matrix class mostly used in the OpenGL rendering code.

### `CoordinateManipulations`

Contains various helper functions that convert coordinates (often `Vector3`s) into different
coordinate systems (e.g. Geocentric, Heliocentric...).  Also augments the `Vector3` with some
astronomy-specific extension functions.

### `RaDec`
A pair of floats representing right ascension and declination.

### `LatLong`
A pair of floats representing latitude and longitude. Has some
helper functions to calculate distance between two points on Earth.

### Geocentric Coordinates
A `Vector3` representing an object's location in 3-space with the Earth at the center,

The 'z' axis points North and the 'x' axis points at RA, dec = 0, 0.

### Heliocentric Coordinates
A `Vector3` representing an object's location relative to the Sun.  A

### `OrbitalElements`

The main class for holding the 'constants' for determining
orbits. These are:

   * d - distance in AU
   * e - eccentricity
   * i - inclination (radians)
   * a - longitude of ascending node (radians)
   * p - longitude of perihelion (radians)
   * l - mean longitude (radians)

The calculation comes from: http://ssd.jpl.nasa.gov/?planet_pos
and has some math to calculate the 'anomaly'.

Good reference as to how this works: http://www.stjarnhimlen.se/comp/tutorial.html


## Planets
Modelled by the `Planet` enum. These are really any solar system objects, not just planets.
The class contains some ids for its name and default image, as well as logic to calculate
the `OrbitalElements` for that object (with the exception of the Moon, below).


## Moon
Instead of `OrbitalElements` there's a special method to get
the lunar location as a function of time as an Ra and Dec.

## Sun
Is actually an instance of the `Planet` enum.

## Horizon

THe horizon isn't fixed in RA and Dec even if it is fixed compared
to the user. The `AstronomerModel` class figures out N, S, E, W and
the Zenith and Nadir in RA and Dec and the horizon can be figured from
there.

## Stars, Messier objects & Constellations etc

These objects have fixed RA and Dec without any time dependence.

# Proposed Refactorings
## Where is the API consumed?
The main consumer of the planet locations is in the `PlanetSource` class via the `RaDec.getInstance` method. This, at present, is essentially the public API to the object location code (for solar system objects).

## Eliminate redundant code

We have methods that appear to be unused. We have classes for doing linear algebra that could probably be replaced by platform provided ones such as 
android.graphics.Matrix or android.opengl.

## Floats to Doubles?

Back in the day on the early devices it was strongly recommended to use `float` for efficiency reasons. This recommendation (like other oddities like making local copies of fields) seems to be no longer be recommended: https://developer.android.com/training/articles/perf-tips

However...all the OpenGL libraries are still built around `float`s... so we should leave this as-is unless we run into accuracy issues that require `double`s.

## Testing

This code is complex and easy to break. We'll need some smoke tests at a high level in the API to make sure we don't break anything when messing up the internals.
`PlanetTest` and `RaDecTest` have some great examples.

## Efficiency

Currently positions are only updated at certain granularities that depend on the object in question.
On the other hand, some calculations (like calculating the orbital elements) probably only need
to be calculated once per user session (unless time travelling, perhaps). Is the extra complication
justified on modern phones? Some kind of profiling would help here.

Update: Profiler shows no significant difference in energy usage when the min update time is
set to 0. Doesn't really change the UX much either as the Moon already updates frequently
and the 'jumpiness' in time travel mode is unchanged - perhaps it's the rate of 'ticks' in TT
mode that actually needs to be addressed.

## Clean up the code
    * Separate general linear algebra classes from astronomy specific ones
    * Data holder classes like RaDec are doing too much. It's OK for them to have helper
    methods specific to their purpose (e.g. conversion functions) but they shouldn't be calculating
    planetary positions!
    * A cleaner high-level API to position calculation
    * Separation of sun-orbiting objects and earth-orbiting objects.

## New API
What should a new API look like?

Ideally some base celestial object that has the core attributes needed to present the object on the
screen, with some specialization in subclasses.  Some facade "universe" class can provide access
to the set of all objects.  Some class hierarchy like:

#### CelestialObject
    * RA, Dec
    * Apparent Magnitude
    * Appearance
        * Color
        * Icon
        * Image (at least one)
    * Apparent size?
    * Distance to earth (used for ordering - does the ordering ever change? Presumably in principle for sun/mercury/venus)

#### Solar System Object
    * Phase (only really matters for mercury, venus, moon)

There should be other objects in the hierarchy that factor out common behaviors for (say) earth-orbiting
and sun-orbiting objects, but these could be mostly transparent to everything else?  The current
hierarchy as of this commit is somewhat more elaborate and can probably be pruned. Some behavior
might be best done as mixins.
