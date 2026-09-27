/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testscene

/**
 * Per-frame draw durations, summarised without keeping every sample.
 *
 * Exists because frames-per-second cannot compare rendering backends once both clear the
 * display's refresh rate — the counter then measures the panel, not the renderer.
 *
 * **It does not fully escape that problem, and the numbers must be read knowing so.** When
 * rendering is vsync-throttled the driver back-pressures *inside* the GL calls, so the wait
 * lands in this measurement rather than in `eglSwapBuffers` after it. Measured on a Pixel 9 Pro
 * (120Hz, 8.33ms budget): GLES1 and GLES3 both report a ~7.5ms mean, in both the opaque and the
 * translucent variant — four numbers within 0.1ms of each other across configurations that do
 * materially different work, which is throttling, not cost. The tail percentiles are the part
 * worth reading; the mean is pinned to the frame interval.
 *
 * Getting a clean submission cost needs vsync out of the picture: render offscreen without
 * swapping, or use GPU timer queries (`EXT_disjoint_timer_query`). Until then this instrument
 * answers "is the frame pacing healthy and is the tail well-behaved", not "which backend is
 * cheaper".
 *
 * A fixed-width histogram rather than a sample list: the benchmark runs unbounded and a list
 * would allocate inside the draw loop, which is the artifact D19 exists to catch. Buckets are
 * 0.1 ms wide up to [MAX_TRACKED_MS]; anything slower lands in the overflow bucket and is
 * reported as "at least" that value, which is honest about the ceiling rather than silently
 * clamping a pathological frame into the distribution.
 *
 * Written on the GL thread and read from the test thread, so the counters are synchronised.
 */
class DrawTimeStats {
    private val buckets = IntArray(BUCKET_COUNT + 1)

    @Volatile
    private var count = 0L

    @Volatile
    private var totalNanos = 0L

    @Volatile
    private var maxNanos = 0L

    /** Records one frame's draw duration. Called on the GL thread. */
    @Synchronized
    fun record(nanos: Long) {
        count++
        totalNanos += nanos
        if (nanos > maxNanos) maxNanos = nanos
        val bucket = (nanos / NANOS_PER_BUCKET).toInt()
        buckets[if (bucket >= BUCKET_COUNT) BUCKET_COUNT else bucket]++
    }

    /** Drops every sample, so a measurement window can start clean after warm-up. */
    @Synchronized
    fun reset() {
        buckets.fill(0)
        count = 0L
        totalNanos = 0L
        maxNanos = 0L
    }

    @Synchronized
    fun snapshot(): Snapshot =
        Snapshot(
            frames = count,
            meanMs = if (count == 0L) 0.0 else totalNanos / count / NANOS_PER_MS,
            p50Ms = percentileMs(0.50),
            p90Ms = percentileMs(0.90),
            p99Ms = percentileMs(0.99),
            maxMs = maxNanos / NANOS_PER_MS,
            overflowed = buckets[BUCKET_COUNT] > 0,
        )

    /**
     * The lower edge of the bucket holding the [fraction] quantile, in ms.
     *
     * Bucket-resolution only (0.1 ms), which is ample next to an 8.3 ms frame budget and avoids
     * retaining samples. Returns [MAX_TRACKED_MS] when the quantile falls in the overflow
     * bucket — a floor, not the real value, which [Snapshot.overflowed] flags.
     */
    private fun percentileMs(fraction: Double): Double {
        if (count == 0L) return 0.0
        val target = (count * fraction).toLong().coerceAtLeast(1L)
        var seen = 0L
        for (i in 0 until BUCKET_COUNT) {
            seen += buckets[i]
            if (seen >= target) return i * BUCKET_MS
        }
        return MAX_TRACKED_MS
    }

    /** One window's summary. All times in milliseconds. */
    data class Snapshot(
        val frames: Long,
        val meanMs: Double,
        val p50Ms: Double,
        val p90Ms: Double,
        val p99Ms: Double,
        val maxMs: Double,
        /** True if any frame exceeded [MAX_TRACKED_MS], so percentiles are floors. */
        val overflowed: Boolean,
    ) {
        override fun toString(): String =
            "%d frames, mean %.2fms, p50 %.1fms, p90 %.1fms, p99 %.1fms, max %.2fms%s".format(
                frames,
                meanMs,
                p50Ms,
                p90Ms,
                p99Ms,
                maxMs,
                if (overflowed) {
                    " (percentiles are floors — some frames exceeded the ceiling)"
                } else {
                    ""
                },
            )
    }

    private companion object {
        const val BUCKET_MS = 0.1
        const val MAX_TRACKED_MS = 100.0
        const val BUCKET_COUNT = (MAX_TRACKED_MS / BUCKET_MS).toInt()
        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_BUCKET = 100_000L // 0.1 ms
    }
}
