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
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.SensorManager
import android.location.LocationManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.data.RoomCatalogRepository
import com.google.android.stardroid.data.SkyMapDatabase
import com.google.android.stardroid.data.SkyMapDatabaseFactory
import com.google.android.stardroid.layers.CatalogLayers
import com.google.android.stardroid.layers.EclipticLayer
import com.google.android.stardroid.layers.GridLayer
import com.google.android.stardroid.layers.HorizonLayer
import com.google.android.stardroid.layers.LayerStrings
import com.google.android.stardroid.layers.PlanetImages
import com.google.android.stardroid.layers.SkyLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.location.LocationController
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.AssetImageLoader
import com.google.android.stardroid.render.RenderConnector
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.gles1.GLSkyRenderer
import com.google.android.stardroid.sensors.GeomagneticDeclinationSource
import com.google.android.stardroid.sensors.OrientationSource
import com.google.android.stardroid.sensors.SensorOrientationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Development-only activity: drives [GLSkyRenderer] with the real bundled catalog (slice 4d) —
 * the three [CatalogLayers] instances read from the generated `skymap.db` — plus the slice-6
 * computed layers (grid, ecliptic, horizon, solar system). [TestScene]'s synthetic scenes remain
 * for benchmark mode only. It also serves as the D19 perf-gate harness.
 *
 * **What it exercises that unit tests cannot:** the GL resource lifecycle (texture upload,
 * atlas build, context loss/recreate on backgrounding), the draw order (lines → images →
 * points → icons → labels), [RenderConnector]'s RENDERMODE_WHEN_DIRTY trigger, and the whole
 * DB-to-pixels path (Room flows → [CatalogLayers] mappings → GL).
 *
 * **Benchmark mode:** launch with intent extra [EXTRA_BENCHMARK] = true to switch to
 * RENDERMODE_CONTINUOUSLY for FPS measurement with [TestScene]'s synthetic 100k-star scene —
 * the D13/D19 no-culling stress load, which the ~6k-star catalog cannot provide. The test uses
 * [frameCount] to read the number of frames drawn. In normal mode the activity draws only when
 * a layer emits, the camera changes (drag-to-pan, or sensor samples in sensor mode), or the
 * night-mode toggle fires, matching D23's battery-friendly intent.
 *
 * **Sensor mode (slice 5):** the "Mode" button switches the camera to the real sky-model
 * pipeline — [SensorOrientationSource] → `SkyModel.pointing` against a [SkyModel.localFrame]
 * built from the last known location (with geomagnetic declination) and refreshed on a slow
 * tick — so the map points where the phone points.
 *
 * **Screenshot comparison:** run side-by-side with v1 pointing at Orion to check the
 * intentional D12 differences (zoom-invariant point size / line width vs. v1's quad-scaled
 * geometry that inflates with zoom).
 */
class RendererTestActivity : Activity() {
    companion object {
        /** If `true` in the launch intent, use RENDERMODE_CONTINUOUSLY for perf measurement. */
        const val EXTRA_BENCHMARK = "benchmark"

        /**
         * If `true`, run with the through-camera surface configuration (translucent holder,
         * media-overlay z-order, alpha-0 clear + scrim) so the D19 gate also covers the AR
         * compositing path (camera-ar-mode.md/D64).
         */
        const val EXTRA_TRANSLUCENT = "translucent"

        private const val LOCATION_PERMISSION_REQUEST = 1

        /** How often the local frame is recomputed (the sky drifts ~0.25°/min). */
        private const val LOCAL_FRAME_PERIOD_MS = 60_000L

        private const val KEY_SENSOR_MODE = "sensorMode"
    }

    /** Total frames drawn on the GL thread. Readable from any thread for the perf gate test. */
    val frameCount = AtomicLong(0L)

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var connector: RenderConnector
    private lateinit var fpsTv: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var database: SkyMapDatabase? = null

    private var nightMode = false
    private var translucentBackground = false
    private var camera =
        SkyCamera(TestScene.CAMERA_LOS, TestScene.CAMERA_UP, TestScene.CAMERA_FOV_DEG)

    // Touch panning state
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Azimuth of the look direction in radians (panned around the Z axis = Dec axis).
    private var cameraAzimuth = PI / 2.0 // starts looking toward RA 6 h
    private var cameraElevation = 0.0

    // Sensor-driven camera (slice 5): the sky-model pipeline replacing the drag rig.
    private lateinit var orientationSource: OrientationSource
    private val declinationSource = GeomagneticDeclinationSource()
    private var sensorMode = false
    private var sensorJob: Job? = null
    private val location = MutableStateFlow(LocationController.DEFAULT_LOCATION)

    @Volatile
    private var localFrame =
        SkyModel.localFrame(Clock.System.now(), LocationController.DEFAULT_LOCATION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val benchmark = intent.getBooleanExtra(EXTRA_BENCHMARK, false)
        translucentBackground = intent.getBooleanExtra(EXTRA_TRANSLUCENT, false)
        val density = resources.displayMetrics.density
        // Survive the recreate on display rotation; onResume restarts the sensor collection.
        sensorMode = savedInstanceState?.getBoolean(KEY_SENSOR_MODE) ?: false

        val assetImageLoader = AssetImageLoader(assets)
        val glRenderer =
            GLSkyRenderer(
                density,
                imageLoader = { ref -> resolveImage(ref) ?: assetImageLoader.load(ref) },
            )
        val glRenderMode =
            if (benchmark) {
                GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        glSurfaceView =
            GLSurfaceView(this).apply {
                setEGLContextClientVersion(1)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(FrameCountingRenderer(glRenderer))
                renderMode = glRenderMode
                if (translucentBackground) {
                    holder.setFormat(PixelFormat.TRANSLUCENT)
                    setZOrderMediaOverlay(true)
                }
            }

        connector = RenderConnector(glRenderer, glSurfaceView)
        if (translucentBackground) submitRenderState()
        orientationSource =
            SensorOrientationSource(getSystemService(SensorManager::class.java)) {
                currentDisplayRotation()
            }

        setupUi(benchmark)
        if (benchmark) {
            submitBenchmarkScene()
        } else {
            startCatalogLayers()
            startComputedLayers()
            requestLocation()
        }
        connector.setCamera(camera)
    }

    // ---- Sensor-driven camera (slice 5) ------------------------------------------------

    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    /**
     * Uses the last known location for the local frame — good enough for a dev harness. The
     * real permission state machine and location flow arrive with `LocationViewModel`.
     */
    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadLastKnownLocation()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        ) {
            loadLastKnownLocation()
        }
    }

    private fun loadLastKnownLocation() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        // Catch per provider: with only ACCESS_COARSE_LOCATION, querying the "gps" provider throws
        // SecurityException. A single outer catch would abort the search on the first such provider
        // and never reach the coarse-accessible ones ("network", "passive").
        val last =
            manager.allProviders.firstNotNullOfOrNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
        if (last != null) {
            location.value = LatLong(last.latitude, last.longitude)
            updateLocalFrame()
        }
    }

    private fun updateLocalFrame() {
        val now = Clock.System.now()
        val loc = location.value
        localFrame =
            SkyModel.localFrame(now, loc, declinationSource.declinationDeg(loc, now))
    }

    private fun startSensorMode() {
        sensorJob =
            scope.launch {
                // The frame drifts slowly; the design expresses v1's 60 s cache as a tick.
                launch {
                    while (isActive) {
                        updateLocalFrame()
                        delay(LOCAL_FRAME_PERIOD_MS)
                    }
                }
                orientationSource.orientations().collect { orientation ->
                    val pointing = SkyModel.pointing(localFrame, orientation)
                    camera = SkyCamera(pointing.lineOfSight, pointing.perpendicular, camera.fovDeg)
                    connector.setCamera(camera)
                }
            }
    }

    private fun stopSensorMode() {
        sensorJob?.cancel()
        sensorJob = null
    }

    private fun toggleSensorMode(
        btn: Button,
        modeLabel: TextView,
    ) {
        sensorMode = !sensorMode
        if (sensorMode) {
            startSensorMode()
        } else {
            stopSensorMode()
            // Seed the manual pan angles from where the sensors left the camera, so the first
            // touch pans from the current view instead of snapping back to the stale angles.
            val los = camera.lineOfSight
            // lineOfSight is only approximately unit-length, so clamp before asin: a z of
            // 1.0000000002 would yield NaN, and coerceIn does not filter NaN, permanently
            // wedging elevation (and then crashing SkyCamera's collinearity check on next pan).
            cameraElevation = asin(los.z.coerceIn(-1.0, 1.0)).coerceIn(-1.4, 1.4)
            cameraAzimuth = atan2(los.y, los.x)
        }
        btn.text = if (sensorMode) "Mode: SENSORS" else "Mode: MANUAL"
        modeLabel.text = if (sensorMode) "[Point at the sky]" else "[Touch to pan]"
    }

    /**
     * Opens the bundled catalog DB (with D24/G11 recovery) and collects each catalog layer's
     * scene flow into the renderer. Collection lives in [scope]; onDestroy cancels it and
     * closes the DB.
     */
    private fun startCatalogLayers() {
        scope.launch {
            val db = SkyMapDatabaseFactory.createWithRecovery(this@RendererTestActivity)
            database = db
            val catalog = RoomCatalogRepository(db)
            val locale = flowOf(LocaleSpec(Locale.getDefault().toLanguageTag()))
            for (layer in CatalogLayers.create(catalog, locale)) {
                launch { layer.scenes().collect { connector.submit(layer.id, it) } }
            }
        }
    }

    private fun setupUi(benchmark: Boolean) {
        fpsTv =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                text = "FPS: --"
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
        val nightBtn =
            Button(this).apply {
                text = "Night mode: OFF"
                textSize = 12f
                setOnClickListener { toggleNightMode(this) }
            }
        val modeLabel =
            TextView(this).apply {
                setTextColor(Color.YELLOW)
                textSize = 11f
                text =
                    when {
                        benchmark -> "[BENCHMARK]"
                        sensorMode -> "[Point at the sky]"
                        else -> "[Touch to pan]"
                    }
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }
        val sensorBtn =
            if (!benchmark && orientationSource.available) {
                Button(this).apply {
                    text = if (sensorMode) "Mode: SENSORS" else "Mode: MANUAL"
                    textSize = 12f
                    setOnClickListener { toggleSensorMode(this, modeLabel) }
                }
            } else {
                null
            }

        val overlay =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
                addView(fpsTv)
                addView(nightBtn)
                sensorBtn?.let { addView(it) }
                addView(modeLabel)
            }

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    glSurfaceView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START,
                    ),
                )
            }
        setContentView(root)

        glSurfaceView.setOnTouchListener { _, event ->
            handleTouch(event)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (sensorMode) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                panCamera(dx, dy)
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
        }
        return false
    }

    private fun panCamera(
        dx: Float,
        dy: Float,
    ) {
        val sensitivity = 0.005 / resources.displayMetrics.density
        cameraAzimuth += dx * sensitivity
        cameraElevation = (cameraElevation + dy * sensitivity).coerceIn(-1.4, 1.4)
        val cosElev = cos(cameraElevation)
        val los =
            Vector3(
                cosElev * cos(cameraAzimuth),
                cosElev * sin(cameraAzimuth),
                sin(cameraElevation),
            )
        val right = (los cross TestScene.CAMERA_UP).normalized()
        val up = (right cross los).normalized()
        camera = SkyCamera(los, up, camera.fovDeg)
        connector.setCamera(camera)
    }

    /** The full synthetic scene: the 100k-point D19 stress load plus every primitive type. */
    private fun submitBenchmarkScene() {
        connector.submit(TestScene.STARS_LAYER, TestScene.buildStarsScene())
        connector.submit(TestScene.GRID_LAYER, TestScene.buildGridScene())
        connector.submit(TestScene.IMAGES_LAYER, TestScene.buildImagesScene())
        connector.submit(TestScene.LABELS_LAYER, TestScene.buildLabelsScene())
    }

    /**
     * The slice-6 computed layers: grid and ecliptic (static), horizon (clock + location), and
     * the solar system (ephemeris + clock). The clock is a plain wall-clock tick — time travel
     * arrives with `TimeTravelViewModel`. Strings are hardcoded English like the rest of this
     * dev harness; the Compose app supplies resource-backed ones.
     */
    private fun startComputedLayers() {
        val strings = flowOf<LayerStrings>(EnglishLayerStrings)
        val clock = clockTicks()
        val layers =
            listOf<SkyLayer>(
                GridLayer(strings),
                EclipticLayer(strings),
                HorizonLayer(clock, location, strings),
                SolarSystemLayer(
                    KeplerianEphemeris,
                    clock,
                    strings,
                    location,
                    PlanetImages(),
                ),
            )
        scope.launch {
            for (layer in layers) {
                launch { layer.scenes().collect { connector.submit(layer.id, it) } }
            }
        }
    }

    /** Emits now, then ticks — the horizon/planet drift cadence (~0.25°/min sky rotation). */
    private fun clockTicks(): Flow<Instant> =
        flow {
            while (true) {
                emit(Clock.System.now())
                delay(LOCAL_FRAME_PERIOD_MS)
            }
        }

    private fun toggleNightMode(btn: Button) {
        nightMode = !nightMode
        btn.text = if (nightMode) "Night mode: ON" else "Night mode: OFF"
        submitRenderState()
    }

    private fun submitRenderState() {
        connector.setRenderState(
            RenderState(
                nightMode = nightMode,
                transparentBackground = translucentBackground,
                // The AR default dimmer, so the gate exercises the scrim quad too.
                cameraScrim = if (translucentBackground) 0.35 else 0.0,
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        fpsFrameCount = 0L
        fpsWindowStartMs = 0L
        glSurfaceView.onResume()
        if (sensorMode && sensorJob == null) startSensorMode()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        // Unregister the sensor listeners while backgrounded (v1 did the same).
        stopSensorMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SENSOR_MODE, sensorMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        database?.close()
    }

    // ---- FPS tracking (GL thread → UI thread) ----------------------------------------

    @Volatile private var fpsFrameCount = 0L

    @Volatile private var fpsWindowStartMs = 0L

    private fun onFrameDrawn() {
        frameCount.incrementAndGet()
        fpsFrameCount++
        val now = SystemClock.elapsedRealtime()
        if (fpsWindowStartMs == 0L) {
            fpsWindowStartMs = now
        } else if (now - fpsWindowStartMs >= 1_000L) {
            val fps = fpsFrameCount * 1_000f / (now - fpsWindowStartMs)
            fpsFrameCount = 0
            fpsWindowStartMs = now
            runOnUiThread { fpsTv.text = "FPS: %.1f".format(fps) }
        }
    }

    // ---- Image loader ----------------------------------------------------------------

    private fun resolveImage(ref: ImageRef): Bitmap? =
        when (ref) {
            TestImageRef.PLANET -> buildPlanetBitmap()
            else -> null
        }

    private fun buildPlanetBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1f, paint)
        return bmp
    }

    // ---- GL thread wrapper that injects FPS tracking --------------------------------

    /** v1's English label strings, hardcoded for the dev harness (see [startComputedLayers]). */
    private object EnglishLayerStrings : LayerStrings {
        override val northPole = "NP"
        override val southPole = "SP"
        override val zenith = "ZENITH"
        override val nadir = "NADIR"
        override val north = "NORTH"
        override val south = "SOUTH"
        override val east = "EAST"
        override val west = "WEST"
        override val ecliptic = "Ecliptic"

        override fun bodyName(body: SolarSystemBody): String =
            body.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private inner class FrameCountingRenderer(
        private val delegate: GLSkyRenderer,
    ) : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(
            gl: GL10,
            config: EGLConfig?,
        ) = delegate.onSurfaceCreated(gl, config)

        override fun onSurfaceChanged(
            gl: GL10,
            width: Int,
            height: Int,
        ) = delegate.onSurfaceChanged(gl, width, height)

        override fun onDrawFrame(gl: GL10) {
            delegate.onDrawFrame(gl)
            onFrameDrawn()
        }
    }
}
