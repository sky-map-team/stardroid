# How are the current object positions calculated?

Some notes on how object positions are calculated in Sky Map. The code is old and crusty and
was thrown together quite quickly by several folks so a roadmap is useful.

In most cases the problem boils down to calculating the object's Right Ascension and Declination.
Once Ra and Dec are established and converted into
 `GeocentricCoordinates` the `AstronomerModel` class calculates
 the direction of the phone in the same coordinate system and the 
 rendering code takes it from there.

## Useful classes
### `Vector3`
A 3-vector of floats (without any particular semantic meaning). `VectorUtils` provides the usual set of
helpers to do mathematical operations on them (cross products etc). 

### `Matrix33`

A 3X3 matrix class with the usual determinant, inverse, transpose methods.

### `Matrix4x4`

A 4X4 matrix class.

### `Geometry`

A helper class for general geometric functions (e.g. vector addition)
which unfortunately also mixes in astronomy specific functions
to do with `GeocentricCoordinates`.

### `RaDec`
A pair of floats representing right ascension and declination.

Somewhat bizarrely also includes a set of static methods for doing some conversions and checks
(some of which aren't used) and for getting planet locations.

The conversion from `GeocentricCoordinates` is pretty simple as it's just straightforward trigonometry.

Contains the math to calculate the RA, Dec for each Planet by
getting its coordinates in HeliocentricCoordinates, getting the same
for Earth, subtracting and then doing some more corrections.
Special cases for Sun (just take Earth's heliocentric coords and invert)
and the moon (just special).

### `LatLong`
A pair of floats representing latitude and longitude. Has some
helper functions to calculate distance between two points on Earth.

### `GeocentricCoordinates`
A 3-vector of floats that subclasses a `Vector3`.
This class has some specific meaning
though - it's an object's location in 3-space with the Earth at the center,

The 'z' axis points North and the 'x' axis points at RA, dec = 0, 0.

Has some basic conversion functions (e.g. to a `Vector3` and a float array) and
some more complex ones to convert to/from `RaDec`.

### `HeliocentricCoordinates`
An object's location relative to the Sun.  Also extends `Vector3`.

Adds a 'radius' property (in AU) - not sure what it's used for.

Has some basic functions for doing arithmetic (subtraction and distance).

Contains a helper function to calculate coordinates from a given Planet and Date
(BAD) and another helper to calculate them from `OrbitalElements` (maybe ok).

Has a helper function to create a new `HeliocentricCoordinates` as "equatorial" coordinates
based on an "OBLIQUITY" constant (converting to/from the Ecliptic plane).

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
Modelled by the `Planet` enum. Each instance has a drawable, name,
update frequency (more distant planets move slower!). Confusingly the `Planet` enum is used to model sun-orbiting objects like the 8 planets, but also the Sun (basically modelling Earth's orbit and then inverting it) and the Moon (hacked in as an afterthought).

The enum has many (too many) responsibilities. It has a method
to calculate the lunar phase (used to show different images).
It has method to calculate the `OrbitalElements` for each kind
of planet for a given date. Because the Sun is included here
the `OrbitalElements` for the Earth are actually calculated
for that object.

An exception will be thrown if you try
to get them for the Moon.

There's a method to calculate the 'phase angle' as a float - again
with a special version for the moon.

There are various helper methods for calculating the full moon time which
also don't really belong here.

And some magnitude calculations that aren't currently used.


## Moon
Instead of `OrbitalElements` there's a special method to get
the lunar location as a function of time as an Ra and Dec.

## Sun
Is actually an instance of the `Planet` enum.

There's a specific `SolarPositionCalculator` that gets the sun's location
in Ra and Dec from its `HeliocentricCoordinates`.

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
