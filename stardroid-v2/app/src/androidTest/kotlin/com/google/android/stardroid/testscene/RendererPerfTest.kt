/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testscene

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.stardroid.render.supportsGles3
import com.google.android.stardroid.settings.RendererBackend
import com.google.android.stardroid.render.gles1.GLSkyRenderer
import com.google.android.stardroid.render.gles3.GLES3SkyRenderer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D19 perf-gate test: drives the [RendererTestActivity] with RENDERMODE_CONTINUOUSLY and the
 * full 100k-point test scene to validate that the GL draw path doesn't catastrophically regress.
 *
 * **Thresholds and hardware context:** the design spec (D19) sets a 30 fps floor measured on a
 * Pixel 3a device or emulator. CI currently runs on a Nexus 6 AVD with SwiftShader software
 * rendering, which is far slower than hardware. The CI threshold here ([MIN_FRAMES_FOR_CI]) is
 * therefore intentionally low — it is a **smoke gate** (the renderer does not hang or crash)
 * rather than a performance gate. The actual D19 30 fps target must be verified manually on a
 * Pixel 3a device or hardware-accelerated emulator and is recorded as a release-time check
 * (D31).
 *
 * Once CI migrates to a hardware-accelerated Pixel 3a AVD the threshold should be raised
 * to [MIN_FRAMES_FOR_PIXEL_3A] = 150 (30 fps × 5 s).
 *
 * **Both backends are gated.** Every benchmark runs once per [RendererBackend], so a change that
 * breaks one and not the other cannot report green — which is what happened until this was
 * parameterised, when every variant silently measured GLES1.
 *
 * **What this gate is, and is not.** It is a *catastrophic-regression* detector: it catches a
 * renderer that hangs, crashes, or collapses to single-digit frame rates. It is **not** a
 * comparison between the backends, and by design no longer pretends to be — a decision taken
 * deliberately rather than a limitation being tolerated.
 *
 * It cannot compare them for two independent reasons, and fixing either alone would not help.
 * The measurement is pinned to the display: frames per second saturates at the panel's refresh
 * rate, and [DrawTimeStats] does not escape it either, because a vsync-throttled driver
 * back-pressures *inside* the GL calls, so the wait lands in the measured region. On a Pixel 9
 * Pro both backends report a ~7.5 ms mean against an 8.33 ms budget, in both the opaque and
 * translucent variants — four numbers within 0.1 ms across configurations doing materially
 * different work.
 *
 * And the scene does not exercise what differs. The load is points, where `StellarRamps.sizeDp`
 * yields two sizes, so GLES1 already batches 100k points into *two* draw calls and GLES3 makes
 * it one — the "one draw call instead of dozens" premise does not apply here. The path where
 * GLES3 genuinely wins is labels, one instanced draw per atlas page against a
 * `glPushMatrix`/`glDrawArrays` per label, and the declutterer caps those at a few dozen.
 *
 * Making it a real comparison needs an offscreen render loop that never swaps (or GPU timer
 * queries) *and* a benchmark-only path that loads the labels past the declutter cap. Deferred;
 * see render-gles3.md §5. The two runs are still reported side by side so the numbers are
 * readable, and are deliberately not asserted against each other.
 *
 * **Timing:** every measurement starts from the first frame the GL thread actually draws, never
 * from a fixed sleep. On SwiftShader the activity can take more than 6 s to reach `Displayed`
 * (three 1 s `SurfaceSyncGroup` timeouts, then multi-hundred-ms frames), so a fixed warmup
 * expires before any frame exists and the gate reads zero — which is what took the translucent
 * variant down on CI, not a renderer regression.
 */
@RunWith(AndroidJUnit4::class)
class RendererPerfTest {
    companion object {
        /** Settle time after the first frame lands, before the measurement window opens. */
        private const val WARMUP_MS = 2_000L
        private const val MEASUREMENT_MS = 5_000L

        /**
         * How long to wait for the very first frame. Generous because the CI emulator's
         * software renderer needs several seconds just to bring the surface up.
         */
        private const val FIRST_FRAME_TIMEOUT_MS = 30_000L

        /**
         * Extra time granted to reach [MIN_FRAMES_FOR_CI] when the measurement window closed
         * short of it. The gate asks "is the renderer still drawing?", so a slow-but-alive
         * software renderer should pass rather than flake.
         */
        private const val SLOW_RENDERER_GRACE_MS = 20_000L

        private const val POLL_MS = 50L

        private const val TAG = "RendererPerfTest"

        /**
         * Instrumentation argument that disables the D19 GL benchmark gate. Set by
         * `android.yml` for the CI emulator only — see [assumeGlBenchmarksSupported].
         */
        private const val ARG_SKIP_GL_BENCHMARKS = "skipGlBenchmarks"

        /**
         * Minimum frames over [MEASUREMENT_MS] ms on the CI SwiftShader emulator (~1 fps floor).
         */
        private const val MIN_FRAMES_FOR_CI = 5

        /**
         * D19 target: 30 fps × 5 s = 150 frames. Use this threshold when running on a
         * hardware-accelerated Pixel 3a emulator or real device.
         */
        @Suppress("unused")
        private const val MIN_FRAMES_FOR_PIXEL_3A = 150
    }

    /**
     * The WHEN_DIRTY variant calls `requestLocation()`, and an ungranted permission puts
     * `GrantPermissionsActivity` on top of the test activity — which stops the GL thread, so no
     * frame is ever drawn. Granting up front keeps the harness on screen. Done through
     * `UiAutomation` rather than `GrantPermissionRule` so `:app` needs no new test dependency.
     */
    @Before
    fun grantLocationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    @Test
    fun rendererDrawsFramesContinuouslyWith100kPointsOnGles1() {
        benchmarkAndReport(RendererBackend.GLES1, translucent = false)
    }

    @Test
    fun rendererDrawsFramesContinuouslyWith100kPointsOnGles3() {
        benchmarkAndReport(RendererBackend.GLES3, translucent = false)
    }

    /**
     * The camera-ar-mode.md slice-1 gate: same benchmark, but on the through-camera surface
     * configuration (translucent holder, media-overlay z-order, alpha-0 clear + scrim quad).
     * Same smoke threshold — the point is that AR compositing doesn't hang or wreck the
     * draw path, not an absolute fps.
     */
    @Test
    fun rendererStillDrawsWithTranslucentSurfaceOnGles1() {
        benchmarkAndReport(RendererBackend.GLES1, translucent = true)
    }

    @Test
    fun rendererStillDrawsWithTranslucentSurfaceOnGles3() {
        benchmarkAndReport(RendererBackend.GLES3, translucent = true)
    }

    @Test
    fun firstFrameRenderedAfterSceneSubmissionOnGles1() {
        assertFirstFrameArrives(RendererBackend.GLES1)
    }

    @Test
    fun firstFrameRenderedAfterSceneSubmissionOnGles3() {
        assertFirstFrameArrives(RendererBackend.GLES3)
    }

    /**
     * Reports a measurement to both stdout and logcat.
     *
     * `println` alone is not enough: the instrumentation runner swallows it, so the numbers this
     * gate exists to produce could not be read back off a run. Logcat survives, which is what
     * makes `adb logcat -s RendererPerfTest` a usable way to get the comparison.
     */
    private fun report(message: String) {
        println(message)
        Log.i(TAG, message)
    }

    /** Runs a benchmark variant and reports it against the D19 target. */
    private fun benchmarkAndReport(
        backend: RendererBackend,
        translucent: Boolean,
    ) {
        val label =
            buildString {
                append("RendererPerfTest [")
                append(backend.name)
                append(']')
                if (translucent) append(" (translucent)")
            }
        val measured = runBenchmark(backend, translucent, label)

        report("$label: D19 Pixel 3a target is $MIN_FRAMES_FOR_PIXEL_3A frames (30 fps)")
        if (measured < MIN_FRAMES_FOR_PIXEL_3A) {
            report(
                "$label: WARN: below D19 30-fps target " +
                    "(expected on CI SwiftShader; verify on Pixel 3a hardware)",
            )
        }
    }

    private fun assertFirstFrameArrives(backend: RendererBackend) {
        assumeBackendSupported(backend)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(context, RendererTestActivity::class.java).apply {
                putExtra(RendererTestActivity.EXTRA_BENCHMARK, false) // WHEN_DIRTY mode
                putExtra(RendererTestActivity.EXTRA_BACKEND, backend.name)
            }

        val startMs = SystemClock.elapsedRealtime()
        ActivityScenario.launch<RendererTestActivity>(intent).use { scenario ->
            // In WHEN_DIRTY mode the activity submits the scene in onCreate, which triggers
            // requestRender.
            val frames = scenario.awaitFrames(target = 1L, timeoutMs = FIRST_FRAME_TIMEOUT_MS)
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            report(
                "RendererPerfTest [${backend.name}]: first frame after ${elapsedMs}ms " +
                    "(D19 p90 target ≤ 2500ms)",
            )

            assertThat(frames).isGreaterThan(0L)
        }
    }

    /**
     * Asserts the renderer that drew the first frame is the one the test asked for.
     *
     * [assumeBackendSupported] checks the *device* can provide GLES3; this checks the app
     * actually used it. Different failures, and only this one yields a plausible number
     * attributed to the wrong renderer. The gate already shipped once in exactly that state —
     * every variant measured GLES1, because `EXTRA_BACKEND` was never passed — and nothing
     * failed to say so.
     */
    private fun assertMeasuringRequestedBackend(
        scenario: ActivityScenario<RendererTestActivity>,
        backend: RendererBackend,
    ) {
        var active: String? = null
        scenario.onActivity { activity -> active = activity.activeBackend.get() }
        val expected =
            when (backend) {
                RendererBackend.GLES1 -> GLSkyRenderer.BACKEND_NAME
                RendererBackend.GLES3 -> GLES3SkyRenderer.BACKEND_NAME
            }
        assertWithMessage(
            "Asked for ${backend.name} but the renderer reported '$active'. Any timing from " +
                "this run describes the wrong backend.",
        ).that(active).isEqualTo(expected)
    }

    /**
     * Skips a GLES3 variant, loudly, on a device that cannot provide a GL ES 3.0 context.
     *
     * Without this the backend factory silently falls back to GLES1 and the test would pass
     * while measuring the wrong renderer — a green gate reporting a number it did not take.
     */
    private fun assumeBackendSupported(backend: RendererBackend) {
        if (backend != RendererBackend.GLES3) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (supportsGles3(context)) return
        val banner =
            "SKIPPED: GLES3 variant — this device reports no GL ES 3.0 support, so the " +
                "backend would silently fall back to GLES1 and measure the wrong renderer."
        Log.w(TAG, banner)
        println(banner)
        Assume.assumeTrue(banner, false)
    }

    /**
     * Runs the benchmark scene in RENDERMODE_CONTINUOUSLY, optionally on the translucent AR
     * surface, and asserts the smoke floor. Returns the frames drawn in the measurement window.
     */
    private fun runBenchmark(
        backend: RendererBackend,
        translucent: Boolean,
        label: String,
    ): Long {
        assumeGlBenchmarksSupported(label)
        assumeBackendSupported(backend)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(context, RendererTestActivity::class.java).apply {
                putExtra(RendererTestActivity.EXTRA_BENCHMARK, true)
                putExtra(RendererTestActivity.EXTRA_BACKEND, backend.name)
                if (translucent) putExtra(RendererTestActivity.EXTRA_TRANSLUCENT, true)
            }

        ActivityScenario.launch<RendererTestActivity>(intent).use { scenario ->
            // Anchor on real progress: surface creation and the initial scene upload are done
            // once a frame has been drawn.
            val firstFrame = scenario.awaitFrames(target = 1L, timeoutMs = FIRST_FRAME_TIMEOUT_MS)
            assertThat(firstFrame).isGreaterThan(0L)
            assertMeasuringRequestedBackend(scenario, backend)
            Thread.sleep(WARMUP_MS)

            scenario.onActivity { activity ->
                activity.frameCount.set(0L)
                activity.drawTimeNanos.reset()
            }
            val startMs = SystemClock.elapsedRealtime()
            Thread.sleep(MEASUREMENT_MS)

            var measuredFrames = 0L
            var drawTime: DrawTimeStats.Snapshot? = null
            scenario.onActivity { activity ->
                measuredFrames = activity.frameCount.get()
                drawTime = activity.drawTimeNanos.snapshot()
            }
            val windowMs = SystemClock.elapsedRealtime() - startMs

            val fps = measuredFrames * 1_000.0 / windowMs
            report(
                "$label: $measuredFrames frames in ${windowMs}ms → %.1f fps".format(fps),
            )
            // The number that can actually compare backends. Frames per second saturates at the
            // panel's refresh rate — on a 120Hz device both backends report exactly 120.0 — so
            // it measures the display rather than the renderer. Draw time is the submission
            // cost the port set out to reduce, and it stays meaningful under a frame-rate cap.
            report("$label: draw ${drawTime}")

            // Smoke gate: the renderer must draw at least a handful of frames — not hang or
            // crash. A software renderer can be slower than the window without being stuck, so
            // give it extra time before failing rather than calling slowness a hang.
            val finalFrames =
                if (measuredFrames >= MIN_FRAMES_FOR_CI) {
                    measuredFrames
                } else {
                    report("$label: below the floor in the window; granting extra time")
                    scenario.awaitFrames(
                        target = MIN_FRAMES_FOR_CI.toLong(),
                        timeoutMs = SLOW_RENDERER_GRACE_MS,
                    )
                }
            assertThat(finalFrames).isAtLeast(MIN_FRAMES_FOR_CI.toLong())
            return measuredFrames
        }
    }

    /**
     * Aborts a benchmark variant as *skipped* when the runner cannot survive it.
     *
     * This has to be decided **before** the activity is launched. On GitHub-hosted runners the
     * emulator *process* dies within a second of `RendererTestActivity` resuming with the
     * benchmark scene — logcat stops mid-stream and UTP reports a short test count — so anything
     * that inspects the live GL context comes far too late to help. It is the 100k-point scene
     * under RENDERMODE_CONTINUOUSLY that does it, not the translucent surface: with the AR
     * variant skipped, the opaque one died in exactly the same way. WHEN_DIRTY rendering of the
     * real catalog ([firstFrameRenderedAfterSceneSubmission]) is unaffected and still runs on CI.
     *
     * Not software rendering as such — a local `-gpu swiftshader_indirect` AVD runs both
     * variants to completion.
     *
     * The skip is deliberately loud. A quietly skipped gate is worse than no gate, because it
     * still looks green.
     */
    private fun assumeGlBenchmarksSupported(label: String) {
        val skip =
            InstrumentationRegistry.getArguments()
                .getString(ARG_SKIP_GL_BENCHMARKS)
                .toBoolean()
        if (skip) {
            val banner =
                "\n" +
                    "============================================================\n" +
                    "SKIPPED: $label\n" +
                    "The D19 GL benchmark gate is DISABLED on this run because\n" +
                    "-PskipGlBenchmarks=true was passed.\n" +
                    "The 100k-point continuous benchmark kills the GitHub-hosted\n" +
                    "runner's emulator process ~1s after the activity resumes,\n" +
                    "taking the whole test run with it. This covers BOTH the\n" +
                    "opaque (D19) and translucent AR (D67) variants.\n" +
                    "Neither is covered by this CI run. Verify locally, where\n" +
                    "both pass:\n" +
                    "  ./gradlew :app:connectedFdroidDebugAndroidTest\n" +
                    "============================================================"
            Log.w(TAG, banner)
            println(banner)
            Assume.assumeFalse(banner, true)
        }
    }

    /** Polls the frame counter until [target] frames are drawn or [timeoutMs] elapses. */
    private fun ActivityScenario<RendererTestActivity>.awaitFrames(
        target: Long,
        timeoutMs: Long,
    ): Long {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var frames = 0L
        while (frames < target && SystemClock.elapsedRealtime() < deadline) {
            onActivity { activity -> frames = activity.frameCount.get() }
            if (frames < target) Thread.sleep(POLL_MS)
        }
        return frames
    }
}
