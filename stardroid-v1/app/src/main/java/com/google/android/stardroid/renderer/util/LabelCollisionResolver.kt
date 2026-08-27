package com.google.android.stardroid.renderer.util

import kotlin.math.cos
import kotlin.math.sin

/** Resolves overlapping label bounds in their shared rotated coordinate system. */
class LabelCollisionResolver {
    private val positions = ArrayList<LabelPosition>()
    private var placedCount = 0

    fun beginFrame() {
        placedCount = 0
    }

    @JvmOverloads
    fun place(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        upAngle: Float,
        maxIterations: Int = DEFAULT_MAX_ITERATIONS,
    ): LabelPosition {
        val cosine = cos(upAngle)
        val sine = sin(upAngle)
        val localX = cosine * x - sine * y
        var localY = sine * x + cosine * y
        var iterations = 0
        while (
            iterations < maxIterations &&
                overlapsAny(localX, localY, width, height, cosine, sine)
        ) {
            // Keep later labels visible; draw order defines which label retains its anchor.
            localY -= height
            iterations++
        }
        val position =
            positions.getOrNull(placedCount) ?: LabelPosition().also { positions.add(it) }
        position.x = cosine * localX + sine * localY
        position.y = -sine * localX + cosine * localY
        position.width = width
        position.height = height
        placedCount++
        return position
    }

    private fun overlapsAny(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cosine: Float,
        sine: Float,
    ): Boolean {
        for (index in 0 until placedCount) {
            val placed = positions[index]
            val placedX = cosine * placed.x - sine * placed.y
            val placedY = sine * placed.x + cosine * placed.y
            if (
                x - width / 2 < placedX + placed.width / 2 &&
                    x + width / 2 > placedX - placed.width / 2 &&
                    y - height / 2 < placedY + placed.height / 2 &&
                    y + height / 2 > placedY - placed.height / 2
            ) {
                return true
            }
        }
        return false
    }

    data class LabelPosition(
        var x: Float = 0f,
        var y: Float = 0f,
        var width: Float = 0f,
        var height: Float = 0f,
    )

    private companion object {
        const val DEFAULT_MAX_ITERATIONS = 10
    }
}
