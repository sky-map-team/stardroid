package com.google.android.stardroid.units;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class RaDecTest2 {
    private static float EPS = 1e-5f;
    @Test
    public void testRaFromHMS() {
        assertThat(RaDec.raDegreesFromHMS(0,0,0)).isWithin(EPS).of(0.0f);
        assertThat(RaDec.raDegreesFromHMS(6,0,0)).isWithin(EPS).of(90.0f);
        assertThat(RaDec.raDegreesFromHMS(6,30,0)).isWithin(EPS).of(6.5f/24*360);
        assertThat(RaDec.raDegreesFromHMS(6,0, 30 * 60)).isWithin(EPS).of(6.5f/24*360);
    }

    @Test
    public void testDecFromDMS() {
        assertThat(RaDec.decDegreesFromDMS(0,0,0)).isWithin(EPS).of(0.0f);
        assertThat(RaDec.decDegreesFromDMS(90,0,0)).isWithin(EPS).of(90.0f);
        assertThat(RaDec.decDegreesFromDMS(90,30,0)).isWithin(EPS).of(90.5f);
        assertThat(RaDec.decDegreesFromDMS(90,0, 30 * 60)).isWithin(EPS).of(90.5f);
    }
}
