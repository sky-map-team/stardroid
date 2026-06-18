package com.google.android.stardroid.math

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth

/**
 * Subject class for use in Truth tests.
 */
class Vector3Subject private constructor(metadata: FailureMetadata, private val actual: Vector3) :
      Subject(metadata, actual) {
    fun isWithin(tol: Float): TolerantComparison {
        return TolerantComparison(tol)
    }

    inner class TolerantComparison(val tol: Float) {
        fun of(other: Vector3) {
            check("x is out of tolerance").that(actual.x).isWithin(tol).of(other.x)
            check("y is out of tolerance").that(actual.y).isWithin(tol).of(other.y)
            check("z is out of tolerance").that(actual.z).isWithin(tol).of(other.z)
        }
    }

    companion object {
        fun vector3s(): Factory<Vector3Subject, Vector3> {
            return Factory { metadata: FailureMetadata, actual: Vector3 ->
                Vector3Subject(
                    metadata,
                    actual
                )
            }
        }

        fun assertThat(actual: Vector3?): Vector3Subject {
            return Truth.assertAbout(vector3s()).that(actual)
        }
    }
}