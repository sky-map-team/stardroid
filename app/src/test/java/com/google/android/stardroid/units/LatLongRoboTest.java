package com.google.android.stardroid.units;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests that require roboelectric for API calls.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class LatLongRoboTest {

    private static final float TOL = 1e-5f;

    @Test
    public void latLong_testDistance() {
        LatLong point1 = new LatLong(0, 0);
        LatLong point2 = new LatLong(90, 0);
        assertThat(point1.distanceFrom(point2)).isWithin(TOL).of(90f);
    }

    @Test
    public void latLong_testDistance2() {
        LatLong point1 = new LatLong(45, 45);
        LatLong point2 = new LatLong(90, 0);
        assertThat(point1.distanceFrom(point2)).isWithin(TOL).of(45f);
    }

}
