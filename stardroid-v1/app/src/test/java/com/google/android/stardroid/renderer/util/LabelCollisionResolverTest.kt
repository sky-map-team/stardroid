package com.google.android.stardroid.renderer.util

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import org.junit.Test

class LabelCollisionResolverTest {
    private val resolver = LabelCollisionResolver()

    @Test
    fun place_noOverlap_keepsOriginalPositions() {
        assertPosition(place(10f, 20f), 10f, 20f)
        assertPosition(place(100f, 200f), 100f, 200f)
    }

    @Test
    fun place_pairOverlap_movesSecondLabelDown() {
        assertPosition(place(10f, 20f), 10f, 20f)
        assertPosition(place(10f, 20f), 10f, 10f)
    }

    @Test
    fun place_tightCluster_usesExplicitOffsets() {
        repeat(5) {
            assertPosition(place(10f, 20f), 10f, 20f - it * 10f)
        }
    }

    @Test
    fun place_rotated_movesAlongLabelDownDirection() {
        val angle = (PI / 2).toFloat()
        assertPosition(place(10f, 20f, angle), 10f, 20f)
        assertPosition(place(10f, 20f, angle), 0f, 20f)
    }

    @Test
    fun beginFrame_reusesPositionsWithoutKeepingCollisions() {
        place(10f, 20f)
        resolver.beginFrame()
        assertPosition(place(10f, 20f), 10f, 20f)
    }

    @Test
    fun place_iterationLimit_stopsFurtherOffsets() {
        resolver.place(10f, 20f, 30f, 100f, 0f)
        assertPosition(resolver.place(10f, 20f, 30f, 10f, 0f, 2), 10f, 0f)
    }

    private fun place(x: Float, y: Float, angle: Float = 0f) =
        resolver.place(x, y, 30f, 10f, angle)

    private fun assertPosition(
        label: LabelCollisionResolver.LabelPosition,
        x: Float,
        y: Float,
    ) {
        assertThat(label.x).isWithin(0.0001f).of(x)
        assertThat(label.y).isWithin(0.0001f).of(y)
    }
}
