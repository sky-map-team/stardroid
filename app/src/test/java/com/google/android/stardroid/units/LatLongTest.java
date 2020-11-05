package com.google.android.stardroid.units;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Created by johntaylor on 2/15/16.
 */
public class LatLongTest {
    private static final float TOL = 1e-5f;

    @Test
    public void latLong_testInstantiatesCorrectly() {
        LatLong position = new LatLong(45, 50);
        assertThat(position.getLatitude()).isWithin(TOL).of(45f);
    }

    @Test
    public void latLong_testBoundsCorrectly() {
        LatLong position = new LatLong(95, 50);
        assertThat(position.getLatitude()).isWithin(TOL).of(90f);

        position = new LatLong(-105, 50);
        assertThat(position.getLatitude()).isWithin(TOL).of(-90f);

        position = new LatLong(45, 240);
        assertThat(position.getLongitude()).isWithin(TOL).of(-120f);

        position = new LatLong(45, -200);
        assertThat(position.getLongitude()).isWithin(TOL).of(160f);

        position = new LatLong(45, 600);
        assertThat(position.getLongitude()).isWithin(TOL).of(-120f);

        position = new LatLong(45, -560);
        assertThat(position.getLongitude()).isWithin(TOL).of(160f);
    }
}
