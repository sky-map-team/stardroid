package com.google.android.stardroid.units;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

/**
 * Tests that require roboelectric for API calls.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class LatLongRoboTest {

    @Test
    public void latLong_testDistance() {
        LatLong point1 = new LatLong(0, 0);
        LatLong point2 = new LatLong(90, 0);
        assertEquals(90f, point1.distanceFrom(point2));
    }

    @Test
    public void latLong_testDistance2() {
        LatLong point1 = new LatLong(45, 45);
        LatLong point2 = new LatLong(90, 0);
        assertEquals(45f, point1.distanceFrom(point2), 0.05);
    }

}
