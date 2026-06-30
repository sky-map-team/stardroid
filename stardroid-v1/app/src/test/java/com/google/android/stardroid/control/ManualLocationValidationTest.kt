/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.control

import com.google.android.stardroid.math.LatLong
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManualLocationValidationTest {

    private fun isValidLatitude(lat: Float) = lat in -90f..90f
    private fun isValidLongitude(lon: Float) = lon in -180f..180f

    @Test fun latitude_above90_isInvalid() { assertThat(isValidLatitude(91f)).isFalse() }
    @Test fun latitude_below90_isInvalid() { assertThat(isValidLatitude(-91f)).isFalse() }
    @Test fun latitude_exactly90_isValid() { assertThat(isValidLatitude(90f)).isTrue() }
    @Test fun latitude_exactlyMinus90_isValid() { assertThat(isValidLatitude(-90f)).isTrue() }
    @Test fun latitude_zero_isValid() { assertThat(isValidLatitude(0f)).isTrue() }
    @Test fun latitude_51_5_isValid() { assertThat(isValidLatitude(51.5f)).isTrue() }

    @Test fun longitude_above180_isInvalid() { assertThat(isValidLongitude(181f)).isFalse() }
    @Test fun longitude_belowMinus180_isInvalid() { assertThat(isValidLongitude(-181f)).isFalse() }
    @Test fun longitude_exactly180_isValid() { assertThat(isValidLongitude(180f)).isTrue() }
    @Test fun longitude_exactlyMinus180_isValid() { assertThat(isValidLongitude(-180f)).isTrue() }
    @Test fun longitude_zero_isValid() { assertThat(isValidLongitude(0f)).isTrue() }

    @Test
    fun latLong_boundaryValues_constructCorrectly() {
        val northPole = LatLong(90f, 0f)
        assertThat(northPole.latitude).isEqualTo(90f)

        val dateLine = LatLong(0f, 180f)
        // LatLong normalises longitude to (-180, 180]; 180 wraps to -180
        assertThat(dateLine.longitude).isWithin(0.001f).of(-180f)
    }

    @Test
    fun parseFloat_invalidString_returnsNull() {
        assertThat("abc".toFloatOrNull()).isNull()
        assertThat("".toFloatOrNull()).isNull()
        assertThat("12.3".toFloatOrNull()).isEqualTo(12.3f)
    }

    // Tests for parseCoordinate logic — normalises Arabic-Indic/Eastern Arabic-Indic digits
    // and comma/Arabic decimal separators to ASCII before parsing.
    private fun parseCoordinate(str: String): Float? {
        val normalized = buildString {
            for (char in str.trim()) {
                when (char) {
                    ',', '٫' -> append('.')
                    in '٠'..'٩' -> append('0' + (char - '٠'))
                    in '۰'..'۹' -> append('0' + (char - '۰'))
                    else -> append(char)
                }
            }
        }
        return normalized.toFloatOrNull()
    }

    @Test fun parseCoordinate_dotDecimal_parsesCorrectly() {
        assertThat(parseCoordinate("52.63")).isWithin(0.0001f).of(52.63f)
    }

    @Test fun parseCoordinate_commaDecimal_parsesCorrectly() {
        assertThat(parseCoordinate("52,63")).isWithin(0.0001f).of(52.63f)
    }

    @Test fun parseCoordinate_negativeDotDecimal_parsesCorrectly() {
        assertThat(parseCoordinate("-20.69")).isWithin(0.0001f).of(-20.69f)
    }

    @Test fun parseCoordinate_negativeCommaDecimal_parsesCorrectly() {
        assertThat(parseCoordinate("-20,69")).isWithin(0.0001f).of(-20.69f)
    }

    @Test fun parseCoordinate_leadingTrailingWhitespace_parsesCorrectly() {
        assertThat(parseCoordinate("  52.63  ")).isWithin(0.0001f).of(52.63f)
    }

    @Test fun parseCoordinate_invalidString_returnsNull() {
        assertThat(parseCoordinate("abc")).isNull()
        assertThat(parseCoordinate("")).isNull()
    }

    @Test fun parseCoordinate_arabicIndicDigits_parsesCorrectly() {
        assertThat(parseCoordinate("٥٢٫٦٣")).isWithin(0.0001f).of(52.63f)
    }

    @Test fun parseCoordinate_arabicIndicNegative_parsesCorrectly() {
        assertThat(parseCoordinate("-٢٠٫٦٩")).isWithin(0.0001f).of(-20.69f)
    }

    @Test fun parseCoordinate_easternArabicIndicDigits_parsesCorrectly() {
        assertThat(parseCoordinate("۵۲٫۶۳")).isWithin(0.0001f).of(52.63f)
    }

    @Test fun parseCoordinate_easternArabicIndicNegative_parsesCorrectly() {
        assertThat(parseCoordinate("-۲۰٫۶۹")).isWithin(0.0001f).of(-20.69f)
    }
}
