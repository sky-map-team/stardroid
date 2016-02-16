package com.google.android.stardroid.units;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * Created by johntaylor on 2/15/16.
 */
public class LatLongTest {
    @Test
    public void latLong_testInstantiatesCorrectly() {
        LatLong position = new LatLong(45, 50);
        assertEquals(45f, position.getLatitude());
    }

    @Test
    public void latLong_testBoundsCorrectly() {
        LatLong position = new LatLong(95, 50);
        assertEquals(90f, position.getLatitude());

        position = new LatLong(-105, 50);
        assertEquals(-90f, position.getLatitude());

        position = new LatLong(45, 240);
        assertEquals(-120f, position.getLongitude());

        position = new LatLong(45, -200);
        assertEquals(160f, position.getLongitude());

        position = new LatLong(45, 600);
        assertEquals(-120f, position.getLongitude());

        position = new LatLong(45, -560);
        assertEquals(160f, position.getLongitude());
    }
}
