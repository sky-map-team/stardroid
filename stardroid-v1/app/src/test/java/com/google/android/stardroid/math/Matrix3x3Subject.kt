package com.google.android.stardroid.math

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth

/**
 * For use in Truth tests.
 */
class Matrix3x3Subject private constructor(metadata: FailureMetadata, private val actual: Matrix3x3) :
    Subject(metadata, actual) {
    fun isWithin(tol: Float): TolerantComparison {
        return TolerantComparison(tol)
    }

    inner class TolerantComparison(val tol: Float) {
        fun of(other: Matrix3x3) {
            check("xx is out of tolerance").that(other.xx).isWithin(tol).of(actual.xx)
            check("xy is out of tolerance").that(other.xy).isWithin(tol).of(actual.xy)
            check("xz is out of tolerance").that(other.xz).isWithin(tol).of(actual.xz)
            check("yx is out of tolerance").that(other.yx).isWithin(tol).of(actual.yx)
            check("yy is out of tolerance").that(other.yy).isWithin(tol).of(actual.yy)
            check("yz is out of tolerance").that(other.yz).isWithin(tol).of(actual.yz)
            check("zx is out of tolerance").that(other.zx).isWithin(tol).of(actual.zx)
            check("zy is out of tolerance").that(other.zy).isWithin(tol).of(actual.zy)
            check("zz is out of tolerance").that(other.zz).isWithin(tol).of(actual.zz)
        }
    }

    companion object {
        private fun matrix3x3s(): Factory<Matrix3x3Subject, Matrix3x3> {
            return Factory { metadata: FailureMetadata, actual: Matrix3x3 ->
                Matrix3x3Subject(
                    metadata,
                    actual
                )
            }
        }

        fun assertThat(actual: Matrix3x3?): Matrix3x3Subject {
            return Truth.assertAbout(matrix3x3s()).that(actual)
        }
    }
}