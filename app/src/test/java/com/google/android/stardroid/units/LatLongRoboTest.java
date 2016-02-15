package com.google.android.stardroid.units;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static junit.framework.Assert.assertEquals;

/**
 * Tests that require roboelectric for API calls.
 */
@RunWith(RobolectricTestRunner.class)
public class LatLongRoboTest {

    @Test
    public void latLong_testDistance() {
        LatLong point1 = new LatLong(0, 0);
        LatLong point2 = new LatLong(95, 0);
        assertEquals(90f, point1.distanceFrom(point2));
    }
}
