package com.google.android.stardroid.space;

import com.google.android.stardroid.provider.ephemeris.Planet;
import com.google.android.stardroid.units.HeliocentricCoordinates;
import com.google.android.stardroid.units.RaDec;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents the celestial objects and physics of the universe.
 *
 * Initially this is going to be a facade to calculating positions etc of objects - akin to
 * the functions that are in the RaDec class at the moment. Might be a temporary shim.
 */
public class Universe {
    private List<SolarSystemObject> solarSystemObjects = new ArrayList<>();

    public Universe() {
        // TODO - create the objects some other way.
    }

    /**
     * Returns all the solar system objects in the universe.
     */
    public List<SolarSystemObject> getSolarSystemObjects() {
        return solarSystemObjects;
    }

    /**
     * Gets the location of a planet at a particular date.
     * Possibly a temporary swap for RaDec.getInstance.
     */
    public RaDec getRaDec(Planet planet, Date datetime) {
        HeliocentricCoordinates sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, datetime);
        return RaDec.getInstance(planet, datetime, sunCoords);
    }

    /**
     * Gets the RaDec of the Moon at a particular date.
     * TODO Factor this away
     */
    public RaDec getMoonRaDec(Date datetime) {
        HeliocentricCoordinates sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, datetime);
        return Planet.calculateLunarGeocentricLocation(datetime);
    }

    /**
     * Gets the RaDec of the sun at a particular date.
     * TODO Factor this away
     */
    public RaDec getSunRaDec(Date datetime) {
        HeliocentricCoordinates sunCoords = HeliocentricCoordinates.getInstance(Planet.Sun, datetime);
        return RaDec.getInstance(Planet.Sun, datetime, sunCoords);
    }
}
