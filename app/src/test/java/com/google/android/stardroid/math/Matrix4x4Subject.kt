package com.google.android.stardroid.math

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth

/**
 * For use in Truth tests.
 */
class Matrix4x4Subject private constructor(metadata: FailureMetadata, private val actual: Matrix4x4) :
    Subject(metadata, actual) {
    fun isWithin(tol: Float): TolerantComparison {
        return TolerantComparison(tol)
    }

    inner class TolerantComparison(val tol: Float) {
        fun of(other: Matrix4x4) {
            for (i in other.floatArray.indices) {
                check("index $i is out of tolerance").that(other.floatArray[i]).isWithin(
                    tol).of(actual.floatArray[i])
            }
        }
    }

    companion object {
        private fun matrix4x4s(): Factory<Matrix4x4Subject, Matrix4x4> {
            return Factory { metadata: FailureMetadata, actual: Matrix4x4 ->
                Matrix4x4Subject(
                    metadata,
                    actual
                )
            }
        }

        fun assertThat(actual: Matrix4x4?): Matrix4x4Subject {
            return Truth.assertAbout(matrix4x4s()).that(actual)
        }
    }
}