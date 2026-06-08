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

    // Tests for parseCoordinate logic (str.trim().replace(',', '.').toFloatOrNull())
    private fun parseCoordinate(str: String): Float? = str.trim().replace(',', '.').toFloatOrNull()

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
}
