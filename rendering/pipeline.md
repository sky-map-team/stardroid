# Rendering Pipeline

This document describes the complete rendering pipeline from astronomical data to screen pixels.

## Pipeline Overview

```
┌────────────────────────────────────────────────────────────────────┐
│  1. DATA SOURCES                                                    │
│     FlatBuffers binary files, ephemeris calculations, network data │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  2. LAYERS                                                          │
│     Load/compute astronomical objects, provide renderables         │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  3. ASTRONOMICAL SOURCES                                            │
│     Logical objects with positions and visual representations      │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  4. PRIMITIVES                                                      │
│     Points, lines, labels, images with coordinates and properties  │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  5. RENDERER CONTROLLER                                             │
│     Queue updates, manage object managers                          │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  6. OBJECT MANAGERS                                                 │
│     Convert primitives to OpenGL vertex data                       │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  7. SKY RENDERER                                                    │
│     Apply transforms, execute OpenGL draw calls                    │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│  8. SCREEN                                                          │
│     Final rendered frame                                           │
└────────────────────────────────────────────────────────────────────┘
```

## Stage 1: Data Sources

### File-Based Data

FlatBuffers binary files loaded from assets (zero-copy):

```kotlin
// Asset loading - zero-copy access
val bytes = assetManager.open("stars.bin").use { it.readBytes() }
val buffer = ByteBuffer.wrap(bytes)
val sources = AstronomicalSources.getRootAsAstronomicalSources(buffer)
```

### Computed Data

Ephemeris calculations for solar system:

```kotlin
// Planet position calculation
val julianDate = TimeUtils.toJulianDate(currentTime)
val position = planet.calculatePosition(julianDate)
```

### Network Data

Real-time data from internet:

```kotlin
// ISS TLE fetch
val tleData = httpClient.get("https://api.wheretheiss.at/...")
val elements = TleParser.parse(tleData)
```

## Stage 2: Layers

Layers organize data into logical groups:

```kotlin
interface Layer {
    fun initialize()
    fun registerWithRenderer(controller: RendererController)
    fun getRenderables(): List<AstronomicalRenderable>
}
```

### Layer Types

| Type | Example | Data Source |
|------|---------|-------------|
| File-based | StarsLayer | FlatBuffers binary |
| Computed | GridLayer | Mathematical generation |
| Network | IssLayer | TLE orbital data |

## Stage 3: Astronomical Sources

Each source represents a celestial object:

```kotlin
interface AstronomicalSource {
    fun getSearchLocation(): GeocentricCoordinates
    fun getNames(): List<String>

    fun getPoints(): List<PointPrimitive>
    fun getLines(): List<LinePrimitive>
    fun getLabels(): List<TextPrimitive>
    fun getImages(): List<ImagePrimitive>
}
```

### FlatBufferAstronomicalSource

Wraps FlatBuffers data (zero-copy access):

```kotlin
class FlatBufferAstronomicalSource(
    private val source: AstronomicalSource
) : AstronomicalSourceInterface {

    override fun getPoints(): List<PointPrimitive> {
        return (0 until source.pointsLength).map { i ->
            val p = source.points(i)!!
            PointPrimitive(
                GeocentricCoordinates(
                    p.location().rightAscension(),
                    p.location().declination()
                ),
                p.color().toInt(),
                p.size()
            )
        }
    }
}
```

## Stage 4: Primitives

Four primitive types represent visual elements:

### PointPrimitive

```kotlin
data class PointPrimitive(
    val coordinates: GeocentricCoordinates,  // Position on celestial sphere
    val color: Int,                          // ARGB color
    val size: Int,                           // Point size in pixels
    val shape: Shape = Shape.Circle          // CIRCLE, STAR, etc.
)
```

### LinePrimitive

```kotlin
data class LinePrimitive(
    val start: GeocentricCoordinates,
    val end: GeocentricCoordinates,
    val color: Int,
    val lineWidth: Float
)
```

### TextPrimitive

```kotlin
data class TextPrimitive(
    val text: String,
    val coordinates: GeocentricCoordinates,
    val color: Int,
    val fontSize: Int,
    val offset: Float  // Offset from associated point
)
```

### ImagePrimitive

```kotlin
data class ImagePrimitive(
    val coordinates: GeocentricCoordinates,
    val resourceId: Int,          // Drawable resource
    val scale: Float,             // Display scale
    val rotationAngle: Float      // Orientation
)
```

## Stage 5: Renderer Controller

Manages update queue and object managers:

```kotlin
class RendererController {
    private val updateQueue: Queue<RenderUpdate> = LinkedList()
    private val managers: Map<KClass<*>, RendererObjectManager> = mutableMapOf()

    fun queueUpdate(type: UpdateType, primitives: List<Primitive>) {
        updateQueue.add(RenderUpdate(type, primitives))
    }

    fun processUpdates() {
        while (updateQueue.isNotEmpty()) {
            val update = updateQueue.poll()
            dispatchToManager(update)
        }
    }
}
```

### Update Types

```kotlin
enum class UpdateType {
    Reset,           // Complete data reload
    UpdatePositions, // Recalculate positions only
    UpdateImages     // Reload textures only
}
```

## Stage 6: Object Managers

Convert primitives to OpenGL-ready data:

```kotlin
abstract class RendererObjectManager {
    protected var vertexBuffer: FloatBuffer? = null
    protected var colorBuffer: FloatBuffer? = null
    protected var vertexCount: Int = 0

    abstract fun updateObjects(primitives: List<Primitive>)
    abstract fun draw(gl: GL10)
}
```

### PointObjectManager

Renders stars and planets as point sprites:

```kotlin
class PointObjectManager : RendererObjectManager() {
    override fun updateObjects(primitives: List<Primitive>) {
        val points = primitives.filterIsInstance<PointPrimitive>()
        vertexCount = points.size
        vertexBuffer = allocateBuffer(vertexCount * 3)  // x, y, z
        colorBuffer = allocateBuffer(vertexCount * 4)   // r, g, b, a
        sizeBuffer = allocateBuffer(vertexCount)        // point size

        for (p in points) {
            vertexBuffer?.put(p.coordinates.x)
            vertexBuffer?.put(p.coordinates.y)
            vertexBuffer?.put(p.coordinates.z)
            // ... colors and sizes
        }
    }
}
```

## Stage 7: Sky Renderer

Main OpenGL renderer:

```kotlin
class SkyRenderer : GLSurfaceView.Renderer {
    override fun onDrawFrame(gl: GL10) {
        // Clear buffers
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT.or(GL10.GL_DEPTH_BUFFER_BIT))

        // Apply view transformation
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glLoadMatrixf(viewMatrix, 0)

        // Process pending updates
        rendererController.processUpdates()

        // Draw each manager in depth order
        for (manager in sortedManagers) {
            manager.draw(gl)
        }
    }
}
```

### Transformation Pipeline

```
Model Coordinates (celestial sphere)
         │
         ▼
    [View Matrix]  ← Phone orientation transform
         │
         ▼
View Coordinates (camera space)
         │
         ▼
    [Projection Matrix]  ← Field of view, aspect ratio
         │
         ▼
Clip Coordinates
         │
         ▼
    [Viewport Transform]
         │
         ▼
Screen Coordinates (pixels)
```

## Frame Timing

Typical frame breakdown:

| Phase | Time (ms) | Notes |
|-------|-----------|-------|
| Update queue processing | 0.5-2 | Depends on update count |
| Vertex buffer updates | 1-3 | When data changes |
| Draw calls | 5-10 | Depends on primitive count |
| Buffer swap | 1-2 | VSync wait |
| **Total** | **8-16** | Target: <16ms for 60 FPS |

## Update Triggers

| Event | Update Type | Affected Managers |
|-------|-------------|-------------------|
| Time change | UpdatePositions | Planets, grids |
| Location change | UpdatePositions | Horizon, grids |
| Layer toggle | Reset | Toggled layer |
| Zoom change | None | Only view matrix |
| Orientation change | None | Only view matrix |
